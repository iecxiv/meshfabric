package iecxiv.meshfabric.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

sealed class AuthResult {
    data class Success(val authCookie: String, val userInfo: UserInfo?) : AuthResult()
    data class Requires2FA(val message: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class MeshCentralClient {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null

    var serverUrl: String = ""
        private set
    var authCookie: String = ""
        private set
    var relayAuthCookie: String = ""
        private set
    var userAuthCookie: String = ""
        private set
    var ignoreSsl: Boolean = false
        private set

    var onNodeConnectedListener: ((String) -> Unit)? = null
    var onClipboardReceivedListener: ((nodeId: String, text: String) -> Unit)? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val meshesMap = ConcurrentHashMap<String, MeshGroup>()
    private val nodesMap = ConcurrentHashMap<String, MeshNode>()

    fun getNodeMeshId(nodeId: String): String {
        val cleanId = nodeId.removePrefix("node//").removePrefix("node/").trim()
        val node = nodesMap[cleanId] ?: nodesMap["node//$cleanId"]
        val meshId = node?.meshid ?: ""
        return meshId.removePrefix("mesh//").removePrefix("mesh/").trim()
    }

    // Emits list of groups mapped to their nodes, sorted alphabetically by group name.
    // Within each group, nodes are sorted with Online nodes FIRST, then Offline nodes.
    private val _groupedDevices = MutableStateFlow<List<Pair<MeshGroup, List<MeshNode>>>>(emptyList())
    val groupedDevices: StateFlow<List<Pair<MeshGroup, List<MeshNode>>>> = _groupedDevices.asStateFlow()

    fun extractRawToken(cookie: String): String {
        for (part in cookie.split(";")) {
            val trimmed = part.trim()
            if (trimmed.startsWith("xid=") || trimmed.startsWith("x-mesh-auth=") || trimmed.startsWith("auth=")) {
                val token = trimmed.substringAfter("=")
                if (token.startsWith("eyJ")) return token
            }
        }
        for (part in cookie.split(";")) {
            val token = part.trim().substringAfter("=")
            if (token.startsWith("eyJ")) return token
        }
        val first = cookie.split(";")[0].trim()
        return if (first.contains("=")) first.substringAfter("=") else first
    }

    fun login(
        rawServerUrl: String,
        username: String,
        pass: String,
        otpToken: String = "",
        ignoreSslCert: Boolean = false,
        onResult: (AuthResult) -> Unit
    ) {
        val cleanUrl = OkHttpHelper.sanitizeServerUrl(rawServerUrl)
        serverUrl = cleanUrl
        ignoreSsl = ignoreSslCert
        val client = OkHttpHelper.createClient(ignoreSslCert)
        okHttpClient = client

        scope.launch {
            try {
                val formBodyBuilder = FormBody.Builder()
                    .add("username", username)
                    .add("password", pass)

                if (otpToken.isNotBlank()) {
                    formBodyBuilder.add("vtoken", otpToken)
                    formBodyBuilder.add("otptoken", otpToken)
                    formBodyBuilder.add("token", otpToken)
                }

                val request = Request.Builder()
                    .url("$cleanUrl/login")
                    .post(formBodyBuilder.build())
                    .build()

                val response: Response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                val responseCode = response.code

                // Extract Cookies
                val cookies = response.headers.values("Set-Cookie")
                var extractedCookie = ""
                for (cookie in cookies) {
                    if (cookie.contains("x-mesh-auth") || cookie.contains("auth=") || cookie.contains("meshsession") || cookie.contains("xid")) {
                        val cookiePart = cookie.split(";")[0]
                        extractedCookie = if (extractedCookie.isEmpty()) cookiePart else "$extractedCookie; $cookiePart"
                    }
                }

                if (extractedCookie.isEmpty() && cookies.isNotEmpty()) {
                    extractedCookie = cookies.joinToString("; ") { it.split(";")[0] }
                }

                // Check for 2FA requirement
                if (responseBody.contains("vtoken", ignoreCase = true) ||
                    responseBody.contains("2fa", ignoreCase = true) ||
                    responseBody.contains("two-factor", ignoreCase = true) ||
                    responseBody.contains("Enter 2FA", ignoreCase = true)
                ) {
                    if (otpToken.isBlank()) {
                        onResult(AuthResult.Requires2FA("Ingrese el código 2FA / OTP enviado a su dispositivo o aplicación de autenticación."))
                        return@launch
                    }
                }

                if (response.isSuccessful || responseCode == 302 || extractedCookie.isNotEmpty()) {
                    authCookie = extractedCookie
                    onResult(AuthResult.Success(extractedCookie, _userInfo.value))
                } else {
                    onResult(AuthResult.Error("Error al iniciar sesión (Código $responseCode). Verifique sus credenciales."))
                }
            } catch (e: Exception) {
                onResult(AuthResult.Error("Error de conexión: ${e.localizedMessage ?: "No se pudo contactar al servidor"}"))
            }
        }
    }

    private var reconnectJob: Job? = null
    var isUserLogout: Boolean = false

    fun reconnectNow() {
        if (isUserLogout) return
        if (serverUrl.isBlank() || authCookie.isBlank()) return
        if (_connectionState.value && webSocket != null) return
        Log.d("MeshCentralClient", "Immediate reconnect triggered for control socket...")
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (!isUserLogout && !_connectionState.value && attempt < 4) {
                attempt++
                try {
                    connectControlSocket(serverUrl, authCookie, ignoreSsl)
                    var waitMs = 0
                    while (!_connectionState.value && waitMs < 2000) {
                        delay(100)
                        waitMs += 100
                    }
                    if (_connectionState.value) break
                } catch (e: Exception) {
                    Log.w("MeshCentralClient", "Reconnect attempt $attempt failed: ${e.localizedMessage}")
                }
                delay(if (attempt == 1) 400L else 1000L)
            }
        }
    }

    private fun scheduleReconnect() {
        if (isUserLogout) return
        if (serverUrl.isBlank() || authCookie.isBlank()) return
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectionState.value = false
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(500)
            if (!isUserLogout && !_connectionState.value) {
                Log.d("MeshCentralClient", "Auto-reconnecting control socket...")
                connectControlSocket(serverUrl, authCookie, ignoreSsl)
            }
        }
    }

    fun connectControlSocket(
        rawServerUrl: String,
        cookie: String,
        ignoreSslCert: Boolean = false
    ) {
        isUserLogout = false
        reconnectJob?.cancel()
        reconnectJob = null

        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectionState.value = false

        serverUrl = OkHttpHelper.sanitizeServerUrl(rawServerUrl)
        authCookie = cookie
        ignoreSsl = ignoreSslCert

        val client = OkHttpHelper.createClient(ignoreSslCert)
        okHttpClient = client

        val wsUrl = OkHttpHelper.toWebSocketUrl(serverUrl, "/control.ashx?moreargs=1")

        val requestBuilder = Request.Builder().url(wsUrl)
        if (cookie.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookie)
        }

        webSocket?.close(1000, "Reconnecting")
        webSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                _connectionState.value = true
                reconnectJob?.cancel()
                reconnectJob = null

                if (cookie.isNotBlank()) {
                    val rawToken = extractRawToken(cookie)
                    val authMsg = JsonObject().apply {
                        addProperty("action", "urlargs")
                        add("args", JsonObject().apply {
                            addProperty("auth", if (rawToken.isNotEmpty()) rawToken else cookie)
                        })
                    }
                    webSocket.send(authMsg.toString())
                }

                scope.launch {
                    delay(150)
                    requestServerData()
                    delay(400)
                    if (meshesMap.isEmpty() || nodesMap.isEmpty()) {
                        requestServerData()
                    }
                }
                startPingLoop()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleControlMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
                stopPingLoop()
                if (!isUserLogout) {
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                    scheduleReconnect()
                } else {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("MeshCentralClient", "Control socket failure: ${t?.localizedMessage}")
                _connectionState.value = false
                stopPingLoop()
                if (!isUserLogout) {
                    _connectionStatus.value = ConnectionStatus.CONNECTING
                    scheduleReconnect()
                } else {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }
            }
        })
    }

    fun requestServerData() {
        webSocket?.send("""{"action":"userinfo"}""")
        webSocket?.send("""{"action":"meshes"}""")
        webSocket?.send("""{"action":"nodes"}""")
        webSocket?.send("""{"action":"authcookie"}""")
    }

    fun requestTunnel(nodeId: String, protocol: Int, tunnelId: String) {
        scope.launch {
            if (relayAuthCookie.isEmpty()) {
                webSocket?.send("""{"action":"authcookie"}""")
                delay(150)
            }
            val cleanNodeId = nodeId.removePrefix("node//").removePrefix("node/").trim()
            val fullNodeId = "node//$cleanNodeId"
            val encodedNodeId = try { URLEncoder.encode(fullNodeId, "UTF-8") } catch (e: Exception) { fullNodeId }

            val rcookieToken = relayAuthCookie.ifEmpty { extractRawToken(authCookie) }
            val encodedRCookie = try { URLEncoder.encode(rcookieToken, "UTF-8") } catch (e: Exception) { rcookieToken }

            val valueStr = "*/meshrelay.ashx?p=$protocol&nodeid=$encodedNodeId&id=$tunnelId&rauth=$encodedRCookie&auth=$encodedRCookie"

            val tunnelMsg = JsonObject().apply {
                addProperty("action", "msg")
                addProperty("type", "tunnel")
                addProperty("nodeid", fullNodeId)
                addProperty("value", valueStr)
                addProperty("usage", protocol)
                addProperty("rights", 0xFFFFFFFF.toInt())
            }
            webSocket?.send(tunnelMsg.toString())
            Log.d("MeshCentralClient", "Sent tunnel request to control socket: $tunnelMsg")
        }
    }

    fun sendTerminalResize(nodeId: String, cols: Int, rows: Int) {
        if (cols < 5 || rows < 2) return
        val fullNodeId = if (!nodeId.startsWith("node//")) "node//" + nodeId.removePrefix("node/") else nodeId

        val msg1 = JsonObject().apply {
            addProperty("action", "msg")
            addProperty("type", "termsize")
            addProperty("nodeid", fullNodeId)
            addProperty("cols", cols)
            addProperty("rows", rows)
        }
        webSocket?.send(msg1.toString())

        val msg2 = JsonObject().apply {
            addProperty("action", "termsize")
            addProperty("nodeid", fullNodeId)
            addProperty("cols", cols)
            addProperty("rows", rows)
        }
        webSocket?.send(msg2.toString())

        Log.d("MeshCentralClient", "Sent PTY termsize to control socket: cols=$cols, rows=$rows")
    }

    private fun handleControlMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java) ?: return
            val action = json.get("action")?.asString ?: return

            Log.d("MeshControlRecv", "Control socket received: $text")

            when (action) {
                "serverinfo", "userinfo", "authcookie" -> {
                    if (action == "authcookie") {
                        val cookieVal = json.get("cookie")?.asString ?: ""
                        val rcookieVal = json.get("rcookie")?.asString ?: ""
                        if (rcookieVal.isNotEmpty()) {
                            relayAuthCookie = rcookieVal
                            Log.d("MeshCentralClient", "Received relay auth cookie (rcookie): $relayAuthCookie")
                        }
                        if (cookieVal.isNotEmpty()) {
                            userAuthCookie = cookieVal
                            Log.d("MeshCentralClient", "Received user auth cookie (cookie): $userAuthCookie")
                        }
                    }
                    if (action == "userinfo") {
                        val info = json.getAsJsonObject("userinfo")
                        if (info != null) {
                            val user = gson.fromJson(info, UserInfo::class.java)
                            _userInfo.value = user
                        }
                    }
                    if (meshesMap.isEmpty() || nodesMap.isEmpty()) {
                        scope.launch {
                            delay(100)
                            requestServerData()
                        }
                    }
                }
                "meshes" -> {
                    val meshesElem = json.get("meshes")
                    if (meshesElem != null) {
                        if (meshesElem.isJsonObject) {
                            val meshesObj = meshesElem.asJsonObject
                            for (entry in meshesObj.entrySet()) {
                                if (entry.value.isJsonObject) {
                                    val parsedMesh = gson.fromJson(entry.value, MeshGroup::class.java)
                                    val id = parsedMesh.id.ifEmpty { entry.key }
                                    val mesh = parsedMesh.copy(id = id)
                                    if (mesh.id.isNotEmpty()) {
                                        meshesMap[mesh.id] = mesh
                                    }
                                }
                            }
                        } else if (meshesElem.isJsonArray) {
                            for (meshElem in meshesElem.asJsonArray) {
                                if (meshElem.isJsonObject) {
                                    val mesh = gson.fromJson(meshElem, MeshGroup::class.java)
                                    if (mesh?.id?.isNotEmpty() == true) {
                                        meshesMap[mesh.id] = mesh
                                    }
                                }
                            }
                        }
                        updateGroupedDevices()
                    }
                }
                "nodes" -> {
                    val nodesElem = json.get("nodes")
                    if (nodesElem != null) {
                        if (nodesElem.isJsonObject) {
                            val nodesObj = nodesElem.asJsonObject
                            for (entry in nodesObj.entrySet()) {
                                val value = entry.value
                                if (value.isJsonArray) {
                                    for (nodeElem in value.asJsonArray) {
                                        if (nodeElem.isJsonObject) {
                                            val node = gson.fromJson(nodeElem, MeshNode::class.java)
                                            val nodeId = node.id.ifEmpty {
                                                nodeElem.asJsonObject.get("_id")?.asString ?: ""
                                            }
                                            val meshId = node.meshid.ifEmpty { entry.key }
                                            val fullNode = node.copy(id = nodeId, meshid = meshId)
                                            if (fullNode.id.isNotEmpty()) {
                                                nodesMap[fullNode.id] = fullNode
                                            }
                                        }
                                    }
                                } else if (value.isJsonObject) {
                                    val node = gson.fromJson(value, MeshNode::class.java)
                                    val nodeId = node.id.ifEmpty { entry.key }
                                    val fullNode = node.copy(id = nodeId)
                                    if (fullNode.id.isNotEmpty()) {
                                        nodesMap[fullNode.id] = fullNode
                                    }
                                }
                            }
                        } else if (nodesElem.isJsonArray) {
                            for (nodeElem in nodesElem.asJsonArray) {
                                if (nodeElem.isJsonObject) {
                                    val node = gson.fromJson(nodeElem, MeshNode::class.java)
                                    if (node?.id?.isNotEmpty() == true) {
                                        nodesMap[node.id] = node
                                    }
                                }
                            }
                        }
                        updateGroupedDevices()
                        if (meshesMap.isEmpty()) {
                            scope.launch {
                                delay(300)
                                webSocket?.send("""{"action":"meshes"}""")
                            }
                        }
                    }
                }
                "nodeconnect" -> {
                    val nodeObj = json.getAsJsonObject("node")
                    if (nodeObj != null) {
                        val node = gson.fromJson(nodeObj, MeshNode::class.java)
                        if (node?.id?.isNotEmpty() == true) {
                            nodesMap[node.id] = node
                            updateGroupedDevices()
                            onNodeConnectedListener?.invoke(node.id)
                        }
                    } else {
                        val conn = json.get("conn")?.asInt ?: 0
                        val nodeId = json.get("nodeid")?.asString ?: ""
                        if (conn == 1 && nodeId.isNotEmpty()) {
                            onNodeConnectedListener?.invoke(nodeId)
                        }
                    }
                }
                "event" -> {
                    val eventObj = json.getAsJsonObject("event")
                    if (eventObj != null) {
                        val eventAction = eventObj.get("action")?.asString ?: ""
                        val conn = eventObj.get("conn")?.asInt ?: 0
                        val nodeId = eventObj.get("nodeid")?.asString ?: ""
                        if ((eventAction == "nodeconnect" || eventAction == "changenode") && conn == 1 && nodeId.isNotEmpty()) {
                            Log.d("MeshCentralClient", "Node re-connected via event: $nodeId")
                            onNodeConnectedListener?.invoke(nodeId)
                        }
                    }
                }
                "nodedisconnect" -> {
                    val nodeId = json.get("nodeid")?.asString
                        ?: json.getAsJsonObject("node")?.get("_id")?.asString
                    if (nodeId != null) {
                        val existing = nodesMap[nodeId]
                        if (existing != null) {
                            nodesMap[nodeId] = existing.copy(conn = 0)
                            updateGroupedDevices()
                        }
                    }
                }
                "changenode" -> {
                    val nodeObj = json.getAsJsonObject("node")
                    if (nodeObj != null) {
                        val node = gson.fromJson(nodeObj, MeshNode::class.java)
                        if (node?.id?.isNotEmpty() == true) {
                            nodesMap[node.id] = node
                            updateGroupedDevices()
                            if (node.isOnline) {
                                onNodeConnectedListener?.invoke(node.id)
                            }
                        }
                    }
                }
                "ping" -> {
                    val pong = JsonObject().apply { addProperty("action", "pong") }
                    webSocket?.send(pong.toString())
                }
                "msg", "clipboard", "getclip", "setclip" -> {
                    val msgType = json.get("type")?.asString ?: json.get("action")?.asString ?: ""
                    val nodeId = json.get("nodeid")?.asString ?: json.get("node")?.asString ?: ""
                    val clipData = json.get("data")?.asString
                        ?: json.get("text")?.asString
                        ?: json.get("clip")?.asString
                        ?: json.get("value")?.asString
                        ?: ""
                    Log.d("MeshCentralClient", "Control message clipboard action=$action, msgType=$msgType, clipData='$clipData'")
                    if (clipData.isNotEmpty()) {
                        onClipboardReceivedListener?.invoke(nodeId, clipData)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun matchesMesh(nodeMeshId: String, meshId: String): Boolean {
        if (nodeMeshId.equals(meshId, ignoreCase = true)) return true
        if (nodeMeshId.isBlank() || meshId.isBlank()) return false
        val cleanNodeMesh = nodeMeshId.removePrefix("mesh//").removePrefix("mesh/").trim()
        val cleanMesh = meshId.removePrefix("mesh//").removePrefix("mesh/").trim()
        return cleanNodeMesh.equals(cleanMesh, ignoreCase = true)
    }

    private fun updateGroupedDevices() {
        val groupedList = mutableListOf<Pair<MeshGroup, List<MeshNode>>>()
        val assignedNodeIds = mutableSetOf<String>()

        // Sort groups/meshes alphabetically by name
        val sortedMeshes = meshesMap.values.sortedBy { it.name.lowercase() }

        // Process known meshes in alphabetical order
        for (mesh in sortedMeshes) {
            val matchingNodes = nodesMap.values.filter { node ->
                matchesMesh(node.meshid, mesh.id)
            }
            if (matchingNodes.isNotEmpty()) {
                matchingNodes.forEach { assignedNodeIds.add(it.id) }

                // Sort nodes in each group: Online FIRST, then Offline (alphabetically by name within each state)
                val sortedNodes = matchingNodes.sortedWith(
                    compareByDescending<MeshNode> { it.isOnline }
                        .thenBy { it.name.lowercase() }
                )
                groupedList.add(Pair(mesh, sortedNodes))
            }
        }

        // Process orphan nodes without group or unknown mesh
        val orphanNodes = nodesMap.values.filter { it.id !in assignedNodeIds }
        if (orphanNodes.isNotEmpty()) {
            val defaultGroup = MeshGroup(id = "default", name = "Equipos General")
            val sortedOrphans = orphanNodes.sortedWith(
                compareByDescending<MeshNode> { it.isOnline }
                    .thenBy { it.name.lowercase() }
            )
            groupedList.add(Pair(defaultGroup, sortedOrphans))
        }

        _groupedDevices.value = groupedList
    }

    private fun startPingLoop() {
        stopPingLoop()
        pingJob = scope.launch {
            while (true) {
                delay(25000)
                val ping = JsonObject().apply { addProperty("action", "ping") }
                webSocket?.send(ping.toString())
            }
        }
    }

    private fun stopPingLoop() {
        pingJob?.cancel()
        pingJob = null
    }

    fun sendClipboardRequest(nodeId: String) {
        if (webSocket == null) return
        val fullNodeId = if (!nodeId.startsWith("node//")) "node//" + nodeId.removePrefix("node/") else nodeId

        val msg1 = JsonObject().apply {
            addProperty("action", "msg")
            addProperty("type", "getclip")
            addProperty("nodeid", fullNodeId)
        }
        val msg2 = JsonObject().apply {
            addProperty("action", "getclip")
            addProperty("nodeid", fullNodeId)
        }
        val msg3 = JsonObject().apply {
            addProperty("action", "getclip")
        }
        val msg4 = JsonObject().apply {
            addProperty("action", "msg")
            addProperty("type", "getclipboard")
            addProperty("nodeid", fullNodeId)
        }
        try {
            webSocket?.send(msg1.toString())
            webSocket?.send(msg2.toString())
            webSocket?.send(msg3.toString())
            webSocket?.send(msg4.toString())
            Log.d("MeshCentralClient", "Sent getclip requests via control socket to $fullNodeId")
        } catch (e: Exception) {
            Log.e("MeshCentralClient", "Error sending getclip request: ${e.message}")
        }
    }

    fun sendClipboardText(nodeId: String, text: String) {
        if (webSocket == null) return
        val fullNodeId = if (!nodeId.startsWith("node//")) "node//" + nodeId.removePrefix("node/") else nodeId

        val msg1 = JsonObject().apply {
            addProperty("action", "msg")
            addProperty("type", "setclip")
            addProperty("nodeid", fullNodeId)
            addProperty("data", text)
        }
        val msg2 = JsonObject().apply {
            addProperty("action", "setclip")
            addProperty("nodeid", fullNodeId)
            addProperty("data", text)
        }
        val msg3 = JsonObject().apply {
            addProperty("action", "setclip")
            addProperty("data", text)
        }
        val msg4 = JsonObject().apply {
            addProperty("action", "msg")
            addProperty("type", "clipboard")
            addProperty("nodeid", fullNodeId)
            addProperty("data", text)
        }
        try {
            webSocket?.send(msg1.toString())
            webSocket?.send(msg2.toString())
            webSocket?.send(msg3.toString())
            webSocket?.send(msg4.toString())
            Log.d("MeshCentralClient", "Sent setclip text via control socket to $fullNodeId: '$text'")
        } catch (e: Exception) {
            Log.e("MeshCentralClient", "Error sending setclip text: ${e.message}")
        }
    }

    fun hasActiveSessions(): Boolean {
        return _connectionState.value && webSocket != null
    }

    fun onNetworkChanged() {
        if (!isUserLogout && !_connectionState.value) {
            reconnectNow()
        }
    }

    fun disconnect() {
        isUserLogout = true
        reconnectJob?.cancel()
        reconnectJob = null
        stopPingLoop()
        webSocket?.close(1000, "User logout")
        webSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _connectionState.value = false
        meshesMap.clear()
        nodesMap.clear()
        _groupedDevices.value = emptyList()
    }
}
