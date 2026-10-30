package iecxiv.meshfabric.data.remote

import android.content.Context
import android.util.Log
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
    val localSessionId = java.util.UUID.randomUUID().toString()
    val openedAt = System.currentTimeMillis()
    @Volatile var disconnectedAt: Long? = null
        private set
    @Volatile private var hasStarted = false
    private val truncated = MutableStateFlow(false)
    val historyTruncated: StateFlow<Boolean> = truncated.asStateFlow()
    val fullNodeId = "node//$cleanNodeId"
    private val generation = SocketGeneration()
    private var handshakeComplete = false
    private var handshakeTimeoutJob: Job? = null

    @Volatile var activeSocket: WebSocket? = null
    @Volatile var isUserDisconnect = false
    private val unexpectedDisconnects = MutableStateFlow(0L)
    val unexpectedDisconnectEvents: StateFlow<Long> = unexpectedDisconnects.asStateFlow()
    var hasPrintedDisconnectMessage = false
    var reconnectJob: Job? = null
    var activeTunnelId = Random.nextInt(100000, 999999).toString()
    var lastCols = 80
    var lastRows = 25

    private val sessionHistory = StringBuilder()
    private val outputJournal = TerminalOutputJournal()
    val outputRevision: StateFlow<Long> = outputJournal.revision
    fun readOutput(cursor: TerminalOutputCursor?): TerminalOutputSlice =
        if (!connectionState.value && cursor == null) outputJournal.readHistory(null) else outputJournal.read(cursor)
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    private val _connectionStatus = MutableStateFlow(TerminalConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<TerminalConnectionStatus> = _connectionStatus.asStateFlow()
    private val _incomingDataFlow = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val incomingDataFlow: SharedFlow<String> = _incomingDataFlow.asSharedFlow()

    fun isConnected(): Boolean = _connectionState.value && activeSocket != null
    fun getFullHistory(): String = synchronized(sessionHistory) { sessionHistory.toString() }
    fun clearHistory() { synchronized(sessionHistory) { sessionHistory.clear(); outputJournal.clear() } }

    private fun getBufferFile(context: Context): File {
        val dir = File(context.filesDir, "terminal_buffers")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "buffer_$cleanNodeId.txt")
    }

    fun saveBufferToDisk(context: Context) {
        try {
            val file = getBufferFile(context)
            synchronized(sessionHistory) {
                if (sessionHistory.isNotEmpty()) file.writeText(sessionHistory.toString())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadBufferFromDisk(context: Context): Boolean {
        return try {
            val file = getBufferFile(context)
            if (file.exists() && file.length() > 0) {
                val content = file.readText()
                synchronized(sessionHistory) {
                    sessionHistory.clear()
                    sessionHistory.append(content)
                    outputJournal.clear()
                    outputJournal.append(content)
                }
                hasStarted = true
                hasPrintedDisconnectMessage = true
                _connectionStatus.value = TerminalConnectionStatus.DISCONNECTED
                true
            } else false
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun deleteBufferFromDisk(context: Context) {
        try {
            val file = getBufferFile(context)
            if (file.exists()) file.delete()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun emitDisconnectBanner() {
        unexpectedDisconnects.value++
        // Status belongs to the UI, never to the terminal stream or interpreter.
        hasPrintedDisconnectMessage = true
        onStateChanged?.invoke()
    }

    fun disconnectKeepingHistory() {
        generation.locked {
            reconnectJob?.cancel()
            reconnectJob = null
            closeSocketOnly()
        }
    }

    private fun resizeForSocket(epoch: Long, socket: WebSocket, cols: Int, rows: Int, force: Boolean = false) {
        scope.launch {
            delay(100)
            generation.runIfCurrent(epoch) {
                if (activeSocket === socket && !isUserDisconnect && _connectionState.value) {
                    sendResize(cols, rows, force)
                }
            }
        }
    }

    fun connect(sendTunnelMsg: Boolean = true) {
        generation.locked {
            val existing = activeSocket
            if (existing != null) {
                if (_connectionState.value) resizeForSocket(generation.current(), existing, lastCols, lastRows)
                return
            }
            if (hasStarted) return
            hasStarted = true
            if (!_connectionState.value && getFullHistory().isNotEmpty() && !isUserDisconnect) {
                _connectionStatus.value = TerminalConnectionStatus.DISCONNECTED
                return
            }
            val serverUrl = meshClient.serverUrl
            val cookie = meshClient.authCookie
            if (serverUrl.isBlank() || cookie.isBlank()) {
                updateState(false)
                return
            }
            val epoch = generation.advance()
            isUserDisconnect = false
            handshakeComplete = false
            handshakeTimeoutJob?.cancel()
            handshakeTimeoutJob = null
            reconnectJob?.cancel()
            reconnectJob = null
            _connectionStatus.value = TerminalConnectionStatus.CONNECTING
            try {
                val client = OkHttpHelper.createClient(meshClient.ignoreSsl)
                val tunnelId = Random.nextInt(100000, 999999).toString()
                activeTunnelId = tunnelId
                val relayToken = meshClient.relayAuthCookie.ifEmpty {
                    meshClient.userAuthCookie.ifEmpty { meshClient.extractRawToken(cookie) }
                }
                val encodedRelayToken = URLEncoder.encode(relayToken, "UTF-8")
                val encodedNodeId = URLEncoder.encode(fullNodeId, "UTF-8")
                val wsUrl = OkHttpHelper.toWebSocketUrl(serverUrl,
                    "/meshrelay.ashx?p=1&nodeid=$encodedNodeId&id=$tunnelId&rauth=$encodedRelayToken&auth=$encodedRelayToken")
                val request = Request.Builder().url(wsUrl).header("Cookie", cookie).build()
                activeSocket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        var accepted = false
                        generation.runIfCurrent(epoch) {
                            if (activeSocket === webSocket && !isUserDisconnect) {
                                accepted = true
                                if (sendTunnelMsg) meshClient.requestTunnel(fullNodeId, 1, tunnelId)
                            }
                        }
                        if (!accepted) webSocket.cancel()
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        generation.runIfCurrent(epoch) {
                            if (activeSocket === webSocket && !isUserDisconnect) handleIncomingData(epoch, webSocket, text)
                        }
                    }
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        generation.runIfCurrent(epoch) {
                            if (activeSocket === webSocket && !isUserDisconnect) {
                                traceTerminalFrame("binary", bytes.size)
                                handleTerminalOutput(bytes.utf8())
                            }
                        }
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        retireSocket(epoch, webSocket)
                        webSocket.close(code, reason)
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        retireSocket(epoch, webSocket)
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (retireSocket(epoch, webSocket)) {
                            Log.e("TerminalSession", "Terminal socket failed (${t.javaClass.simpleName})")
                        }
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

    private fun retireSocket(epoch: Long, socket: WebSocket): Boolean = generation.locked {
        if (generation.current() != epoch || activeSocket !== socket) return@locked false
        val established = handshakeComplete
        activeSocket = null
        handshakeComplete = false
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null
        generation.advance()
        updateState(false)
        if (established && !isUserDisconnect) emitDisconnectBanner()
        true
    }

    private var tracedFrames = 0
    private val traceEnabled: Boolean get() = try {
        (iecxiv.meshfabric.MeshFabricApp.instance.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    } catch (_: Exception) { false }

    private fun traceTerminalFrame(kind: String, size: Int) {
        if (traceEnabled && tracedFrames < 64) {
            tracedFrames++
            Log.d("TerminalEcho", "rx=$tracedFrames kind=$kind size=$size connected=${_connectionState.value} handshake=$handshakeComplete")
        }
    }

    private fun handleIncomingData(epoch: Long, webSocket: WebSocket, data: String) {
        traceTerminalFrame("text", data.length)
        if (TerminalHandshakePolicy.shouldHandle(data, handshakeComplete)) {
            val wasConnected = _connectionState.value
            handshakeComplete = true
            handshakeTimeoutJob?.cancel()
            handshakeTimeoutJob = null
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
                resizeForSocket(epoch, webSocket, if (c >= 5) c else 80, if (r >= 2) r else 25, true)
            }
            return
        }
        handleTerminalOutput(data)
    }

    private fun handleTerminalOutput(data: String) {
        if (_connectionState.value) {
            synchronized(sessionHistory) {
                sessionHistory.append(data)
                outputJournal.append(data)
                if (sessionHistory.length > 200000) {
                    var remove = sessionHistory.length - 200000
                    if (remove < sessionHistory.length && Character.isHighSurrogate(sessionHistory[remove - 1]) &&
                        Character.isLowSurrogate(sessionHistory[remove])) remove++
                    sessionHistory.delete(0, remove)
                    truncated.value = true
                }
            }
            _incomingDataFlow.tryEmit(data)
            if (traceEnabled && tracedFrames in 1..63) {
                Log.d("TerminalEcho", "published chars=${data.length} revision=${outputRevision.value}")
            }
        }
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
            if (!_connectionState.value) return
            activeSocket?.send(ByteString.of(*text.toByteArray(Charsets.UTF_8)))
        }
    }

    fun sendControlKey(keyChar: Char) {
        generation.locked {
            if (!_connectionState.value) return
            val code = keyChar.uppercaseChar().code - 64
            if (code in 1..26) activeSocket?.send(ByteString.of(*byteArrayOf(code.toByte())))
        }
    }

    fun sendResize(cols: Int, rows: Int, force: Boolean = false) {
        generation.locked {
            if (cols < 5 || rows < 2) return
            if (!force && cols == lastCols && rows == lastRows) return
            lastCols = cols
            lastRows = rows
            if (_connectionState.value && activeSocket != null) {
                activeSocket?.send("{\"action\":\"termsize\",\"cols\":$cols,\"rows\":$rows}")
                activeSocket?.send("{\"type\":\"termsize\",\"cols\":$cols,\"rows\":$rows}")
                activeSocket?.send("{\"action\":\"resize\",\"cols\":$cols,\"rows\":$rows}")
            }
        }
    }

    fun closeSocketOnly() {
        generation.locked {
            generation.advance()
            handshakeTimeoutJob?.cancel()
            handshakeTimeoutJob = null
            handshakeComplete = false
            val socket = activeSocket
            activeSocket = null
            updateState(false)
            socket?.close(1000, "Terminal session closed")
        }
    }

    var onStateChanged: (() -> Unit)? = null
    fun updateState(connected: Boolean) {
        if (!connected && disconnectedAt == null) disconnectedAt = System.currentTimeMillis()
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

    @Synchronized
    fun promoteSession(expected: TerminalSession, candidate: TerminalSession): Boolean {
        val key = expected.cleanNodeId
        if (sessions[key] !== expected || expected.isConnected() || !candidate.isConnected() ||
            candidate.cleanNodeId != key) return false
        candidate.onStateChanged = { updateSessionsStatus() }
        sessions[key] = candidate
        updateSessionsStatus()
        return true
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
