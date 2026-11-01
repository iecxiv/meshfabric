package iecxiv.meshfabric.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

import iecxiv.meshfabric.R

enum class DesktopEncoding(val code: Int, val label: String) {
    JPEG(1, "JPEG"),
    PNG(2, "PNG"),
    TIFF(4, "TIFF"),
    WEBP(3, "WebP")
}

enum class FrameRateOption(val delayMs: Long, val labelRes: Int) {
    FAST(33L, R.string.fr_fast),
    MEDIUM(100L, R.string.fr_medium),
    SLOW(500L, R.string.fr_slow),
    VERY_SLOW(1000L, R.string.fr_very_slow)
}

data class DesktopSettings(
    val quality: Int = 50,
    val scale: Int = 1024,
    val frameRate: FrameRateOption = FrameRateOption.FAST,
    val encoding: DesktopEncoding = DesktopEncoding.JPEG,
    val swapMouseButtons: Boolean = false,
    val reverseMouseWheel: Boolean = false,
    val useRemoteKeyboardMap: Boolean = false,
    val autoClipboard: Boolean = false,
    val lockOnDisconnect: Boolean = false
)

class DesktopSession(
    val nodeId: String,
    val meshClient: MeshCentralClient,
    private val scope: CoroutineScope
) {
    val cleanNodeId = nodeId.removePrefix("node//").removePrefix("node/").trim()
    val fullNodeId = "node//$cleanNodeId"

    @Volatile var webSocket: WebSocket? = null
    @Volatile var isUserDisconnect = true
    private var visible = false
    private var epoch = 0L
    private var retryAt = 0L
    private var attemptStarted = 0L
    private var failures = 0
    private var recoveryJob: Job? = null
    private var tunnelJob: Job? = null
    private var initializationJob: Job? = null

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _screenResolution = MutableStateFlow(Pair(1024, 768))
    val screenResolution: StateFlow<Pair<Int, Int>> = _screenResolution.asStateFlow()

    private val _desktopBitmap = MutableStateFlow<Bitmap?>(null)
    val desktopBitmap: StateFlow<Bitmap?> = _desktopBitmap.asStateFlow()

    private val _frameVersion = MutableStateFlow(0L)
    val frameVersion: StateFlow<Long> = _frameVersion.asStateFlow()

    private var diagPackets = 0L
    private var diagBytes = 0L
    private var diagImagePackets = 0L
    private var diagJumboPackets = 0L
    private var diagDecoded = 0L
    private var diagDecodeFailures = 0L
    private var diagPublished = 0L
    private var diagLastCmd = -1
    private var diagLastLog = 0L

    private fun logFrameDiagnostics(stage: String, force: Boolean = false) {
        if (!iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) return
        val now = nowMs()
        if (!force && now - diagLastLog < 1000L) return
        diagLastLog = now
        iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "MeshDesktopDiag", "DesktopRelayClient.kt:98 epoch=") { "epoch=$epoch stage=$stage connected=${_connectionState.value} visible=$visible rx=$diagPackets bytes=$diagBytes lastCmd=$diagLastCmd images=$diagImagePackets jumbo=$diagJumboPackets decoded=$diagDecoded decodeFailed=$diagDecodeFailures published=$diagPublished version=${_frameVersion.value}" }
    }

    private val _settings = MutableStateFlow(DesktopSettings())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    private val _remoteClipboardText = MutableStateFlow("")
    val remoteClipboardText: StateFlow<String> = _remoteClipboardText.asStateFlow()

    var onClipboardReceived: ((String) -> Unit)? = null
    var onStateChanged: (() -> Unit)? = null

    private var unpauseJob: Job? = null
    private var canvasBitmap: Bitmap? = null
    private var desktopCanvas: Canvas? = null
    private val paint = Paint().apply {
        isFilterBitmap = true
        isDither = true
    }

    fun isConnected(): Boolean {
        return _connectionState.value && webSocket != null
    }

    private fun updateState(connected: Boolean) {
        _connectionState.value = connected
        onStateChanged?.invoke()
    }

    fun updateSettings(newSettings: DesktopSettings) {
        val oldSettings = _settings.value
        _settings.value = newSettings
        if (oldSettings.quality != newSettings.quality ||
            oldSettings.scale != newSettings.scale ||
            oldSettings.encoding != newSettings.encoding) {
            sendCompressionSettings(
                imageType = newSettings.encoding.code,
                compressionLevel = newSettings.quality,
                scalingLevel = newSettings.scale
            )
            sendUnPause()
        }
    }

    private fun nowMs() = System.nanoTime() / 1_000_000L

    private fun current(socket: WebSocket, generation: Long): Boolean =
        !isUserDisconnect && epoch == generation && webSocket === socket

    private fun startUnpauseLoop() {
        stopUnpauseLoop()
        val socket = webSocket ?: return
        val generation = epoch
        if (!visible) return
        unpauseJob = scope.launch {
            while (true) {
                delay(_settings.value.frameRate.delayMs.coerceAtLeast(100L))
                synchronized(this@DesktopSession) {
                    if (current(socket, generation) && visible && isConnected()) {
                            sendUnPause()
                            logFrameDiagnostics("heartbeat")
                        }
                }
            }
        }
    }

    @Synchronized
    fun stopUnpauseLoop() {
        unpauseJob?.cancel()
        unpauseJob = null
    }

    @Synchronized
    fun pauseSession() {
        visible = false
        stopUnpauseLoop()
    }

    @Synchronized
    fun resumeSession() {
        if (isUserDisconnect) return
        visible = true
        if (isConnected()) {
            refreshDisplay()
            startUnpauseLoop()
        } else {
            connect()
        }
    }

    @Synchronized
    fun connect() {
        isUserDisconnect = false
        visible = true
        onStateChanged?.invoke()
        if (isConnected()) {
            resumeSession()
            return
        }
        forceReconnect()
    }

    @Synchronized
    fun forceReconnect() {
        if (isUserDisconnect) return
        retireSocket()
        failures = 0
        retryAt = 0L
        if (!meshClient.connectionState.value && !meshClient.isUserLogout) meshClient.reconnectNow()
        if (recoveryJob?.isActive != true) {
            recoveryJob = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                while (true) {
                    synchronized(this@DesktopSession) {
                        if (meshClient.isUserLogout) {
                            disconnect()
                            return@launch
                        }
                        if (!isUserDisconnect) {
                            if (!meshClient.connectionState.value) {
                                if (webSocket != null) retireSocket()
                            } else if (webSocket != null && !isConnected() && nowMs() - attemptStarted >= 20_000L) {
                                iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("DesktopSession", "DesktopRelayClient.kt:220 Desktop handshake timed out for")
                                retireSocket()
                            }
                            if (meshClient.connectionState.value && webSocket == null && nowMs() >= retryAt) openSocket()
                        }
                    }
                    delay(1000L)
                }
            }
            recoveryJob?.start()
        }
        if (meshClient.connectionState.value) openSocket()
    }

    private fun retireSocket() {
        logFrameDiagnostics("retire", true)
        epoch++
        tunnelJob?.cancel()
        tunnelJob = null
        initializationJob?.cancel()
        initializationJob = null
        stopUnpauseLoop()
        val old = webSocket
        webSocket = null
        updateState(false)
        old?.cancel()
        retryAt = nowMs() + DesktopRecoveryPolicy.retryDelay(failures++)
    }

    private fun openSocket() {
        if (isUserDisconnect || meshClient.isUserLogout || webSocket != null || !meshClient.connectionState.value) return
        val generation = ++epoch
        attemptStarted = nowMs()
        try {
            val token = meshClient.relayAuthCookie.ifEmpty {
                meshClient.userAuthCookie.ifEmpty { meshClient.extractRawToken(meshClient.authCookie) }
            }
            val tunnelId = java.util.UUID.randomUUID().toString()
            val encodedToken = URLEncoder.encode(token, "UTF-8")
            val encodedNode = URLEncoder.encode(fullNodeId, "UTF-8")
            val url = OkHttpHelper.toWebSocketUrl(meshClient.serverUrl,
                "/meshrelay.ashx?p=2&nodeid=$encodedNode&id=$tunnelId&rauth=$encodedToken&auth=$encodedToken")
            val request = Request.Builder().url(url).apply {
                if (meshClient.authCookie.isNotBlank()) header("Cookie", meshClient.authCookie)
            }.build()
            webSocket = OkHttpHelper.createClient(meshClient.ignoreSsl).newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(this@DesktopSession) {
                        if (!current(webSocket, generation)) { webSocket.cancel(); return }
                        if (!meshClient.connectionState.value) { retireSocket(); return }
                        meshClient.requestTunnel(fullNodeId, 2, tunnelId)
                        tunnelJob = scope.launch {
                            delay(1000L)
                            synchronized(this@DesktopSession) {
                                if (current(webSocket, generation) && !isConnected() && meshClient.connectionState.value) {
                                    meshClient.requestTunnel(fullNodeId, 2, tunnelId)
                                }
                            }
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    synchronized(this@DesktopSession) {
                        if (!current(webSocket, generation)) return
                        if (text == "c" || text == "cr") {
                            if (isConnected()) return
                            tunnelJob?.cancel()
                            tunnelJob = null
                            failures = 0
                            updateState(true)
                            webSocket.send("2")
                            initializationJob = scope.launch {
                                for (wait in listOf(100L, 200L, 400L, 800L)) {
                                    delay(wait)
                                    synchronized(this@DesktopSession) {
                                        if (!current(webSocket, generation)) return@launch
                                        refreshDisplay()
                                        if (wait == 400L) requestRemoteClipboard()
                                    }
                                }
                            }
                            startUnpauseLoop()
                        } else if (text.trim().startsWith("{")) {
                            handleJsonControlMessage(text.trim())
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    synchronized(this@DesktopSession) {
                        if (current(webSocket, generation)) {
                            run { if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagPackets++ }; Unit }
                            if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagBytes += bytes.size }
                            handleBinaryFrame(bytes.toByteArray())
                            logFrameDiagnostics("receive", diagPackets <= 3L)
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(this@DesktopSession) {
                        if (current(webSocket, generation)) retireSocket()
                    }
                    webSocket.cancel()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(this@DesktopSession) {
                        if (current(webSocket, generation)) retireSocket()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    synchronized(this@DesktopSession) {
                        if (current(webSocket, generation)) {
                            iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("DesktopSession", "DesktopRelayClient.kt:336 Desktop transport failed for")
                            retireSocket()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("DesktopSession", "DesktopRelayClient.kt:343 Cannot open desktop transport for")
            retireSocket()
        }
    }

    @Synchronized
    fun refreshOrReconnect() {
        if (isConnected()) refreshDisplay() else {
            isUserDisconnect = false
            forceReconnect()
        }
    }

    fun updateRemoteClipboard(text: String) {
        val cleaned = text.replace("\u0000", "").trim()
        if (cleaned.isNotEmpty()) {
            iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:359 Updated remote clipboard for") { "Updated remote clipboard for $cleanNodeId: '$cleaned'" }
            _remoteClipboardText.value = cleaned
            onClipboardReceived?.invoke(cleaned)
        }
    }

    private fun handleJsonControlMessage(text: String) {
        try {
            val json = com.google.gson.Gson().fromJson(text, com.google.gson.JsonObject::class.java) ?: return
            val type = json.get("type")?.asString ?: json.get("action")?.asString ?: ""
            iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:369 Received JSON control message type:") { "Received JSON control message type: $type for $cleanNodeId" }
            when (type.lowercase()) {
                "ping" -> {
                    val pong = com.google.gson.JsonObject().apply {
                        addProperty("ctrlChannel", "102938")
                        addProperty("type", "pong")
                    }
                    webSocket?.send(pong.toString())
                }
                "metadata", "displays", "desktop", "uiprompt" -> {
                    refreshDisplay()
                }
                "clipboard", "getclip", "setclip", "getclipboard" -> {
                    val clipData = json.get("data")?.asString
                        ?: json.get("text")?.asString
                        ?: json.get("clip")?.asString
                        ?: json.get("value")?.asString
                        ?: ""
                    updateRemoteClipboard(clipData)
                }
            }
        } catch (e: Exception) {
            iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("DesktopSession", "DesktopRelayClient.kt:391 Error handling JSON control message:")
        }
    }

    private fun findImageStartOffset(data: ByteArray): Int {
        val maxOffset = (data.size - 4).coerceAtMost(64)
        for (i in 0..maxOffset) {
            if (data[i] == 0x52.toByte() && data[i + 1] == 0x49.toByte() &&
                data[i + 2] == 0x46.toByte() && data[i + 3] == 0x46.toByte()) {
                return i
            }
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() && data[i + 2] == 0xFF.toByte()) {
                return i
            }
            if (data[i] == 0x89.toByte() && data[i + 1] == 0x50.toByte() &&
                data[i + 2] == 0x4E.toByte() && data[i + 3] == 0x47.toByte()) {
                return i
            }
        }
        return -1
    }

    @Synchronized
    private fun handleBinaryFrame(data: ByteArray) {
        if (data.size < 4) return

        val cmd = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)

        if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagLastCmd = cmd }
        if (cmd == 3) run { if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagImagePackets++ }; Unit }
        if (cmd == 27) run { if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagJumboPackets++ }; Unit }
        when (cmd) {
            7 -> {
                if (data.size < 8) return
                val width = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
                val height = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
                if (width > 0 && height > 0) {
                    val (currentW, currentH) = _screenResolution.value
                    if (currentW != width || currentH != height || canvasBitmap == null) {
                        _screenResolution.value = Pair(width, height)
                        initCanvas(width, height)
                    }
                }
            }
            27 -> {
                if (data.size > 8) {
                    handleBinaryFrame(data.copyOfRange(8, data.size))
                }
            }
            3 -> {
                if (data.size < 8) return
                val x = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
                val y = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)

                var offset = findImageStartOffset(data)
                if (offset < 0) offset = 8

                if (data.size > offset) {
                    val imgData = data.copyOfRange(offset, data.size)
                    try {
                        val tileBitmap = BitmapFactory.decodeByteArray(imgData, 0, imgData.size)
                        if (tileBitmap == null) run { if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagDecodeFailures++ }; Unit } else run { if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagDecoded++ }; Unit }
                        if (tileBitmap != null) {
                            val requiredW = max(x + tileBitmap.width, _screenResolution.value.first)
                            val requiredH = max(y + tileBitmap.height, _screenResolution.value.second)

                            val currentBitmap = canvasBitmap
                            if (currentBitmap == null) {
                                initCanvas(max(requiredW, 1024), max(requiredH, 768))
                            }
                            drawTileOnCanvas(tileBitmap, x, y)
                        }
                    } catch (e: Exception) {
                        run { if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagDecodeFailures++ }; Unit }
                        iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("MeshDesktopDiag", "DesktopRelayClient.kt:465 decode-error type=")
                        iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("DesktopSession", "DesktopRelayClient.kt:466 Error decoding tile at")
                    }
                }
            }
            9 -> {
                if (data.size >= 4) {
                    val pktLen = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
                    val textLen = if (pktLen >= 4) (pktLen - 4).coerceAtMost(data.size - 4) else (data.size - 4)
                    val text = if (textLen > 0) {
                        String(data, 4, textLen, Charsets.UTF_8)
                    } else ""
                    iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:477 Received binary KVM cmd 9 (Clipboard) textLen=") { "Received binary KVM cmd 9 (Clipboard) textLen=$textLen, rawText='$text'" }
                    updateRemoteClipboard(text)
                }
            }
            else -> {
                if (cmd != 82) { // suppress high frequency WebP tile log
                    iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:483 Received binary KVM cmd=") { "Received binary KVM cmd=$cmd, size=${data.size}" }
                }
            }
        }
    }

    @Synchronized
    private fun initCanvas(width: Int, height: Int) {
        try {
            val oldBitmap = canvasBitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.BLACK)

            if (oldBitmap != null && !oldBitmap.isRecycled) {
                canvas.drawBitmap(oldBitmap, 0f, 0f, paint)
            }

            canvasBitmap = bitmap
            desktopCanvas = canvas
            _desktopBitmap.value = bitmap
        } catch (e: Exception) {
            run { iecxiv.meshfabric.diagnostics.AppDiagnostics.fault("DesktopRelayClient", "DesktopRelayClient.kt:505", e); Unit }
        }
    }

    @Synchronized
    private fun drawTileOnCanvas(tile: Bitmap, x: Int, y: Int) {
        val canvas = desktopCanvas
        if (canvas != null) {
            canvas.drawBitmap(tile, x.toFloat(), y.toFloat(), paint)
            _frameVersion.value = System.currentTimeMillis()
            run { if (iecxiv.meshfabric.diagnostics.AppDiagnostics.enabled) { diagPublished++ }; Unit }
        }
    }

    fun sendUnPause() {
        if (!_connectionState.value || webSocket == null) return
        val pkt = byteArrayOf(0x00, 0x06, 0x00, 0x04)
        webSocket?.send(ByteString.of(*pkt))
    }

    fun sendCompressionSettings(imageType: Int, compressionLevel: Int, scalingLevel: Int) {
        if (webSocket == null) return
        val pkt = ByteArray(10)
        pkt[0] = 0x00
        pkt[1] = 0x05
        pkt[2] = 0x00
        pkt[3] = 0x0A
        pkt[4] = (imageType and 0xFF).toByte()
        pkt[5] = (compressionLevel and 0xFF).toByte()
        pkt[6] = ((scalingLevel shr 8) and 0xFF).toByte()
        pkt[7] = (scalingLevel and 0xFF).toByte()
        pkt[8] = 0x00
        pkt[9] = 0x07 // KVM Flags: Bit 1 (0x01) = Clipboard, Bit 2 (0x02) = Audio, Bit 3 (0x04) = Touch
        webSocket?.send(ByteString.of(*pkt))
        iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:539 Sent compression settings with KVM flags 0x07 (Clipboard enabled) for") { "Sent compression settings with KVM flags 0x07 (Clipboard enabled) for $cleanNodeId" }
    }

    fun sendMouseEvent(actionButton: Int, x: Int, y: Int) {
        if (!_connectionState.value || webSocket == null) return
        val packet = ByteArray(10)
        packet[0] = 0x00
        packet[1] = 0x02
        packet[2] = 0x00
        packet[3] = 0x0A
        packet[4] = 0x00
        packet[5] = (actionButton and 0xFF).toByte()
        packet[6] = ((x shr 8) and 0xFF).toByte()
        packet[7] = (x and 0xFF).toByte()
        packet[8] = ((y shr 8) and 0xFF).toByte()
        packet[9] = (y and 0xFF).toByte()
        webSocket?.send(ByteString.of(*packet))
    }

    fun sendMouseScroll(x: Int, y: Int, delta: Int) {
        if (!_connectionState.value || webSocket == null) return
        val packet = ByteArray(12)
        packet[0] = 0x00
        packet[1] = 0x02
        packet[2] = 0x00
        packet[3] = 0x0C
        packet[4] = 0x00
        packet[5] = 0x00
        packet[6] = ((x shr 8) and 0xFF).toByte()
        packet[7] = (x and 0xFF).toByte()
        packet[8] = ((y shr 8) and 0xFF).toByte()
        packet[9] = (y and 0xFF).toByte()
        packet[10] = ((delta shr 8) and 0xFF).toByte()
        packet[11] = (delta and 0xFF).toByte()
        webSocket?.send(ByteString.of(*packet))
    }

    fun sendSingleKey(keyCode: Int) {
        sendKeyEvent(keyCode, isDown = true)
        scope.launch {
            delay(30)
            sendKeyEvent(keyCode, isDown = false)
        }
    }

    fun sendKeyCombo(vararg keyCodes: Int) {
        scope.launch {
            for (code in keyCodes) {
                sendKeyEvent(code, isDown = true)
                delay(20)
            }
            delay(50)
            for (code in keyCodes.reversed()) {
                sendKeyEvent(code, isDown = false)
                delay(20)
            }
        }
    }

    fun sendKeyEvent(keyCode: Int, isDown: Boolean) {
        if (!_connectionState.value || webSocket == null) return
        val pkt = ByteArray(6)
        pkt[0] = 0x00
        pkt[1] = 0x01
        pkt[2] = 0x00
        pkt[3] = 0x06
        pkt[4] = if (isDown) 0x00.toByte() else 0x01.toByte()
        pkt[5] = (keyCode and 0xFF).toByte()
        webSocket?.send(ByteString.of(*pkt))
    }

    fun sendUnicodeText(text: String) {
        if (!_connectionState.value || webSocket == null) return
        scope.launch {
            for (char in text) {
                sendChar(char)
                delay(20)
            }
        }
    }

    fun sendUnicodeKey(char: Char) {
        if (!_connectionState.value || webSocket == null) return
        scope.launch {
            sendChar(char)
        }
    }

    private suspend fun sendChar(char: Char) {
        val code = char.code
        when (char) {
            in 'a'..'z' -> {
                val vk = 0x41 + (char - 'a')
                sendKeyEvent(vk, isDown = true)
                delay(15)
                sendKeyEvent(vk, isDown = false)
            }
            in 'A'..'Z' -> {
                val vk = 0x41 + (char - 'A')
                sendKeyEvent(0x10, isDown = true)
                delay(10)
                sendKeyEvent(vk, isDown = true)
                delay(15)
                sendKeyEvent(vk, isDown = false)
                delay(10)
                sendKeyEvent(0x10, isDown = false)
            }
            in '0'..'9' -> {
                val vk = 0x30 + (char - '0')
                sendKeyEvent(vk, isDown = true)
                delay(15)
                sendKeyEvent(vk, isDown = false)
            }
            ' ' -> {
                sendKeyEvent(0x20, isDown = true)
                delay(15)
                sendKeyEvent(0x20, isDown = false)
            }
            '\n', '\r' -> {
                sendKeyEvent(0x0D, isDown = true)
                delay(15)
                sendKeyEvent(0x0D, isDown = false)
            }
            '\t' -> {
                sendKeyEvent(0x09, isDown = true)
                delay(15)
                sendKeyEvent(0x09, isDown = false)
            }
            '\b' -> {
                sendKeyEvent(0x08, isDown = true)
                delay(15)
                sendKeyEvent(0x08, isDown = false)
            }
            else -> {
                send10ByteUnicodeKey(code)
            }
        }
    }

    private fun send10ByteUnicodeKey(charCode: Int) {
        if (!_connectionState.value || webSocket == null) return

        val pktDown = ByteArray(10)
        pktDown[0] = 0x00
        pktDown[1] = 0x01
        pktDown[2] = 0x00
        pktDown[3] = 0x0A
        pktDown[4] = 0x02.toByte()
        pktDown[5] = 0x00
        pktDown[6] = ((charCode shr 24) and 0xFF).toByte()
        pktDown[7] = ((charCode shr 16) and 0xFF).toByte()
        pktDown[8] = ((charCode shr 8) and 0xFF).toByte()
        pktDown[9] = (charCode and 0xFF).toByte()
        webSocket?.send(ByteString.of(*pktDown))

        val pktUp = ByteArray(10)
        pktUp[0] = 0x00
        pktUp[1] = 0x01
        pktUp[2] = 0x00
        pktUp[3] = 0x0A
        pktUp[4] = 0x03.toByte()
        pktUp[5] = 0x00
        pktUp[6] = ((charCode shr 24) and 0xFF).toByte()
        pktUp[7] = ((charCode shr 16) and 0xFF).toByte()
        pktUp[8] = ((charCode shr 8) and 0xFF).toByte()
        pktUp[9] = (charCode and 0xFF).toByte()
        webSocket?.send(ByteString.of(*pktUp))
    }

    fun sendClipboardText(text: String) {
        iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:709 sendClipboardText called for") { "sendClipboardText called for $cleanNodeId with text '$text'" }
        if (webSocket != null) {
            val textBytes = text.toByteArray(Charsets.UTF_8)
            val len = 4 + textBytes.size
            val pkt = ByteArray(len)
            pkt[0] = 0x00
            pkt[1] = 0x09
            pkt[2] = ((len shr 8) and 0xFF).toByte()
            pkt[3] = (len and 0xFF).toByte()
            System.arraycopy(textBytes, 0, pkt, 4, textBytes.size)
            webSocket?.send(ByteString.of(*pkt))
            iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:720 Sent binary clipboard text (cmd 9) to KVM socket for") { "Sent binary clipboard text (cmd 9) to KVM socket for $cleanNodeId: '$text'" }
        }
        meshClient.sendClipboardText(fullNodeId, text)
    }

    fun requestRemoteClipboard() {
        iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:726 requestRemoteClipboard called for") { "requestRemoteClipboard called for $cleanNodeId, ws connected=${_connectionState.value}, ws=$webSocket" }
        if (webSocket != null) {
            val pkt = byteArrayOf(0x00, 0x09, 0x00, 0x04)
            webSocket?.send(ByteString.of(*pkt))
            iecxiv.meshfabric.diagnostics.AppDiagnostics.trace(3, "DesktopSession", "DesktopRelayClient.kt:730 Sent binary getclipboard (cmd 9) to KVM socket for") { "Sent binary getclipboard (cmd 9) to KVM socket for $cleanNodeId" }
        }
        meshClient.sendClipboardRequest(fullNodeId)
    }

    fun sendLockScreen() {
        sendKeyCombo(0x5B, 0x4C)
    }

    fun sendHotkeyCommand(command: String) {
        when (command) {
            "CTRL_ALT_DEL" -> sendKeyCombo(0x11, 0x12, 0x2E)
            "ALT_TAB" -> sendKeyCombo(0x12, 0x09)
            "WIN_KEY" -> sendSingleKey(0x5B)
            "ESC" -> sendSingleKey(0x1B)
            "LOCK" -> sendLockScreen()
        }
    }

    fun sendDisplaySelect(displayIndex: Int = 0) {
        if (!_connectionState.value || webSocket == null) return
        val packet = ByteArray(6)
        packet[0] = 0x00
        packet[1] = 0x0B
        packet[2] = 0x00
        packet[3] = 0x06
        packet[4] = ((displayIndex shr 8) and 0xFF).toByte()
        packet[5] = (displayIndex and 0xFF).toByte()
        webSocket?.send(ByteString.of(*packet))
    }

    fun refreshDisplay() {
        sendUnPause()
        if (webSocket != null) {
            val pkt = byteArrayOf(0x00, 0x06, 0x00, 0x04)
            webSocket?.send(ByteString.of(*pkt))
        }
    }

    @Synchronized
    fun disconnect() {
        if (_settings.value.lockOnDisconnect && isConnected()) sendLockScreen()
        isUserDisconnect = true
        visible = false
        recoveryJob?.cancel()
        recoveryJob = null
        retireSocket()
        _desktopBitmap.value = null
        canvasBitmap = null
        desktopCanvas = null
    }

}

class DesktopRelayClient {

    companion object {
        var activeInstance: DesktopRelayClient? = null
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val sessions = ConcurrentHashMap<String, DesktopSession>()

    private val _activeNodes = MutableStateFlow<Set<String>>(emptySet())
    val activeNodesFlow: StateFlow<Set<String>> = _activeNodes.asStateFlow()

    private val _sessionsStatus = MutableStateFlow<Map<String, iecxiv.meshfabric.data.model.SessionStatus>>(emptyMap())
    val desktopSessionsStatusFlow: StateFlow<Map<String, iecxiv.meshfabric.data.model.SessionStatus>> = _sessionsStatus.asStateFlow()

    init {
        activeInstance = this
    }

    private fun cleanKey(nodeId: String): String {
        return nodeId.removePrefix("node//").removePrefix("node/").trim()
    }

    fun updateSessionsStatus() {
        val map = sessions.mapKeys { cleanKey(it.key) }.mapValues { entry ->
            val sess = entry.value
            DesktopRecoveryPolicy.status(!sess.isUserDisconnect)
        }
        _sessionsStatus.value = map
        updateActiveNodes()
    }

    fun updateActiveNodes() {
        val active = sessions.entries
            .filter { !it.value.isUserDisconnect }
            .map { it.key }
            .toSet()
        _activeNodes.value = active
    }

    @Synchronized
    fun getSession(meshClient: MeshCentralClient, nodeId: String): DesktopSession {
        val key = cleanKey(nodeId)
        return sessions.getOrPut(key) {
            DesktopSession(nodeId = key, meshClient = meshClient, scope = scope).also { sess ->
                sess.onStateChanged = { updateSessionsStatus() }
            }
        }
    }

    fun isConnectedToNode(nodeId: String): Boolean {
        val session = sessions[cleanKey(nodeId)]
        return session?.isConnected() ?: false
    }

    fun getConnectionState(meshClient: MeshCentralClient, nodeId: String): StateFlow<Boolean> {
        return getSession(meshClient, nodeId).connectionState
    }

    fun getScreenResolution(meshClient: MeshCentralClient, nodeId: String): StateFlow<Pair<Int, Int>> {
        return getSession(meshClient, nodeId).screenResolution
    }

    fun getDesktopBitmap(meshClient: MeshCentralClient, nodeId: String): StateFlow<Bitmap?> {
        return getSession(meshClient, nodeId).desktopBitmap
    }

    fun getFrameVersion(meshClient: MeshCentralClient, nodeId: String): StateFlow<Long> {
        return getSession(meshClient, nodeId).frameVersion
    }

    fun getSettings(meshClient: MeshCentralClient, nodeId: String): StateFlow<DesktopSettings> {
        return getSession(meshClient, nodeId).settings
    }

    fun getRemoteClipboardText(meshClient: MeshCentralClient, nodeId: String): StateFlow<String> {
        return getSession(meshClient, nodeId).remoteClipboardText
    }

    fun connect(meshClient: MeshCentralClient, nodeId: String) {
        val session = getSession(meshClient, nodeId)
        session.connect()
        updateSessionsStatus()
    }

    fun pauseSession(nodeId: String) {
        sessions[cleanKey(nodeId)]?.pauseSession()
    }

    fun resumeSession(nodeId: String) {
        sessions[cleanKey(nodeId)]?.resumeSession()
    }

    fun sendMouseEvent(nodeId: String, actionButton: Int, x: Int, y: Int) {
        sessions[cleanKey(nodeId)]?.sendMouseEvent(actionButton, x, y)
    }

    fun sendMouseScroll(nodeId: String, x: Int, y: Int, delta: Int) {
        sessions[cleanKey(nodeId)]?.sendMouseScroll(x, y, delta)
    }

    fun sendSingleKey(nodeId: String, keyCode: Int) {
        sessions[cleanKey(nodeId)]?.sendSingleKey(keyCode)
    }

    fun sendKeyCombo(nodeId: String, vararg keyCodes: Int) {
        sessions[cleanKey(nodeId)]?.sendKeyCombo(*keyCodes)
    }

    fun sendUnicodeText(nodeId: String, text: String) {
        sessions[cleanKey(nodeId)]?.sendUnicodeText(text)
    }

    fun sendUnicodeKey(nodeId: String, char: Char) {
        sessions[cleanKey(nodeId)]?.sendUnicodeKey(char)
    }

    fun sendClipboardText(meshClient: MeshCentralClient, nodeId: String, text: String) {
        val session = getSession(meshClient, nodeId)
        session.sendClipboardText(text)
    }

    fun requestRemoteClipboard(meshClient: MeshCentralClient, nodeId: String) {
        val session = getSession(meshClient, nodeId)
        session.requestRemoteClipboard()
    }

    fun sendLockScreen(nodeId: String) {
        sessions[cleanKey(nodeId)]?.sendLockScreen()
    }

    fun sendHotkeyCommand(nodeId: String, command: String) {
        sessions[cleanKey(nodeId)]?.sendHotkeyCommand(command)
    }

    fun sendDisplaySelect(nodeId: String, displayIndex: Int = 0) {
        sessions[cleanKey(nodeId)]?.sendDisplaySelect(displayIndex)
    }

    fun updateSettings(nodeId: String, newSettings: DesktopSettings) {
        sessions[cleanKey(nodeId)]?.updateSettings(newSettings)
    }

    fun refreshDisplay(nodeId: String) {
        sessions[cleanKey(nodeId)]?.refreshOrReconnect()
    }

    fun hasActiveSessions(): Boolean {
        return sessions.values.any { !it.isUserDisconnect }
    }

    fun getSessionStatus(nodeId: String): iecxiv.meshfabric.data.model.SessionStatus {
        val session = sessions[cleanKey(nodeId)] ?: return iecxiv.meshfabric.data.model.SessionStatus.NONE
        return DesktopRecoveryPolicy.status(!session.isUserDisconnect)
    }

    fun hasDisconnectedWithBuffer(): Boolean {
        return false // Desktop transport loss does not create a lost buffered session.
    }

    fun getSessionCount(): Int {
        return sessions.values.count { !it.isUserDisconnect }
    }

    fun onNetworkChanged() {
        for (session in sessions.values) {
            if (!session.isUserDisconnect) {
                session.forceReconnect()
            }
        }
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

    fun disconnect() {
        disconnectAll()
    }
}
