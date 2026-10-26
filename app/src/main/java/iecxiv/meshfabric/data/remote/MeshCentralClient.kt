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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var okHttpClient: OkHttpClient? = null
    @Volatile private var webSocket: WebSocket? = null
    private val controlGeneration=SocketGeneration()
    private var controlConnecting=false
    private var controlIdentity:Triple<String,String,Boolean>?=null
    private var controlTimeout:Job?=null
    private val controlWork=mutableSetOf<Job>()
    private var controlConfigValid=true

    private var pingJob: Job? = null
    private var nodePollJob: Job? = null

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

    private val controlRetries=ControlRetry(scope,controlGeneration,
        shouldConnect={ !isUserLogout && controlConfigValid && serverUrl.isNotBlank() && authCookie.isNotBlank() && !_connectionState.value && !controlConnecting },
        connect={ connectControlSocket(serverUrl,authCookie,ignoreSsl) },
        onError={
            controlConnecting=false
            _connectionState.value=false
            _connectionStatus.value=ConnectionStatus.CONNECTING
            Log.e("MeshCentralClient","Cannot start control connection (${it.javaClass.simpleName})")
        })
    @Volatile var isUserLogout: Boolean = false

    fun reconnectNow() { controlRetries.request(true) }

    private fun scheduleReconnect() { controlRetries.request() }

    fun connectControlSocket(rawServerUrl:String,cookie:String,ignoreSslCert:Boolean=false) {
        controlGeneration.locked {
            val url=OkHttpHelper.sanitizeServerUrl(rawServerUrl)
            val identity=Triple(url,cookie,ignoreSslCert)
            if(webSocket!=null && controlIdentity==identity && (controlConnecting || _connectionState.value)) return
            val epoch=controlGeneration.advance()
            cancelControlWork()
            controlTimeout?.cancel()
            controlTimeout=null
            stopPingLoop()
            stopNodePollLoop()
            val previous=webSocket
            webSocket=null
            previous?.cancel()
            if(controlIdentity!=identity) {
                relayAuthCookie=""
                userAuthCookie=""
                _userInfo.value=null
                meshesMap.clear()
                nodesMap.clear()
                _groupedDevices.value=emptyList()
            }
            controlIdentity=identity
            isUserLogout=false
            serverUrl=url
            authCookie=cookie
            ignoreSsl=ignoreSslCert
            _connectionState.value=false
            _connectionStatus.value=ConnectionStatus.CONNECTING
            controlConnecting=true
            controlConfigValid=true
            try {
                val client=OkHttpHelper.createClient(ignoreSslCert)
                okHttpClient=client
                val request=Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1"))
                    .apply { if(cookie.isNotBlank()) header("Cookie",cookie) }.build()
                webSocket=client.newWebSocket(request,object:WebSocketListener() {
                    override fun onOpen(socket:WebSocket,response:Response) {
                        var accepted=false
                        controlGeneration.runIfCurrent(epoch) {
                            if(webSocket===socket && !isUserLogout) {
                                accepted=true
                                controlConnecting=false
                                controlTimeout?.cancel()
                                controlTimeout=null
                                _connectionStatus.value=ConnectionStatus.CONNECTED
                                _connectionState.value=true
                                controlRetries.cancel()
                                if(cookie.isNotBlank()) {
                                    val token=extractRawToken(cookie)
                                    val auth=JsonObject().apply {
                                        addProperty("action","urlargs")
                                        add("args",JsonObject().apply { addProperty("auth",token.ifEmpty { cookie }) })
                                    }
                                    socket.send(auth.toString())
                                }
                                launchControlWork { _ ->
                                    delay(150)
                                    requestServerData()
                                    delay(400)
                                    if(meshesMap.isEmpty() || nodesMap.isEmpty()) requestServerData()
                                }
                                startPingLoop()
                                startNodePollLoop()
                            }
                        }
                        if(!accepted) socket.cancel()
                    }
                    override fun onMessage(socket:WebSocket,text:String) {
                        controlGeneration.runIfCurrent(epoch) {
                            if(webSocket===socket && !isUserLogout) handleControlMessage(text)
                        }
                    }
                    override fun onClosing(socket:WebSocket,code:Int,reason:String) {
                        retireControlSocket(epoch,socket)
                        socket.close(code,reason)
                    }
                    override fun onClosed(socket:WebSocket,code:Int,reason:String) {
                        retireControlSocket(epoch,socket)
                    }
                    override fun onFailure(socket:WebSocket,t:Throwable,response:Response?) {
                        if(retireControlSocket(epoch,socket)) Log.e("MeshCentralClient","Control socket failed (${t.javaClass.simpleName})")
                    }
                })
                val socket=webSocket!!
                controlTimeout=scope.launch {
                    delay(20_000L)
                    controlGeneration.runIfCurrent(epoch) {
                        if(webSocket===socket && controlConnecting && !isUserLogout) {
                            retireControlSocket(epoch,socket)
                            socket.cancel()
                        }
                    }
                }
            } catch(_:IllegalArgumentException) {
                controlConfigValid=false
                controlGeneration.advance()
                controlConnecting=false
                webSocket=null
                _connectionState.value=false
                _connectionStatus.value=ConnectionStatus.DISCONNECTED
                Log.e("MeshCentralClient","Invalid control connection configuration")
            }
        }
    }

    fun requestServerData() {
        webSocket?.send("""{"action":"userinfo"}""")
        webSocket?.send("""{"action":"meshes"}""")
        webSocket?.send("""{"action":"nodes"}""")
        webSocket?.send("""{"action":"authcookie"}""")
    }

    fun requestTunnel(nodeId:String,protocol:Int,tunnelId:String) {
        val captured=webSocket ?: return
        scope.launch {
            if(relayAuthCookie.isEmpty()) {
                captured.send(JsonObject().apply { addProperty("action","authcookie") }.toString())
                delay(150)
            }
            controlGeneration.locked {
                if(webSocket!==captured || isUserLogout || !_connectionState.value) return@locked
                val fullNodeId="node//"+nodeId.removePrefix("node//").removePrefix("node/").trim()
                val encodedId=URLEncoder.encode(fullNodeId,"UTF-8")
                val token=URLEncoder.encode(relayAuthCookie.ifEmpty { extractRawToken(authCookie) },"UTF-8")
                val packet=JsonObject().apply {
                    addProperty("action","msg")
                    addProperty("type","tunnel")
                    addProperty("nodeid",fullNodeId)
                    addProperty("value","*/meshrelay.ashx?p=$protocol&nodeid=$encodedId&id=$tunnelId&rauth=$token&auth=$token")
                    addProperty("usage",protocol)
                    addProperty("rights",0xFFFFFFFF.toInt())
                }
                captured.send(packet.toString())
            }
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


            when (action) {
                "serverinfo", "userinfo", "authcookie" -> {
                    if (action == "authcookie") {
                        val cookieVal = json.get("cookie")?.asString ?: ""
                        val rcookieVal = json.get("rcookie")?.asString ?: ""
                        if (rcookieVal.isNotEmpty()) {
                            relayAuthCookie = rcookieVal
                        }
                        if (cookieVal.isNotEmpty()) {
                            userAuthCookie = cookieVal
                        }
                    }
                    if (action == "userinfo") {
                        val info = json.getAsJsonObject("userinfo")
                        if (info != null) {
                            val user = gson.fromJson(info, UserInfo::class.java)
                            _userInfo.value = user
                            controlRetries.reset()
                        }
                    }
                    if (meshesMap.isEmpty() || nodesMap.isEmpty()) {
                        launchControlWork { webSocket ->
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
                            launchControlWork { webSocket ->
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
                        val nodeId = json.get("nodeid")?.asString ?: json.get("node")?.asString ?: ""
                        if (nodeId.isNotEmpty()) {
                            val existing = nodesMap[nodeId] ?: nodesMap["node//$nodeId"]
                            if (existing != null) {
                                nodesMap[existing.id] = existing.copy(conn = 1)
                            }
                            updateGroupedDevices()
                            onNodeConnectedListener?.invoke(nodeId)
                        }
                    }
                    launchControlWork { webSocket ->
                        delay(100)
                        webSocket?.send("""{"action":"nodes"}""")
                    }
                }
                "event" -> {
                    val eventObj = json.getAsJsonObject("event")
                    if (eventObj != null) {
                        val eventAction = eventObj.get("action")?.asString ?: ""
                        val conn = eventObj.get("conn")?.asInt ?: 1
                        val nodeId = eventObj.get("nodeid")?.asString ?: eventObj.get("node")?.asString ?: ""
                        if (nodeId.isNotEmpty() || eventAction.contains("node", ignoreCase = true)) {
                            Log.d("MeshCentralClient", "Node event received: $eventAction for $nodeId")
                            if (nodeId.isNotEmpty()) {
                                val existing = nodesMap[nodeId] ?: nodesMap["node//$nodeId"]
                                if (existing != null) {
                                    nodesMap[existing.id] = existing.copy(conn = if (eventAction == "nodedisconnect") 0 else (if (conn > 0) conn else 1))
                                }
                            }
                            updateGroupedDevices()
                            if (nodeId.isNotEmpty() && eventAction != "nodedisconnect") {
                                onNodeConnectedListener?.invoke(nodeId)
                            }
                            launchControlWork { webSocket ->
                                delay(100)
                                webSocket?.send("""{"action":"nodes"}""")
                            }
                        }
                    }
                }
                "nodedisconnect" -> {
                    val nodeId = json.get("nodeid")?.asString
                        ?: json.getAsJsonObject("node")?.get("_id")?.asString
                        ?: json.get("node")?.asString
                    if (nodeId != null) {
                        val cleanId = nodeId.removePrefix("node//").removePrefix("node/").trim()
                        val existing = nodesMap[cleanId] ?: nodesMap["node//$cleanId"] ?: nodesMap[nodeId]
                        if (existing != null) {
                            nodesMap[existing.id] = existing.copy(conn = 0)
                            updateGroupedDevices()
                        }
                    }
                    launchControlWork { webSocket ->
                        delay(100)
                        webSocket?.send("""{"action":"nodes"}""")
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
                    launchControlWork { webSocket ->
                        delay(100)
                        webSocket?.send("""{"action":"nodes"}""")
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
        pingJob = launchControlWork { webSocket ->
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

    private fun startNodePollLoop() {
        stopNodePollLoop()
        nodePollJob = launchControlWork { webSocket ->
            while (true) {
                delay(10000)
                if (_connectionState.value && webSocket != null) {
                    webSocket?.send("""{"action":"nodes"}""")
                    webSocket?.send("""{"action":"meshes"}""")
                }
            }
        }
    }

    private fun stopNodePollLoop() {
        nodePollJob?.cancel()
        nodePollJob = null
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
        controlGeneration.locked {
            if(isUserLogout) return
            controlGeneration.advance()
            controlConnecting=false
            controlTimeout?.cancel()
            controlTimeout=null
            cancelControlWork()
            stopPingLoop()
            stopNodePollLoop()
            val old=webSocket
            webSocket=null
            _connectionState.value=false
            controlRetries.cancel()
            old?.cancel()
            _connectionStatus.value=if(serverUrl.isBlank() || authCookie.isBlank()) ConnectionStatus.DISCONNECTED else ConnectionStatus.CONNECTING
            controlRetries.request(true)
        }
    }

    fun disconnect() {
        controlGeneration.locked {
            isUserLogout=true
            controlConnecting=false
            controlGeneration.advance()
            controlTimeout?.cancel()
            controlTimeout=null
            controlRetries.cancel()
            cancelControlWork()
            stopPingLoop()
            stopNodePollLoop()
            val old=webSocket
            webSocket=null
            old?.cancel()
            _connectionState.value=false
            _connectionStatus.value=ConnectionStatus.DISCONNECTED

        isUserLogout = true
        controlRetries.cancel()
        stopPingLoop()
        stopNodePollLoop()
        webSocket?.close(1000, "User logout")
        webSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _connectionState.value = false
        meshesMap.clear()
        nodesMap.clear()
        _groupedDevices.value = emptyList()

        }
    }
    private fun cancelControlWork() {
        val jobs=controlWork.toList()
        controlWork.clear()
        jobs.forEach { it.cancel() }
    }

    private fun launchControlWork(block:suspend CoroutineScope.(WebSocket?)->Unit):Job = controlGeneration.locked {
        val epoch=controlGeneration.current()
        val captured=webSocket
        val job=scope.launch(start=CoroutineStart.LAZY) {
            val valid=controlGeneration.locked {
                captured!=null && controlGeneration.current()==epoch && webSocket===captured && !isUserLogout
            }
            if(valid) block(this,captured)
        }
        controlWork.add(job)
        job.invokeOnCompletion { controlGeneration.locked { controlWork.remove(job) } }
        job.start()
        job
    }

    private fun retireControlSocket(epoch:Long,socket:WebSocket):Boolean = controlGeneration.locked {
        if(controlGeneration.current()!=epoch || webSocket!==socket) return@locked false
        controlGeneration.advance()
        controlConnecting=false
        webSocket=null
        controlTimeout?.cancel()
        controlTimeout=null
        cancelControlWork()
        stopPingLoop()
        stopNodePollLoop()
        _connectionState.value=false
        _connectionStatus.value=if(isUserLogout) ConnectionStatus.DISCONNECTED else ConnectionStatus.CONNECTING
        if(!isUserLogout) controlRetries.request()
        true
    }

}
