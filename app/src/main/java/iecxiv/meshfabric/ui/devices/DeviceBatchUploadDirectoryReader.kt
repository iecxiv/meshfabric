package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.model.identifyDevicePlatform
import iecxiv.meshfabric.data.remote.FileBrowserTransport
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import iecxiv.meshfabric.data.remote.UploadWire
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

/** Read-only preflight. No mkdir, upload, rename, delete or reconnection. */
internal object DeviceBatchUploadDirectoryReader {
    suspend fun read(source: MeshCentralClient, node: MeshNode, meta: GeneralMeta,
        options: DeviceBatchUploadOptions): DeviceBatchUploadListing = coroutineScope {
        val identity = deviceBatchPowerIdentity(source)
        val platform = identifyDevicePlatform(null, meta.os)
        val target = requireNotNull(deviceBatchUploadRoute(options, platform))
        require(deviceBatchAgentUpdateIdentity(identity.actor, node.id) && meta.mesh == node.meshid)
        require(deviceBatchUploadAvailable(node.id, meta, source.groupedDevices.value))
        // Windows drive roots need a separate drive-existence read before support is enabled.
        require(!Regex("^[A-Z]:/$").matches(target))
        val completion = CompletableDeferred<DeviceBatchUploadListing>()
        val lock = Any()
        var socket: WebSocket? = null
        var done = false
        var protocolStarted = false
        var readingParent = target != "/"
        var currentPath = if (readingParent) UploadWire.parent(target) else target
        var requestId = ""
        val tunnel = UUID.randomUUID().toString()
        fun same() = deviceBatchPowerSame(source, identity) && deviceBatchUploadAvailable(node.id, meta, source.groupedDevices.value)
        fun fail(error: Throwable) = synchronized(lock) {
            if (!done) { done = true; completion.completeExceptionally(error); socket?.cancel() }
        }
        fun issue(ws: WebSocket) {
            if (!same()) throw IOException("Upload preflight identity changed")
            requestId = UUID.randomUUID().toString()
            val packet = deviceBatchUploadListPacket(currentPath, platform, requestId)
            if (!ws.send(FileBrowserTransport.listingPayload(packet.toString()))) throw IOException("Directory read not queued")
        }
        fun accept(ws: WebSocket, text: String) {
            synchronized(lock) {
                if (done || socket !== ws) return
                try {
                    if (!same()) throw IOException("Upload preflight identity changed")
                    if (text.length > 2000000) throw IOException("Directory reply too large")
                    if (text == "c" || text == "cr") {
                        if (!protocolStarted) {
                            protocolStarted = true
                            if (!ws.send("5")) throw IOException("File protocol unavailable")
                            issue(ws)
                        }
                        return
                    }
                    if (!protocolStarted || !text.trimStart().startsWith("{")) return
                    val packet = JsonParser.parseString(text).asJsonObject
                    val id = packet.get("reqid")
                    if (id == null || !id.isJsonPrimitive || !id.asJsonPrimitive.isString || id.asString != requestId) return
                    val listing = deviceBatchUploadDecodeListing(packet, node.id, currentPath, platform, requestId)
                        ?: throw IOException("Directory reply could not be verified")
                    if (readingParent) {
                        when (deviceBatchUploadDirectoryEvidence(listing, target, platform)) {
                            DeviceBatchUploadDirectoryState.MISSING -> {
                                done = true
                                completion.complete(DeviceBatchUploadListing(node.id, target, listing.requestId, emptyList(), true,
                                    DeviceBatchUploadDirectoryState.MISSING))
                            }
                            DeviceBatchUploadDirectoryState.EXISTS -> {
                                readingParent = false; currentPath = target; issue(ws)
                            }
                            DeviceBatchUploadDirectoryState.UNKNOWN -> throw IOException("Destination directory type is unknown")
                        }
                    } else {
                        done = true
                        completion.complete(listing)
                    }
                } catch (failure: Exception) { fail(failure) }
            }
        }
        val guard = launch {
            while (!completion.isCompleted) {
                delay(100L)
                if (!same()) fail(IOException("Upload preflight identity changed"))
            }
        }
        val timeout = launch { delay(25000L); fail(IOException("Directory preflight timed out")) }
        try {
            if (!same() || identity.cookie.isBlank()) throw IOException("Control unavailable")
            val token = source.relayAuthCookie.ifEmpty { source.userAuthCookie.ifEmpty { source.extractRawToken(identity.cookie) } }
            val url = OkHttpHelper.toWebSocketUrl(identity.url, FileBrowserTransport.endpoint(
                URLEncoder.encode(node.id, "UTF-8"), tunnel, URLEncoder.encode(token, "UTF-8")))
            synchronized(lock) {
                if (done || !same()) throw IOException("Directory read retired")
                socket = OkHttpHelper.createClient(identity.tls).newBuilder().followRedirects(false).followSslRedirects(false).build()
                    .newWebSocket(Request.Builder().url(url).header("Cookie", identity.cookie).build(), object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            synchronized(lock) {
                                if (done || socket !== webSocket || !same()) { webSocket.cancel(); fail(IOException("Directory read retired")); return }
                                source.requestTunnel(node.id, 5, tunnel)
                            }
                        }
                        override fun onMessage(webSocket: WebSocket, text: String) { accept(webSocket, text) }
                        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                            if (bytes.size > 2000000) fail(IOException("Directory reply too large")) else accept(webSocket, bytes.utf8())
                        }
                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { fail(IOException("Directory relay failed")) }
                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { fail(IOException("Directory relay closed")); webSocket.cancel() }
                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { fail(IOException("Directory relay closed")) }
                    })
            }
            completion.await()
        } finally {
            guard.cancel(); timeout.cancel()
            synchronized(lock) { done = true; socket?.cancel(); socket = null }
            completion.cancel()
        }
    }
}
