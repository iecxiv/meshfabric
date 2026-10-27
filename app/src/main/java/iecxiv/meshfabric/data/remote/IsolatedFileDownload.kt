package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.io.OutputStream
import java.net.URLEncoder
import java.util.UUID

internal class DownloadStallWatch(private val timeoutMs: Long, private val clock: () -> Long) {
    @Volatile private var lastProgress = clock()
    init { require(timeoutMs > 0L) }
    fun touch() { lastProgress = clock() }
    fun expired(): Boolean = clock() - lastProgress >= timeoutMs
}

/** One download, one socket, one attempt. No binary data enters the browser relay. */
internal object IsolatedFileDownload {
    suspend fun download(
        mesh: MeshCentralClient,
        nodeId: String,
        path: String,
        expectedSize: Long?,
        output: OutputStream,
        gate: DownloadNetworkAttempt,
        epoch: Long,
        onProgress: (Long) -> Unit
    ): Long = coroutineScope {
        val completion = CompletableDeferred<Long>()
        val lock = Any()
        val identity = Any()
        val attempt = DownloadAttempt(UUID.randomUUID().toString(), epoch)
        val wireId = attempt.transferId
        val tunnelId = UUID.randomUUID().toString()
        val watch = DownloadStallWatch(20_000L) { System.nanoTime() / 1_000_000L }
        var socket: WebSocket? = null
        var done = false
        var protocolStarted = false
        var streaming = false
        var received = 0L

        fun valid(ws: WebSocket): Boolean = !done && socket === ws && gate.isCurrent(epoch)
        fun fail(error: Throwable) {
            synchronized(lock) {
                if (!done) {
                    done = true
                    completion.completeExceptionally(error)
                    socket?.cancel()
                }
            }
        }
        fun send(ws: WebSocket, sub: String, includePath: Boolean = false) {
            val packet = JsonObject().apply {
                addProperty("action", "download")
                addProperty("sub", sub)
                addProperty("id", wireId)
                if (includePath) addProperty("path", path)
                if (sub == "startack") addProperty("ack", 1)
            }
            if (!ws.send(packet.toString())) throw IOException("WS download command rejected locally")
        }
        fun control(ws: WebSocket, text: String) {
            if (text == "c" || text == "cr") {
                if (protocolStarted) return
                protocolStarted = true
                watch.touch()
                if (!ws.send("5")) throw IOException("Cannot select file protocol")
                send(ws, "start", true)
                return
            }
            if (!text.trimStart().startsWith("{")) return
            val json = JsonParser.parseString(text).asJsonObject
            if (json.get("action")?.asString != "download") return
            if (!DownloadWireProtocol.acceptsControl(wireId, json.get("id")?.asString)) return
            when (json.get("sub")?.asString) {
                "start" -> {
                    if (!streaming) {
                        streaming = true
                        watch.touch()
                        send(ws, "startack")
                    }
                }
                "cancel" -> throw IOException("Agent rejected isolated download")
            }
        }

        val bound = gate.bind(epoch, identity) { fail(IOException("Download network generation changed")) }
        val watchdog = launch {
            while (!completion.isCompleted) {
                delay(250L)
                if (!gate.isCurrent(epoch)) fail(IOException("Download network generation changed"))
                else if (watch.expired()) fail(IOException("WS download made no progress for 20 seconds"))
            }
        }
        try {
            if (!bound) throw IOException("Download attempt already retired")
            if (!mesh.connectionState.value || mesh.isUserLogout) throw IOException("Control socket unavailable")
            val token = mesh.relayAuthCookie.ifEmpty {
                mesh.userAuthCookie.ifEmpty { mesh.extractRawToken(mesh.authCookie) }
            }
            val fullNode = "node//" + nodeId.removePrefix("node//").removePrefix("node/").trim()
            val url = OkHttpHelper.toWebSocketUrl(mesh.serverUrl,
                "/meshrelay.ashx?p=5&nodeid=${URLEncoder.encode(fullNode, "UTF-8")}&id=$tunnelId&rauth=${URLEncoder.encode(token, "UTF-8")}&auth=${URLEncoder.encode(token, "UTF-8")}")
            val request = Request.Builder().url(url).apply {
                if (mesh.authCookie.isNotBlank()) header("Cookie", mesh.authCookie)
            }.build()
            synchronized(lock) {
                if (done || !gate.isCurrent(epoch)) throw IOException("Download attempt retired before socket creation")
                socket = OkHttpHelper.createClient(mesh.ignoreSsl).newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        synchronized(lock) {
                            if (!valid(webSocket)) { webSocket.cancel(); return }
                            if (!mesh.connectionState.value || mesh.isUserLogout) {
                                fail(IOException("Control socket lost before download tunnel")); return
                            }
                            mesh.requestTunnel(fullNode, 5, tunnelId)
                        }
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        synchronized(lock) {
                            if (!valid(webSocket)) return
                            try { control(webSocket, text) } catch (e: Exception) { fail(e) }
                        }
                    }
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        synchronized(lock) {
                            if (!valid(webSocket)) return
                            try {
                                val raw = bytes.toByteArray()
                                if (raw.isNotEmpty() && raw[0] == 1.toByte()) {
                                    if (!streaming) throw IOException("Download data arrived before start acknowledgement")
                                    val block = DownloadWireProtocol.decode(attempt, attempt, raw)
                                        ?: throw IOException("Retired download block")
                                    val accepted = gate.runIfCurrent(epoch) {
                                        if (expectedSize != null && received + block.payload.size > expectedSize) {
                                            throw IOException("WS download exceeded expected file size")
                                        }
                                        output.write(block.payload)
                                        received += block.payload.size
                                        onProgress(received)
                                        watch.touch()
                                        if (block.isLast) {
                                            if (expectedSize != null && received != expectedSize) {
                                                throw IOException("WS download ended with an incomplete file")
                                            }
                                            output.flush()
                                            done = true
                                            completion.complete(received)
                                        } else send(webSocket, "ack")
                                    }
                                    if (!accepted) fail(IOException("Download network generation changed during write"))
                                } else {
                                    control(webSocket, bytes.utf8())
                                }
                            } catch (e: Exception) { fail(e) }
                        }
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        synchronized(lock) { if (valid(webSocket)) fail(IOException("Download relay closed before EOF")) }
                        webSocket.cancel()
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        synchronized(lock) { if (valid(webSocket)) fail(IOException("Download relay closed before EOF")) }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        synchronized(lock) {
                            if (valid(webSocket)) fail(IOException("Download relay failed (${t.javaClass.simpleName})"))
                        }
                    }
                })
            }
            completion.await()
        } finally {
            watchdog.cancel()
            synchronized(lock) {
                done = true
                socket?.cancel()
                socket = null
            }
            gate.release(epoch, identity)
            completion.cancel()
        }
    }
}
