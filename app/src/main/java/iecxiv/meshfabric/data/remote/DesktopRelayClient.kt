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
                    if (current(socket, generation) && visible && isConnected()) sendUnPause()
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
                                Log.w("DesktopSession", "Desktop handshake timed out for $cleanNodeId")
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
                override fun onOpen(socket: WebSocket, response: Response) {
                    synchronized(this@DesktopSession) {
                        if (!current(socket, generation)) { socket.cancel(); return }
                        if (!meshClient.connectionState.value) { retireSocket(); return }
                        meshClient.requestTunnel(fullNodeId, 2, tunnelId)
                        tunnelJob = scope.launch {
                            delay(1000L)
                            synchronized(this@DesktopSession) {
                                if (current(socket, generation) && !isConnected() && meshClient.connectionState.value) {
                                    meshClient.requestTunnel(fullNodeId, 2, tunnelId)
                                }
                            }
                        }
                    }
                }

                override fun onMessage(socket: WebSocket, text: String) {
                    synchronized(this@DesktopSession) {
                        if (!current(socket, generation)) return
                        if (text == "c" || text == "cr") {
                            if (isConnected()) return
                            tunnelJob?.cancel()
                            tunnelJob = null
                            failures = 0
                            updateState(true)
                            socket.send("2")
                            initializationJob = scope.launch {
                                for (wait in listOf(100L, 200L, 400L, 800L)) {
                                    delay(wait)
                                    synchronized(this@DesktopSession) {
                                        if (!current(socket, generation)) return@launch
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

                override fun onMessage(socket: WebSocket, bytes: ByteString) {
                    synchronized(this@DesktopSession) {
                        if (current(socket, generation)) handleBinaryFrame(bytes.toByteArray())
                    }
                }

                override fun onClosing(socket: WebSocket, code: Int, reason: String) {
                    synchronized(this@DesktopSession) {
                        if (current(socket, generation)) retireSocket()
                    }
                    socket.cancel()
                }

                override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                    synchronized(this@DesktopSession) {
                        if (current(socket, generation)) retireSocket()
                    }
                }

                override fun onFailure(socket: WebSocket, t: Throwable, response: Response?) {
                    synchronized(this@DesktopSession) {
                        if (current(socket, generation)) {
                            Log.w("DesktopSession", "Desktop transport failed for $cleanNodeId (${t.javaClass.simpleName})")
                            retireSocket()
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.w("DesktopSession", "Cannot open desktop transport for $cleanNodeId (${e.javaClass.simpleName})")
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
        val cleaned = text.replace("\\u0000", "").trim()
        if (cleaned.isNotEmpty()) {
            Log.d("DesktopSession", "Updated remote clipboard for $cleanNodeId: '$cleaned'")
            _remoteClipboardText.value = cleaned
            onClipboardReceived?.invoke(cleaned)
        }
    }

    private fun handleJsonControlMessage(text: String) {
        try {
            val json = com.google.gson.Gson().fromJson(text, com.google.gson.JsonObject::class.java) ?: return
            val type = json.get("type")?.asString ?: json.get("action")?.asString ?: ""
            Log.d("DesktopSession", "Received JSON control message type: $type for $cleanNodeId")
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
            Log.e("DesktopSession", "Error handling JSON control message: ${e.message}")
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
                        Log.e("DesktopSession", "Error decoding tile at $x, $y: ${e.message}")
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
                    Log.d("DesktopSession", "Received binary KVM cmd 9 (Clipboard) textLen=$textLen, rawText='$text'")
                    updateRemoteClipboard(text)
                }
            }
            else -> {
                if (cmd != 82) { // suppress high frequency WebP tile log
                    Log.d("DesktopSession", "Received binary KVM cmd=$cmd, size=${data.size}")
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
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun drawTileOnCanvas(tile: Bitmap, x: Int, y: Int) {
        val canvas = desktopCanvas
        if (canvas != null) {
            canvas.drawBitmap(tile, x.toFloat(), y.toFloat(), paint)
            _frameVersion.value = System.currentTimeMillis()
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
        Log.d("DesktopSession", "Sent compression settings with KVM flags 0x07 (Clipboard enabled) for $cleanNodeId")
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
        webSocket?.send(ByteString.of(*packet))\n    }\n\n    fun sendMouseScroll(x: Int, y: Int, delta: Int) {\n        if (!_connectionState.value || webSocket == null) return\n        val packet = ByteArray(12)\n        packet[0] = 0x00\n        packet[1] = 0x02\n        packet[2] = 0x00\n        packet[3] = 0x0C\n        packet[4] = 0x00\n        packet[5] = 0x00\n        packet[6] = ((x shr 8) and 0xFF).toByte()\n        packet[7] = (x and 0xFF).toByte()\n        packet[8] = ((y shr 8) and 0xFF).toByte()\n        packet[9] = (y and 0xFF).toByte()\n        packet[10] = ((delta shr 8) and 0xFF).toByte()\n        packet[11] = (delta and 0xFF).toByte()\n        webSocket?.send(ByteString.of(*packet))\n    }\n\n    fun sendSingleKey(keyCode: Int) {\n        sendKeyEvent(keyCode, isDown = true)\n        scope.launch {\n            delay(30)\n            sendKeyEvent(keyCode, isDown = false)\n        }\n    }\n\n    fun sendKeyCombo(vararg keyCodes: Int) {\n        scope.launch {\n            for (code in keyCodes) {\n                sendKeyEvent(code, isDown = true)\n                delay(20)\n            }\n            delay(50)\n            for (code in keyCodes.reversed()) {\n                sendKeyEvent(code, isDown = false)\n                delay(20)\n            }\n        }\n    }\n\n    fun sendKeyEvent(keyCode: Int, isDown: Boolean) {\n        if (!_connectionState.value || webSocket == null) return\n        val pkt = ByteArray(6)\n        pkt[0] = 0x00\n        pkt[1] = 0x01\n        pkt[2] = 0x00\n        pkt[3] = 0x06\n        pkt[4] = if (isDown) 0x00.toByte() else 0x01.toByte()\n        pkt[5] = (keyCode and 0xFF).toByte()\n        webSocket?.send(ByteString.of(*pkt))\n    }\n\n    fun sendUnicodeText(text: String) {\n        if (!_connectionState.value || webSocket == null) return\n        scope.launch {\n            for (char in text) {\n                sendChar(char)\n                delay(20)\n            }\n        }\n    }\n\n    fun sendUnicodeKey(char: Char) {\n        if (!_connectionState.value || webSocket == null) return\n        scope.launch {\n            sendChar(char)\n        }\n    }\n\n    private suspend fun sendChar(char: Char) {\n        val code = char.code\n        when (char) {\n            in 'a'..'z' -> {\n                val vk = 0x41 + (char - 'a')\n                sendKeyEvent(vk, isDown = true)\n                delay(15)\n                sendKeyEvent(vk, isDown = false)\n            }\n            in 'A'..'Z' -> {\n                val vk = 0x41 + (char - 'A')\n                sendKeyEvent(0x10, isDown = true)\n                delay(10)\n                sendKeyEvent(vk, isDown = true)\n                delay(15)\n                sendKeyEvent(vk, isDown = false)\n                delay(10)\n                sendKeyEvent(0x10, isDown = false)\n            }\n            in '0'..'9' -> {\n                val vk = 0x30 + (char - '0')\n                sendKeyEvent(vk, isDown = true)\n                delay(15)\n                sendKeyEvent(vk, isDown = false)\n            }\n            ' ' -> {\n                sendKeyEvent(0x20, isDown = true)\n                delay(15)\n                sendKeyEvent(0x20, isDown = false)\n            }\n            '\\n', '\\r' -> {\n                sendKeyEvent(0x0D, isDown = true)\n                delay(15)\n                sendKeyEvent(0x0D, isDown = false)\n            }\n            '\\t' -> {\n                sendKeyEvent(0x09, isDown = true)\n                delay(15)\n                sendKeyEvent(0x09, isDown = false)\n            }\n            '\\b' -> {\n                sendKeyEvent(0x08, isDown = true)\n                delay(15)\n                sendKeyEvent(0x08, isDown = false)\n            }\n            else -> {\n                send10ByteUnicodeKey(code)\n            }\n        }\n    }\n\n    private fun send10ByteUnicodeKey(charCode: Int) {\n        if (!_connectionState.value || webSocket == null) return\n\n        val pktDown = ByteArray(10)\n        pktDown[0] = 0x00\n        pktDown[1] = 0x01\n        pktDown[2] = 0x00\n        pktDown[3] = 0x0A\n        pktDown[4] = 0x02.toByte()\n        pktDown[5] = 0x00\n        pktDown[6] = ((charCode shr 24) and 0xFF).toByte()\n        pktDown[7] = ((charCode shr 16) and 0xFF).toByte()\n        pktDown[8] = ((charCode shr 8) and 0xFF).toByte()\n        pktDown[9] = (charCode and 0xFF).toByte()\n        webSocket?.send(ByteString.of(*pktDown))\n\n        val pktUp = ByteArray(10)\n        pktUp[0] = 0x00\n        pktUp[1] = 0x01\n        pktUp[2] = 0x00\n        pktUp[3] = 0x0A\n        pktUp[4] = 0x03.toByte()\n        pktUp[5] = 0x00\n        pktUp[6] = ((charCode shr 24) and 0xFF).toByte()\n        pktUp[7] = ((charCode shr 16) and 0xFF).toByte()\n        pktUp[8] = ((charCode shr 8) and 0xFF).toByte()\n        pktUp[9] = (charCode and 0xFF).toByte()\n        webSocket?.send(ByteString.of(*pktUp))\n    }\n\n    fun sendClipboardText(text: String) {\n        Log.d(\"DesktopSession\", \"sendClipboardText called for $cleanNodeId with text '$text'\")\n        if (webSocket != null) {\n            val textBytes = text.toByteArray(Charsets.UTF_8)\n            val len = 4 + textBytes.size\n            val pkt = ByteArray(len)\n            pkt[0] = 0x00\n            pkt[1] = 0x09\n            pkt[2] = ((len shr 8) and 0xFF).toByte()\n            pkt[3] = (len and 0xFF).toByte()\n            System.arraycopy(textBytes, 0, pkt, 4, textBytes.size)\n            webSocket?.send(ByteString.of(*pkt))\n            Log.d(\"DesktopSession\", \"Sent binary clipboard text (cmd 9) to KVM socket for $cleanNodeId: '$text'\")\n        }\n        meshClient.sendClipboardText(fullNodeId, text)\n    }\n\n    fun requestRemoteClipboard() {\n        Log.d(\"DesktopSession\", \"requestRemoteClipboard called for $cleanNodeId, ws connected=${_connectionState.value}, ws=$webSocket\")\n        if (webSocket != null) {\n            val pkt = byteArrayOf(0x00, 0x09, 0x00, 0x04)\n            webSocket?.send(ByteString.of(*pkt))\n            Log.d(\"DesktopSession\", \"Sent binary getclipboard (cmd 9) to KVM socket for $cleanNodeId\")\n        }\n        meshClient.sendClipboardRequest(fullNodeId)\n    }\n\n    fun sendLockScreen() {\n        sendKeyCombo(0x5B, 0x4C)\n    }\n\n    fun sendHotkeyCommand(command: String) {\n        when (command) {\n            \"CTRL_ALT_DEL\" -> sendKeyCombo(0x11, 0x12, 0x2E)\n            \"ALT_TAB\" -> sendKeyCombo(0x12, 0x09)\n            \"WIN_KEY\" -> sendSingleKey(0x5B)\n            \"ESC\" -> sendSingleKey(0x1B)\n            \"LOCK\" -> sendLockScreen()\n        }\n    }\n\n    fun sendDisplaySelect(displayIndex: Int = 0) {\n        if (!_connectionState.value || webSocket == null) return\n        val packet = ByteArray(6)\n        packet[0] = 0x00\n        packet[1] = 0x0B\n        packet[2] = 0x00\n        packet[3] = 0x06\n        packet[4] = ((displayIndex shr 8) and 0xFF).toByte()\n        packet[5] = (displayIndex and 0xFF).toByte()\n        webSocket?.send(ByteString.of(*packet))\n    }\n\n    fun refreshDisplay() {\n        sendUnPause()\n        if (webSocket != null) {\n            val pkt = byteArrayOf(0x00, 0x06, 0x00, 0x04)\n            webSocket?.send(ByteString.of(*pkt))\n        }\n    }\n\n    @Synchronized\n    fun disconnect() {\n        if (_settings.value.lockOnDisconnect && isConnected()) sendLockScreen()\n        isUserDisconnect = true\n        visible = false\n        recoveryJob?.cancel()\n        recoveryJob = null\n        retireSocket()\n        _desktopBitmap.value = null\n        canvasBitmap = null\n        desktopCanvas = null\n    }\n\n}\n\nclass DesktopRelayClient {\n\n    companion object {\n        var activeInstance: DesktopRelayClient? = null\n    }\n\n    private val scope = CoroutineScope(Dispatchers.IO + Job())\n    private val sessions = ConcurrentHashMap<String, DesktopSession>()\n\n    private val _activeNodes = MutableStateFlow<Set<String>>(emptySet())\n    val activeNodesFlow: StateFlow<Set<String>> = _activeNodes.asStateFlow()\n\n    private val _sessionsStatus = MutableStateFlow<Map<String, iecxiv.meshfabric.data.model.SessionStatus>>(emptyMap())\n    val desktopSessionsStatusFlow: StateFlow<Map<String, iecxiv.meshfabric.data.model.SessionStatus>> = _sessionsStatus.asStateFlow()\n\n    init {\n        activeInstance = this\n    }\n\n    private fun cleanKey(nodeId: String): String {\n        return nodeId.removePrefix(\"node//\").removePrefix(\"node/\").trim()\n    }\n\n    fun updateSessionsStatus() {\n        val map = sessions.mapKeys { cleanKey(it.key) }.mapValues { entry ->\n            val sess = entry.value\n            DesktopRecoveryPolicy.status(!sess.isUserDisconnect)\n        }\n        _sessionsStatus.value = map\n        updateActiveNodes()\n    }\n\n    fun updateActiveNodes() {\n        val active = sessions.entries\n            .filter { !it.value.isUserDisconnect }\n            .map { it.key }\n            .toSet()\n        _activeNodes.value = active\n    }\n\n    @Synchronized\n    fun getSession(meshClient: MeshCentralClient, nodeId: String): DesktopSession {\n        val key = cleanKey(nodeId)\n        return sessions.getOrPut(key) {\n            DesktopSession(nodeId = key, meshClient = meshClient, scope = scope).also { sess ->\n                sess.onStateChanged = { updateSessionsStatus() }\n            }\n        }\n    }\n\n    fun isConnectedToNode(nodeId: String): Boolean {\n        val session = sessions[cleanKey(nodeId)]\n        return session?.isConnected() ?: false\n    }\n\n    fun getConnectionState(meshClient: MeshCentralClient, nodeId: String): StateFlow<Boolean> {\n        return getSession(meshClient, nodeId).connectionState\n    }\n\n    fun getScreenResolution(meshClient: MeshCentralClient, nodeId: String): StateFlow<Pair<Int, Int>> {\n        return getSession(meshClient, nodeId).screenResolution\n    }\n\n    fun getDesktopBitmap(meshClient: MeshCentralClient, nodeId: String): StateFlow<Bitmap?> {\n        return getSession(meshClient, nodeId).desktopBitmap\n    }\n\n    fun getFrameVersion(meshClient: MeshCentralClient, nodeId: String): StateFlow<Long> {\n        return getSession(meshClient, nodeId).frameVersion\n    }\n\n    fun getSettings(meshClient: MeshCentralClient, nodeId: String): StateFlow<DesktopSettings> {\n        return getSession(meshClient, nodeId).settings\n    }\n\n    fun getRemoteClipboardText(meshClient: MeshCentralClient, nodeId: String): StateFlow<String> {\n        return getSession(meshClient, nodeId).remoteClipboardText\n    }\n\n    fun connect(meshClient: MeshCentralClient, nodeId: String) {\n        val session = getSession(meshClient, nodeId)\n        session.connect()\n        updateSessionsStatus()\n    }\n\n    fun pauseSession(nodeId: String) {\n        sessions[cleanKey(nodeId)]?.pauseSession()\n    }\n\n    fun resumeSession(nodeId: String) {\n        sessions[cleanKey(nodeId)]?.resumeSession()\n    }\n\n    fun sendMouseEvent(nodeId: String, actionButton: Int, x: Int, y: Int) {\n        sessions[cleanKey(nodeId)]?.sendMouseEvent(actionButton, x, y)\n    }\n\n    fun sendMouseScroll(nodeId: String, x: Int, y: Int, delta: Int) {\n        sessions[cleanKey(nodeId)]?.sendMouseScroll(x, y, delta)\n    }\n\n    fun sendSingleKey(nodeId: String, keyCode: Int) {\n        sessions[cleanKey(nodeId)]?.sendSingleKey(keyCode)\n    }\n\n    fun sendKeyCombo(nodeId: String, vararg keyCodes: Int) {\n        sessions[cleanKey(nodeId)]?.sendKeyCombo(*keyCodes)\n    }\n\n    fun sendUnicodeText(nodeId: String, text: String) {\n        sessions[cleanKey(nodeId)]?.sendUnicodeText(text)\n    }\n\n    fun sendUnicodeKey(nodeId: String, char: Char) {\n        sessions[cleanKey(nodeId)]?.sendUnicodeKey(char)\n    }\n\n    fun sendClipboardText(meshClient: MeshCentralClient, nodeId: String, text: String) {\n        val session = getSession(meshClient, nodeId)\n        session.sendClipboardText(text)\n    }\n\n    fun requestRemoteClipboard(meshClient: MeshCentralClient, nodeId: String) {\n        val session = getSession(meshClient, nodeId)\n        session.requestRemoteClipboard()\n    }\n\n    fun sendLockScreen(nodeId: String) {\n        sessions[cleanKey(nodeId)]?.sendLockScreen()\n    }\n\n    fun sendHotkeyCommand(nodeId: String, command: String) {\n        sessions[cleanKey(nodeId)]?.sendHotkeyCommand(command)\n    }\n\n    fun sendDisplaySelect(nodeId: String, displayIndex: Int = 0) {\n        sessions[cleanKey(nodeId)]?.sendDisplaySelect(displayIndex)\n    }\n\n    fun updateSettings(nodeId: String, newSettings: DesktopSettings) {\n        sessions[cleanKey(nodeId)]?.updateSettings(newSettings)\n    }\n\n    fun refreshDisplay(nodeId: String) {\n        sessions[cleanKey(nodeId)]?.refreshOrReconnect()\n    }\n\n    fun hasActiveSessions(): Boolean {\n        return sessions.values.any { !it.isUserDisconnect }\n    }\n\n    fun getSessionStatus(nodeId: String): iecxiv.meshfabric.data.model.SessionStatus {\n        val session = sessions[cleanKey(nodeId)] ?: return iecxiv.meshfabric.data.model.SessionStatus.NONE\n        return DesktopRecoveryPolicy.status(!session.isUserDisconnect)\n    }\n\n    fun hasDisconnectedWithBuffer(): Boolean {\n        return false // Desktop transport loss does not create a lost buffered session.\n    }\n\n    fun getSessionCount(): Int {\n        return sessions.values.count { !it.isUserDisconnect }\n    }\n\n    fun onNetworkChanged() {\n        for (session in sessions.values) {\n            if (!session.isUserDisconnect) {\n                session.forceReconnect()\n            }\n        }\n    }\n\n    fun disconnectSession(nodeId: String) {\n        val key = cleanKey(nodeId)\n        val session = sessions.remove(key)\n        session?.disconnect()\n        updateSessionsStatus()\n    }\n\n    fun disconnectAll() {\n        for (session in sessions.values) {\n            session.disconnect()\n        }\n        sessions.clear()\n        updateSessionsStatus()\n    }\n\n    fun disconnect() {\n        disconnectAll()\n    }\n}\n