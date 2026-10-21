package iecxiv.meshfabric.data.remote

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
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class TerminalSession(
    val nodeId: String,
    val meshClient: MeshCentralClient,
    private val scope: CoroutineScope
) {
    val cleanNodeId = nodeId.removePrefix("node//").removePrefix("node/")
    val fullNodeId = "node//$cleanNodeId"

    var activeSocket: WebSocket? = null
    var isUserDisconnect = false
    var reconnectJob: Job? = null
    var activeTunnelId = Random.nextInt(100000, 999999).toString()

    var lastCols = 80
    var lastRows = 25

    private val sessionHistory = StringBuilder()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

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

    fun connect(sendTunnelMsg: Boolean = true) {
        if (_connectionState.value && activeSocket != null) {
            Log.d("TerminalSession", "Session for $cleanNodeId ALREADY connected. Skipping connect.")
            scope.launch {
                delay(100)
                sendResize(lastCols, lastRows)
            }
            return
        }

        isUserDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = null

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
                if (activeSocket == webSocket) {
                    _connectionState.value = false
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (activeSocket == webSocket) {
                    _connectionState.value = false
                    activeSocket = null
                    if (!isUserDisconnect && !_connectionState.value) {
                        scheduleReconnect()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("TerminalSession", "WS onFailure ($cleanNodeId): ${t.localizedMessage}", t)
                if (activeSocket == webSocket) {
                    _connectionState.value = false
                    activeSocket = null
                    if (!isUserDisconnect && !_connectionState.value) {
                        scheduleReconnect()
                    }
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (_connectionState.value) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(1500)
            if (!isUserDisconnect && !_connectionState.value) {
                connect(sendTunnelMsg = false)
            }
        }
    }

    private fun handleIncomingData(webSocket: WebSocket, data: String) {
        if (!_connectionState.value && (data == "c" || data == "cr")) {
            Log.d("TerminalSession", "Handshake received ($cleanNodeId): $data. Sending protocol 1")
            updateState(true)
            reconnectJob?.cancel()
            reconnectJob = null
            webSocket.send("1")

            val c = lastCols
            val r = lastRows
            lastCols = 0
            lastRows = 0

            scope.launch {
                delay(100)
                sendResize(if (c >= 5) c else 80, if (r >= 2) r else 25, force = true)
            }
        } else if (_connectionState.value) {
            synchronized(sessionHistory) {
                sessionHistory.append(data)
                if (sessionHistory.length > 200000) {
                    sessionHistory.delete(0, 50000)
                }
            }
            _incomingDataFlow.tryEmit(data)
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

    private fun closeSocketOnly() {
        activeSocket?.close(1000, "Reconnecting terminal session")
        activeSocket = null
    }

    var onStateChanged: (() -> Unit)? = null

    fun updateState(connected: Boolean) {
        _connectionState.value = connected
        onStateChanged?.invoke()
    }

    fun disconnect() {
        isUserDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        activeSocket?.close(1000, "Disconnecting terminal session")
        activeSocket = null
        updateState(false)
        clearHistory()
    }
}

class TerminalRelayClient {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val sessions = ConcurrentHashMap<String, TerminalSession>()

    private val _activeNodes = MutableStateFlow<Set<String>>(emptySet())
    val activeNodesFlow: StateFlow<Set<String>> = _activeNodes.asStateFlow()

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
                sess.onStateChanged = { updateActiveNodes() }
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
        updateActiveNodes()
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
        updateActiveNodes()
    }

    fun disconnectAll() {
        for (session in sessions.values) {
            session.disconnect()
        }
        sessions.clear()
        updateActiveNodes()
    }
}