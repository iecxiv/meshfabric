package iecxiv.meshfabric.data.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

enum class TerminalConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class TerminalSession(
    val nodeId: String,
    val meshClient: MeshCentralClient,
    private val scope: CoroutineScope
) {
    val cleanNodeId = nodeId.removePrefix("node//").removePrefix("node/").trim()
    val fullNodeId = "node//$cleanNodeId"

    var activeSocket: WebSocket? = null
    var isUserDisconnect = false
    var hasPrintedDisconnectMessage = false
    var reconnectJob: Job? = null
    var activeTunnelId = Random.nextInt(100000, 999999).toString()

    var lastCols = 80
    var lastRows = 25

    private val sessionHistory = StringBuilder()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _connectionStatus = MutableStateFlow(TerminalConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<TerminalConnectionStatus> = _connectionStatus.asStateFlow()

    private val _incomingDataFlow = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val incomingDataFlow: SharedFlow<String> = _incomingDataFlow.asSharedFlow()

    fun isConnected(): Boolean {
        return _connectionState.value && activeSocket != null
    }

    @Synchronized
    fun getFullHistory(): String {
        return sessionHistory.toString()
    }

    @Synchronized
    fun clearHistory() {
        sessionHistory.clear()
    }

    private fun getBufferFile(context: Context): File {
        val dir = File(context.filesDir, "terminal_buffers")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "buffer_$cleanNodeId.txt")
    }

    fun saveBufferToDisk(context: Context) {
        try {
            val file = getBufferFile(context)
            synchronized(sessionHistory) {
                if (sessionHistory.isNotEmpty()) {
                    file.writeText(sessionHistory.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadBufferFromDisk(context: Context): Boolean {
        return try {
            val file = getBufferFile(context)
            if (file.exists() && file.length() > 0) {
                val content = file.readText()
                synchronized(sessionHistory) {
                    sessionHistory.clear()
                    sessionHistory.append(content)
                }
                hasPrintedDisconnectMessage = true
                _connectionStatus.value = TerminalConnectionStatus.DISCONNECTED
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteBufferFromDisk(context: Context) {
        try {
            val file = getBufferFile(context)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDisconnectMessage(): String {
        return try {
            iecxiv.meshfabric.MeshFabricApp.instance.getString(iecxiv.meshfabric.R.string.terminal_session_disconnected)
        } catch (_: Exception) {
            "[--sesion desconectada, cambio de red / tunel cerrado--]"
        }
    }

    private fun emitDisconnectBanner() {
        if (isUserDisconnect || hasPrintedDisconnectMessage) return
        hasPrintedDisconnectMessage = true
        val msg = getDisconnectMessage()
        val disconnectBanner = "\r\n\u001b[38;5;130m$msg\u001b[0m\r\n"
        synchronized(sessionHistory) {
            sessionHistory.append(disconnectBanner)
        }
        _incomingDataFlow.tryEmit(disconnectBanner)
        saveBufferToDisk(iecxiv.meshfabric.MeshFabricApp.instance)
        onStateChanged?.invoke()
    }

    fun connect(sendTunnelMsg: Boolean = true) {
        if (_connectionState.value && activeSocket != null) {
            Log.d("TerminalSession", "Session for $cleanNodeId ALREADY connected. Skipping connect.")
            scope.launch {
                delay(100)
                sendResize(lastCols, lastRows)
            }
            return
        }

        if (!_connectionState.value && sessionHistory.isNotEmpty() && !isUserDisconnect) {
            Log.d("TerminalSession", "Session for $cleanNodeId is disconnected with buffer. Preserving buffer for consultation.")
            _connectionStatus.value = TerminalConnectionStatus.DISCONNECTED
            return
        }

        isUserDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        _connectionStatus.value = TerminalConnectionStatus.CONNECTING

        val serverUrl = meshClient.serverUrl
        val cookie = meshClient.authCookie
        val ignoreSsl = meshClient.ignoreSsl

        val client = OkHttpHelper.createClient(ignoreSsl)
        val tunnelId = activeTunnelId.ifEmpty { Random.nextInt(100000, 999999).toString().also { activeTunnelId = it } }

        val relayToken = meshClient.relayAuthCookie.ifEmpty {
            meshClient.userAuthCookie.ifEmpty {
                meshClient.extractRawToken(cookie)
            }
        }
        val encodedRelayToken = try { URLEncoder.encode(relayToken, "UTF-8") } catch (e: Exception) { relayToken }
        val encodedNodeId = try { URLEncoder.encode(fullNodeId, "UTF-8") } catch (e: Exception) { fullNodeId }

        val protocol = 1
        val wsUrl = OkHttpHelper.toWebSocketUrl(
            serverUrl,
            "/meshrelay.ashx?p=$protocol&nodeid=$encodedNodeId&id=$tunnelId&rauth=$encodedRelayToken&auth=$encodedRelayToken"
        )

        Log.d("TerminalSession", "Connecting WS ($cleanNodeId, id=$tunnelId) to $wsUrl")

        val requestBuilder = Request.Builder().url(wsUrl)
        if (cookie.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookie)
        }

        closeSocketOnly()

        activeSocket = client.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("TerminalSession", "WS onOpen ($cleanNodeId). sendTunnelMsg=$sendTunnelMsg")
                if (sendTunnelMsg) {
                    meshClient.requestTunnel(nodeId = fullNodeId, protocol = protocol, tunnelId = tunnelId)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingData(webSocket, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncomingData(webSocket, bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (activeSocket == webSocket || _connectionState.value) {
                    updateState(false)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val wasActive = (activeSocket == webSocket || _connectionState.value)
                if (wasActive) {
                    activeSocket = null
                    updateState(false)
                    if (!isUserDisconnect) {
                        emitDisconnectBanner()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TerminalSession", "WS onFailure ($cleanNodeId): ${t.localizedMessage}", t)
                val wasActive = (activeSocket == webSocket || _connectionState.value)
                if (wasActive) {
                    activeSocket = null
                    updateState(false)
                    if (!isUserDisconnect) {
                        emitDisconnectBanner()
                    }
                }
            }
        })
    }

    private fun handleIncomingData(webSocket: WebSocket, data: String) {
        if (data == "c" || data == "cr" || data == "1") {
            Log.d("TerminalSession", "Handshake received ($cleanNodeId): $data")
            val wasConnected = _connectionState.value
            updateState(true)
            hasPrintedDisconnectMessage = false
            reconnectJob?.cancel()
            reconnectJob = null

            if (!wasConnected) {
                webSocket.send("1")

                val c = lastCols
                val r = lastRows
                lastCols = 0
                lastRows = 0

                scope.launch {
                    delay(100)
                    sendResize(if (c >= 5) c else 80, if (r >= 2) r else 25, force = true)
                }
            }
            return
        }

        if (_connectionState.value) {
            synchronized(sessionHistory) {
                sessionHistory.append(data)
                if (sessionHistory.length > 200000) {
                    sessionHistory.delete(0, 50000)
                }
            }
            _incomingDataFlow.tryEmit(data)
        }
    }

    fun onNetworkChanged() {
        if (!isUserDisconnect && _connectionState.value) {
            Log.d("TerminalSession", "Network change detected for $cleanNodeId, closing tunnel...")
            emitDisconnectBanner()
            closeSocketOnly()
            updateState(false)
        }
    }

    fun sendInput(text: String) {
        if (!_connectionState.value) return
        val bytes = text.toByteArray(Charsets.UTF_8)
        activeSocket?.send(ByteString.of(*bytes))
    }

    fun sendControlKey(keyChar: Char) {
        if (!_connectionState.value) return
        val code = (keyChar.uppercaseChar().code - 64)
        if (code in 1..26) {
            val bytes = byteArrayOf(code.toByte())
            activeSocket?.send(ByteString.of(*bytes))
        }
    }

    fun sendResize(cols: Int, rows: Int, force: Boolean = false) {
        if (cols < 5 || rows < 2) return
        if (!force && cols == lastCols && rows == lastRows) return

        lastCols = cols
        lastRows = rows

        if (_connectionState.value && activeSocket != null) {
            val json1 = "{\"action\":\"termsize\",\"cols\":$cols,\"rows\":$rows}"
            val json2 = "{\"type\":\"termsize\",\"cols\":$cols,\"rows\":$rows}"
            val json3 = "{\"action\":\"resize\",\"cols\":$cols,\"rows\":$rows}"
            activeSocket?.send(json1)
            activeSocket?.send(json2)
            activeSocket?.send(json3)
        }
    }

    fun closeSocketOnly() {
        activeSocket?.close(1000, "Terminal session closed")
        activeSocket = null
    }

    var onStateChanged: (() -> Unit)? = null

    fun updateState(connected: Boolean) {
        _connectionState.value = connected
        _connectionStatus.value = if (connected) TerminalConnectionStatus.CONNECTED else TerminalConnectionStatus.DISCONNECTED
        onStateChanged?.invoke()
    }

    fun disconnect() {
        isUserDisconnect = true
        hasPrintedDisconnectMessage = false
        reconnectJob?.cancel()
        reconnectJob = null
        activeSocket?.close(1000, "Disconnecting terminal session")
        activeSocket = null
        updateState(false)
        clearHistory()
        deleteBufferFromDisk(iecxiv.meshfabric.MeshFabricApp.instance)
    }
}

class TerminalRelayClient {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val sessions = ConcurrentHashMap<String, TerminalSession>()

    private val _activeNodes = MutableStateFlow<Set<String>>(emptySet())
    val activeNodesFlow: StateFlow<Set<String>> = _activeNodes.asStateFlow()

    private val _sessionsStatus = MutableStateFlow<Map<String, iecxiv.meshfabric.data.model.SessionStatus>>(emptyMap())
    val terminalSessionsStatusFlow: StateFlow<Map<String, iecxiv.meshfabric.data.model.SessionStatus>> = _sessionsStatus.asStateFlow()

    fun initBuffersFromDisk(context: Context, meshClient: MeshCentralClient, isEnabled: Boolean = true) {
        try {
            val dir = File(context.filesDir, "terminal_buffers")
            if (!isEnabled) {
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
                return
            }
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.name.startsWith("buffer_") && f.name.endsWith(".txt") && f.length() > 0) {
                        val key = f.name.removePrefix("buffer_").removeSuffix(".txt")
                        val session = getSession(meshClient, key)
                        session.loadBufferFromDisk(context)
                    }
                }
                updateSessionsStatus()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveAllBuffersAndClose(context: Context, isEnabled: Boolean = true) {
        val dir = File(context.filesDir, "terminal_buffers")
        for (session in sessions.values) {
            if (!session.isUserDisconnect) {
                if (isEnabled) {
                    session.saveBufferToDisk(context)
                } else {
                    session.deleteBufferFromDisk(context)
                }
                session.closeSocketOnly()
                session.updateState(false)
            }
        }
        if (!isEnabled && dir.exists()) {
            dir.deleteRecursively()
        }
        updateSessionsStatus()
    }

    fun updateSessionsStatus() {
        val map = sessions.mapKeys { cleanKey(it.key) }.mapValues { entry ->
            val sess = entry.value
            when {
                sess.isConnected() -> iecxiv.meshfabric.data.model.SessionStatus.CONNECTED
                sess.getFullHistory().isNotEmpty() && !sess.isUserDisconnect -> iecxiv.meshfabric.data.model.SessionStatus.DISCONNECTED_WITH_BUFFER
                else -> iecxiv.meshfabric.data.model.SessionStatus.NONE
            }
        }
        _sessionsStatus.value = map
        updateActiveNodes()
    }

    fun updateActiveNodes() {
        val active = sessions.entries
            .filter { it.value.isConnected() }
            .map { it.key }
            .toSet()
        _activeNodes.value = active
    }

    private fun cleanKey(nodeId: String): String {
        return nodeId.removePrefix("node//").removePrefix("node/").trim()
    }

    @Synchronized
    fun getSession(meshClient: MeshCentralClient, nodeId: String): TerminalSession {
        val key = cleanKey(nodeId)
        return sessions.getOrPut(key) {
            TerminalSession(nodeId = key, meshClient = meshClient, scope = scope).also { sess ->
                sess.onStateChanged = { updateSessionsStatus() }
            }
        }
    }

    fun isConnectedToNode(nodeId: String): Boolean {
        val session = sessions[cleanKey(nodeId)]
        return session?.isConnected() ?: false
    }

    fun getConnectionState(nodeId: String): StateFlow<Boolean> {
        val session = sessions[cleanKey(nodeId)]
        return session?.connectionState ?: MutableStateFlow(false).asStateFlow()
    }

    fun getConnectionStatus(nodeId: String): StateFlow<TerminalConnectionStatus> {
        val session = sessions[cleanKey(nodeId)]
        return session?.connectionStatus ?: MutableStateFlow(TerminalConnectionStatus.CONNECTING).asStateFlow()
    }

    fun getIncomingDataFlow(nodeId: String): SharedFlow<String> {
        val session = sessions[cleanKey(nodeId)]
        return session?.incomingDataFlow ?: MutableSharedFlow<String>().asSharedFlow()
    }

    fun getFullHistory(nodeId: String): String {
        val session = sessions[cleanKey(nodeId)]
        return session?.getFullHistory() ?: ""
    }

    fun connect(meshClient: MeshCentralClient, nodeId: String) {
        val session = getSession(meshClient, nodeId)
        session.connect(sendTunnelMsg = true)
        updateSessionsStatus()
    }

    fun restartSession(meshClient: MeshCentralClient, nodeId: String) {
        val key = cleanKey(nodeId)
        val session = sessions.remove(key)
        session?.disconnect()

        val newSession = getSession(meshClient, nodeId)
        newSession.isUserDisconnect = false
        newSession.clearHistory()
        newSession.deleteBufferFromDisk(context = iecxiv.meshfabric.MeshFabricApp.instance)
        newSession.connect(sendTunnelMsg = true)
        updateSessionsStatus()
    }

    fun hasActiveSessions(): Boolean {
        return sessions.values.any { it.isConnected() }
    }

    fun getSessionStatus(nodeId: String): iecxiv.meshfabric.data.model.SessionStatus {
        val session = sessions[cleanKey(nodeId)] ?: return iecxiv.meshfabric.data.model.SessionStatus.NONE
        return when {
            session.isConnected() -> iecxiv.meshfabric.data.model.SessionStatus.CONNECTED
            session.getFullHistory().isNotEmpty() && !session.isUserDisconnect -> iecxiv.meshfabric.data.model.SessionStatus.DISCONNECTED_WITH_BUFFER
            else -> iecxiv.meshfabric.data.model.SessionStatus.NONE
        }
    }

    fun hasDisconnectedWithBuffer(): Boolean {
        return sessions.values.any { !it.isConnected() && it.getFullHistory().isNotEmpty() && !it.isUserDisconnect }
    }

    fun getSessionCount(): Int {
        return sessions.values.count { (it.isConnected() || it.getFullHistory().isNotEmpty()) && !it.isUserDisconnect }
    }

    fun onNetworkChanged() {
        for (session in sessions.values) {
            if (!session.isUserDisconnect) {
                session.onNetworkChanged()
            }
        }
    }

    fun sendInput(nodeId: String, text: String) {
        sessions[cleanKey(nodeId)]?.sendInput(text)
    }

    fun sendControlKey(nodeId: String, keyChar: Char) {
        sessions[cleanKey(nodeId)]?.sendControlKey(keyChar)
    }

    fun sendResize(nodeId: String, cols: Int, rows: Int) {
        sessions[cleanKey(nodeId)]?.sendResize(cols, rows)
    }

    fun disconnectSession(nodeId: String) {
        val key = cleanKey(nodeId)
        val session = sessions.remove(key)
        session?.disconnect()
        updateSessionsStatus()
    }

    fun disconnectAll() {
        for (session in sessions.values) {
            session.disconnect()
        }
        sessions.clear()
        updateSessionsStatus()
    }
}
