package iecxiv.meshfabric.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

enum class TerminalConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

class TerminalSession(
    val nodeId: String,
    val meshClient: MeshCentralClient,
    private val scope: CoroutineScope
) {
    val cleanNodeId = nodeId.removePrefix("node//").removePrefix("node/").trim()
    val fullNodeId = "node//$cleanNodeId"
    private val generation = SocketGeneration()
    private val decoder = TerminalUtf8Decoder()
    private val resizeState = TerminalResizeState()
    private var handshakeComplete = false
    private var handshakeTimeoutJob: Job? = null
    private var resizeJob: Job? = null

    @Volatile var activeSocket: WebSocket? = null
    @Volatile var isUserDisconnect = false
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

    fun isConnected(): Boolean = _connectionState.value && activeSocket != null
    fun getFullHistory(): String = synchronized(sessionHistory) { sessionHistory.toString() }
    fun clearHistory() { synchronized(sessionHistory) { sessionHistory.clear() } }

    private fun getBufferFile(context: Context): File {
        val dir = File(context.filesDir, "terminal_buffers")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "buffer_$cleanNodeId.txt")
    }

    fun saveBufferToDisk(context: Context) {
        try {
            synchronized(sessionHistory) {
                if (sessionHistory.isNotEmpty()) getBufferFile(context).writeText(sessionHistory.toString())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadBufferFromDisk(context: Context): Boolean = try {
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
    } catch (e: Exception) { e.printStackTrace(); false }

    fun deleteBufferFromDisk(context: Context) {
        try { getBufferFile(context).delete() } catch (e: Exception) { e.printStackTrace() }
    }

    private fun appendOutput(data: String) {
        if (data.isEmpty()) return
        synchronized(sessionHistory) {
            sessionHistory.append(data)
            if (sessionHistory.length > 200000) sessionHistory.delete(0, sessionHistory.length - 150000)
        }
        // The snapshot/live handoff and lossless UI delivery are a separate refactor.
        if (!_incomingDataFlow.tryEmit(data)) Log.w("TerminalSession", "Output notification buffer full")
    }

    private fun emitDisconnectBanner() {
        if (isUserDisconnect || hasPrintedDisconnectMessage) return
        hasPrintedDisconnectMessage = true
        val message = try {
            iecxiv.meshfabric.MeshFabricApp.instance.getString(iecxiv.meshfabric.R.string.terminal_session_disconnected)
        } catch (_: Exception) { "[--sesion desconectada, cambio de red / tunel cerrado--]" }
        appendOutput("\r\n\u001b[38;5;130m$message\u001b[0m\r\n")
        saveBufferToDisk(iecxiv.meshfabric.MeshFabricApp.instance)
        onStateChanged?.invoke()
    }

    private fun resizeForSocket(epoch: Long, socket: WebSocket, force: Boolean = false) {
        resizeJob?.cancel()
        resizeJob = scope.launch {
            delay(100)
            generation.runIfCurrent(epoch) {
                if (activeSocket === socket && !isUserDisconnect && handshakeComplete) {
                    // Read the newest desired grid, never a captured pre-keyboard size.
                    sendResize(lastCols, lastRows, force)
                }
            }
        }
    }

    fun connect(sendTunnelMsg: Boolean = true) {
        generation.locked {
            val existing = activeSocket
            if (existing != null) {
                if (_connectionState.value) resizeForSocket(generation.current(), existing)
                return
            }
            if (!_connectionState.value && getFullHistory().isNotEmpty() && !isUserDisconnect) {
                _connectionStatus.value = TerminalConnectionStatus.DISCONNECTED
                return
            }
            val cookie = meshClient.authCookie
            if (meshClient.serverUrl.isBlank() || cookie.isBlank()) { updateState(false); return }
            val epoch = generation.advance()
            isUserDisconnect = false
            resetTransportState()
            reconnectJob?.cancel()
            reconnectJob = null
            _connectionStatus.value = TerminalConnectionStatus.CONNECTING
            try {
                val tunnelId = Random.nextInt(100000, 999999).toString()
                activeTunnelId = tunnelId
                val relayToken = meshClient.relayAuthCookie.ifEmpty {
                    meshClient.userAuthCookie.ifEmpty { meshClient.extractRawToken(cookie) }
                }
                val token = URLEncoder.encode(relayToken, "UTF-8")
                val node = URLEncoder.encode(fullNodeId, "UTF-8")
                val url = OkHttpHelper.toWebSocketUrl(meshClient.serverUrl,
                    "/meshrelay.ashx?p=1&nodeid=$node&id=$tunnelId&rauth=$token&auth=$token")
                val request = Request.Builder().url(url).header("Cookie", cookie).build()
                activeSocket = OkHttpHelper.createClient(meshClient.ignoreSsl).newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(socket: WebSocket, response: Response) {
                        var accepted = false
                        generation.runIfCurrent(epoch) {
                            if (activeSocket === socket && !isUserDisconnect) {
                                accepted = true
                                if (sendTunnelMsg) meshClient.requestTunnel(fullNodeId, 1, tunnelId)
                            }
                        }
                        if (!accepted) socket.cancel()
                    }
                    override fun onMessage(socket: WebSocket, text: String) {
                        generation.runIfCurrent(epoch) {
                            if (activeSocket === socket && !isUserDisconnect) handleText(epoch, socket, text)
                        }
                    }
                    override fun onMessage(socket: WebSocket, bytes: ByteString) {
                        generation.runIfCurrent(epoch) {
                            if (activeSocket === socket && !isUserDisconnect && handshakeComplete) {
                                // Binary output is never interpreted as handshake or JSON control.
                                appendOutput(decoder.decode(bytes.toByteArray()))
                            }
                        }
                    }
                    override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                        retireSocket(epoch, socket)
                        socket.close(code, reason)
                    }
                    override fun onClosed(socket: WebSocket, code: Int, reason: String) { retireSocket(epoch, socket) }
                    override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                        if (retireSocket(epoch, socket)) Log.w("TerminalSession", "Terminal transport failed (${t.javaClass.simpleName})")
                    }
                })
                val socket = activeSocket!!
                handshakeTimeoutJob = scope.launch {
                    delay(20_000L)
                    generation.runIfCurrent(epoch) {
                        if (activeSocket === socket && !handshakeComplete && !isUserDisconnect) {
                            retireSocket(epoch, socket)
                            socket.cancel()
                        }
                    }
                }
            } catch (_: IllegalArgumentException) {
                closeSocketOnly()
                Log.e("TerminalSession", "Invalid terminal connection configuration")
            }
        }
    }

    private fun resetTransportState() {
        handshakeComplete = false
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null
        resizeJob?.cancel()
        resizeJob = null
        decoder.reset()
        resizeState.reset()
    }

    private fun retireSocket(epoch: Long, socket: WebSocket): Boolean = generation.locked {
        if (generation.current() != epoch || activeSocket !== socket) return@locked false
        val established = handshakeComplete
        activeSocket = null
        resetTransportState()
        generation.advance()
        updateState(false)
        if (established && !isUserDisconnect) emitDisconnectBanner()
        true
    }

    private fun handleText(epoch: Long, socket: WebSocket, text: String) {
        if (!handshakeComplete) {
            if (!TerminalProtocol.isHandshake(text, false)) {
                handleControl(socket, text)
                return
            }
            val cols = lastCols.takeIf { it >= 5 } ?: 80
            val rows = lastRows.takeIf { it >= 2 } ?: 25
            // Match MeshCentral: options precede the protocol selector that starts the PTY.
            if (!socket.send(TerminalProtocol.options(cols, rows)) || !socket.send("1")) {
                retireSocket(epoch, socket)
                socket.cancel()
                return
            }
            lastCols = cols
            lastRows = rows
            handshakeComplete = true
            handshakeTimeoutJob?.cancel()
            handshakeTimeoutJob = null
            hasPrintedDisconnectMessage = false
            reconnectJob?.cancel()
            reconnectJob = null
            updateState(true)
            resizeForSocket(epoch, socket, true)
            return
        }
        if (!handleControl(socket, text)) appendOutput(text)
    }

    private fun handleControl(socket: WebSocket, text: String): Boolean {
        if (!text.trimStart().startsWith("{")) return false
        return try {
            val json = Gson().fromJson(text, JsonObject::class.java)
            if (json.get("ctrlChannel")?.asString != "102938") return false
            when (json.get("type")?.asString) {
                "ping" -> socket.send("{\"ctrlChannel\":\"102938\",\"type\":\"pong\"}")
                "console" -> {
                    val message = json.get("msg")?.asString.orEmpty().filter {
                        it == '\r' || it == '\n' || it == '\t' || (it.code >= 32 && it.code !in 127..159)
                    }
                    if (message.isNotBlank()) appendOutput("\r\n$message\r\n")
                }
            }
            true
        } catch (_: Exception) { false }
    }

    fun onNetworkChanged() {
        generation.locked {
            if (isUserDisconnect || (activeSocket == null && !_connectionState.value)) return
            val established = handshakeComplete
            val socket = activeSocket
            closeSocketOnly()
            socket?.cancel()
            if (established) emitDisconnectBanner()
        }
    }

    fun sendInput(text: String) {
        generation.locked {
            val socket = activeSocket ?: return@locked
            if (!_connectionState.value) return@locked
            if (!socket.send(ByteString.of(*text.toByteArray(Charsets.UTF_8)))) {
                retireSocket(generation.current(), socket)
                socket.cancel()
            }
        }
    }

    fun sendControlKey(keyChar: Char) {
        val code = keyChar.uppercaseChar().code - 64
        if (code in 1..26) sendInput(code.toChar().toString())
    }

    fun sendResize(cols: Int, rows: Int, force: Boolean = false) {
        generation.locked {
            if (cols < 5 || rows < 2) return@locked
            lastCols = cols
            lastRows = rows
            val socket = activeSocket ?: return@locked
            if (!handshakeComplete || !_connectionState.value) return@locked
            var rejected = false
            resizeState.send(cols, rows, force) { packet ->
                socket.send(packet).also { rejected = !it }
            }
            if (rejected) {
                retireSocket(generation.current(), socket)
                socket.cancel()
            }
        }
    }

    fun closeSocketOnly() {
        generation.locked {
            generation.advance()
            resetTransportState()
            val socket = activeSocket
            activeSocket = null
            updateState(false)
            socket?.close(1000, "Terminal session closed")
        }
    }

    var onStateChanged: (() -> Unit)? = null
    fun updateState(connected: Boolean) {
        _connectionState.value = connected
        _connectionStatus.value = if (connected) TerminalConnectionStatus.CONNECTED else TerminalConnectionStatus.DISCONNECTED
        onStateChanged?.invoke()
    }

    fun disconnect() {
        generation.locked {
            isUserDisconnect = true
            hasPrintedDisconnectMessage = false
            reconnectJob?.cancel()
            reconnectJob = null
            closeSocketOnly()
            clearHistory()
            deleteBufferFromDisk(iecxiv.meshfabric.MeshFabricApp.instance)
        }
    }
}

class TerminalRelayClient {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val _activeNodes = MutableStateFlow<Set<String>>(emptySet())
    val activeNodesFlow: StateFlow<Set<String>> = _activeNodes.asStateFlow()
    private val _sessionsStatus = MutableStateFlow<Map<String, iecxiv.meshfabric.data.model.SessionStatus>>(emptyMap())
    val terminalSessionsStatusFlow: StateFlow<Map<String, iecxiv.meshfabric.data.model.SessionStatus>> = _sessionsStatus.asStateFlow()

    fun initBuffersFromDisk(context: Context, meshClient: MeshCentralClient, isEnabled: Boolean = true) {
        try {
            val dir = File(context.filesDir, "terminal_buffers")
            if (!isEnabled) { if (dir.exists()) dir.deleteRecursively(); return }
            for (file in dir.listFiles().orEmpty()) {
                if (file.name.startsWith("buffer_") && file.name.endsWith(".txt") && file.length() > 0) {
                    getSession(meshClient, file.name.removePrefix("buffer_").removeSuffix(".txt")).loadBufferFromDisk(context)
                }
            }
            updateSessionsStatus()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun saveAllBuffersAndClose(context: Context, isEnabled: Boolean = true) {
        for (session in sessions.values) {
            if (!session.isUserDisconnect) {
                if (isEnabled) session.saveBufferToDisk(context) else session.deleteBufferFromDisk(context)
                session.closeSocketOnly()
                session.updateState(false)
            }
        }
        val dir = File(context.filesDir, "terminal_buffers")
        if (!isEnabled && dir.exists()) dir.deleteRecursively()
        updateSessionsStatus()
    }

    fun updateSessionsStatus() {
        _sessionsStatus.value = sessions.mapKeys { cleanKey(it.key) }.mapValues { status(it.value) }
        updateActiveNodes()
    }
    fun updateActiveNodes() { _activeNodes.value = sessions.filterValues { it.isConnected() }.keys.toSet() }
    private fun cleanKey(nodeId: String) = nodeId.removePrefix("node//").removePrefix("node/").trim()
    private fun status(session: TerminalSession): iecxiv.meshfabric.data.model.SessionStatus = when {
        session.isConnected() -> iecxiv.meshfabric.data.model.SessionStatus.CONNECTED
        session.getFullHistory().isNotEmpty() && !session.isUserDisconnect -> iecxiv.meshfabric.data.model.SessionStatus.DISCONNECTED_WITH_BUFFER
        else -> iecxiv.meshfabric.data.model.SessionStatus.NONE
    }

    @Synchronized
    fun getSession(meshClient: MeshCentralClient, nodeId: String): TerminalSession =
        sessions.getOrPut(cleanKey(nodeId)) {
            TerminalSession(cleanKey(nodeId), meshClient, scope).also { it.onStateChanged = { updateSessionsStatus() } }
        }

    fun isConnectedToNode(nodeId: String) = sessions[cleanKey(nodeId)]?.isConnected() ?: false
    fun getConnectionState(nodeId: String): StateFlow<Boolean> =
        sessions[cleanKey(nodeId)]?.connectionState ?: MutableStateFlow(false).asStateFlow()
    fun getConnectionStatus(nodeId: String): StateFlow<TerminalConnectionStatus> =
        sessions[cleanKey(nodeId)]?.connectionStatus ?: MutableStateFlow(TerminalConnectionStatus.CONNECTING).asStateFlow()
    fun getIncomingDataFlow(nodeId: String): SharedFlow<String> =
        sessions[cleanKey(nodeId)]?.incomingDataFlow ?: MutableSharedFlow<String>().asSharedFlow()
    fun getFullHistory(nodeId: String) = sessions[cleanKey(nodeId)]?.getFullHistory() ?: ""
    fun connect(meshClient: MeshCentralClient, nodeId: String) {
        getSession(meshClient, nodeId).connect(true)
        updateSessionsStatus()
    }
    fun restartSession(meshClient: MeshCentralClient, nodeId: String) {
        sessions.remove(cleanKey(nodeId))?.disconnect()
        getSession(meshClient, nodeId).apply {
            isUserDisconnect = false
            clearHistory()
            deleteBufferFromDisk(iecxiv.meshfabric.MeshFabricApp.instance)
            connect(true)
        }
        updateSessionsStatus()
    }
    fun hasActiveSessions() = sessions.values.any { it.isConnected() }
    fun getSessionStatus(nodeId: String) = sessions[cleanKey(nodeId)]?.let { status(it) }
        ?: iecxiv.meshfabric.data.model.SessionStatus.NONE
    fun hasDisconnectedWithBuffer() = sessions.values.any { !it.isConnected() && it.getFullHistory().isNotEmpty() && !it.isUserDisconnect }
    fun getSessionCount() = sessions.values.count { (it.isConnected() || it.getFullHistory().isNotEmpty()) && !it.isUserDisconnect }
    fun onNetworkChanged() { sessions.values.filter { !it.isUserDisconnect }.forEach { it.onNetworkChanged() } }
    fun sendInput(nodeId: String, text: String) { sessions[cleanKey(nodeId)]?.sendInput(text) }
    fun sendControlKey(nodeId: String, keyChar: Char) { sessions[cleanKey(nodeId)]?.sendControlKey(keyChar) }
    fun sendResize(nodeId: String, cols: Int, rows: Int) { sessions[cleanKey(nodeId)]?.sendResize(cols, rows) }
    fun disconnectSession(nodeId: String) { sessions.remove(cleanKey(nodeId))?.disconnect(); updateSessionsStatus() }
    fun disconnectAll() { sessions.values.forEach { it.disconnect() }; sessions.clear(); updateSessionsStatus() }
}
