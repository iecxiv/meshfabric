package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

internal class UploadRejected(message: String) : IOException(message)
internal class UploadConflict(message: String) : IOException(message)

internal object UploadWire {
    fun encode(data: ByteArray): ByteArray =
        if (data.isNotEmpty() && (data[0] == 0.toByte() || data[0] == 123.toByte())) byteArrayOf(0) + data else data
    fun same(a: RemoteUploadEntry?, b: RemoteUploadEntry?): Boolean = a != null && b != null &&
        !a.directory && !b.directory && a.size == b.size &&
        (a.modified == 0L || b.modified == 0L || a.modified == b.modified)
    fun key(path: String): String = path.replace('\\', '/').trimEnd('/').ifEmpty { "/" }
    fun name(path: String): String = key(path).substringAfterLast('/')
    fun parent(path: String): String = key(path).substringBeforeLast('/', "").ifEmpty { "/" }
    fun join(parent: String, name: String): String = if (parent == "/") "/$name" else "${parent.trimEnd('/')}/$name"
}

internal data class RemoteUploadEntry(val name: String, val size: Long, val modified: Long, val directory: Boolean)

/** Separate socket for each attempt; every exchange is bounded and correlated. */
internal class UploadChannel private constructor(
    private val mesh: MeshCentralClient,
    private val node: String,
    private val gate: DownloadNetworkAttempt,
    private val epoch: Long
) : Closeable {
    private val lock = Any()
    private val identity = Any()
    private val messages = Channel<JsonObject>(64)
    private val tunnelId = UUID.randomUUID().toString()
    private var socket: WebSocket? = null
    private var closed = false
    private var ready = false

    private fun valid(ws: WebSocket): Boolean = !closed && socket === ws && gate.isCurrent(epoch)
    private fun fail(error: Throwable) = synchronized(lock) {
        if (!closed) {
            closed = true
            messages.close(error)
            socket?.cancel()
        }
    }
    private fun accept(ws: WebSocket, text: String) = synchronized(lock) {
        if (!valid(ws)) return@synchronized
        try {
            if (text == "c" || text == "cr") {
                if (!ready) {
                    ready = true
                    if (!ws.send("5")) throw IOException("Cannot select files protocol")
                    check(messages.trySend(JsonObject().apply { addProperty("action", "_ready") }).isSuccess)
                }
            } else if (text.trimStart().startsWith("{")) {
                val packet = JsonParser.parseString(text).asJsonObject
                if (!messages.trySend(packet).isSuccess) throw IOException("Upload response queue overflow")
            }
        } catch (e: Exception) { fail(e) }
    }
    private fun open() {
        if (!gate.bind(epoch, identity) { fail(IOException("Upload network changed")) }) throw IOException("Retired upload attempt")
        synchronized(lock) {
            if (closed || !gate.isCurrent(epoch) || !mesh.connectionState.value || mesh.isUserLogout) throw IOException("Control unavailable")
            val token = mesh.relayAuthCookie.ifEmpty { mesh.userAuthCookie.ifEmpty { mesh.extractRawToken(mesh.authCookie) } }
            val full = "node//" + node.removePrefix("node//").removePrefix("node/").trim()
            val auth = URLEncoder.encode(token, "UTF-8")
            val url = OkHttpHelper.toWebSocketUrl(mesh.serverUrl,
                "/meshrelay.ashx?p=5&nodeid=${URLEncoder.encode(full, "UTF-8")}&id=$tunnelId&rauth=$auth&auth=$auth")
            val request = Request.Builder().url(url).apply {
                if (mesh.authCookie.isNotBlank()) header("Cookie", mesh.authCookie)
            }.build()
            socket = OkHttpHelper.createClient(mesh.ignoreSsl).newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
                    if (!valid(webSocket)) { webSocket.cancel(); return@synchronized }
                    if (!mesh.connectionState.value || mesh.isUserLogout) { fail(IOException("Control lost")); return@synchronized }
                    mesh.requestTunnel(full, 5, tunnelId)
                }
                override fun onMessage(webSocket: WebSocket, text: String) { accept(webSocket, text) }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) { accept(webSocket, bytes.utf8()) }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(lock) { if (valid(webSocket)) fail(IOException("Upload relay closed")) }
                    webSocket.cancel()
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    synchronized(lock) { if (valid(webSocket)) fail(IOException("Upload relay closed")) }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    synchronized(lock) { if (valid(webSocket)) fail(IOException("Upload relay failed (${t.javaClass.simpleName})")) }
                }
            })
        }
    }
    fun send(packet: JsonObject) = synchronized(lock) {
        if (closed || !gate.isCurrent(epoch) || socket?.send(packet.toString()) != true) throw IOException("Upload transport unavailable")
    }
    private fun binary(data: ByteArray) = synchronized(lock) {
        if (closed || !gate.isCurrent(epoch) || socket?.send(ByteString.of(*UploadWire.encode(data))) != true) throw IOException("Upload data rejected locally")
    }
    private suspend fun receive(action: String, id: String? = null, parent: String? = null): JsonObject =
        withTimeoutOrNull(20_000L) {
            while (true) {
                val packet = messages.receive()
                val kind = packet.get("action")?.asString ?: ""
                val replyId = packet.get("reqid")?.takeUnless { it.isJsonNull }?.asString
                if (kind == "uploaderror" && (replyId == null || replyId == id)) throw UploadRejected("Agent rejected upload")
                val matchingId = id == null || replyId == id ||
                    (replyId == null && parent != null && packet.get("path")?.asString?.let { UploadWire.key(it) == UploadWire.key(parent) } == true)
                if (matchingId && (kind == action || (action == "ls" && kind == "dir"))) return@withTimeoutOrNull packet
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        } ?: throw IOException("Upload acknowledgement timed out")

    suspend fun list(parent: String): List<RemoteUploadEntry> {
        val id = UUID.randomUUID().toString()
        send(JsonObject().apply { addProperty("action", "ls"); addProperty("path", parent); addProperty("reqid", id) })
        val response = receive("ls", id, parent)
        val array = response.getAsJsonArray("dir") ?: response.getAsJsonArray("files") ?: response.getAsJsonArray("list")
            ?: throw IOException("Missing directory data during upload verification")
        return array.mapNotNull { item ->
            if (!item.isJsonObject) null else {
                val entry = item.asJsonObject
                val name = entry.get("name")?.asString ?: entry.get("n")?.asString
                if (name == null) null else RemoteUploadEntry(name,
                    entry.get("s")?.asLong ?: entry.get("size")?.asLong ?: 0L,
                    runCatching { entry.get("d")?.asLong ?: entry.get("date")?.asLong ?: 0L }.getOrDefault(0L),
                    (entry.get("t")?.asInt ?: 0) in setOf(1, 2))
            }
        }
    }
    suspend fun upload(source: File, parent: String, name: String, progress: (Long) -> Unit) {
        val id = UUID.randomUUID().toString()
        send(JsonObject().apply {
            addProperty("action", "upload"); addProperty("path", parent); addProperty("name", name)
            addProperty("size", source.length()); addProperty("reqid", id)
        })
        receive("uploadstart", id)
        var confirmed = 0L
        source.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                binary(buffer.copyOf(count))
                receive("uploadack", id)
                confirmed += count
                progress(confirmed)
            }
        }
        if (confirmed != source.length()) throw IOException("Local upload source changed")
        send(JsonObject().apply { addProperty("action", "uploaddone"); addProperty("reqid", id) })
        receive("uploaddone", id)
    }
    fun rename(parent: String, old: String, new: String) {
        send(JsonObject().apply {
            addProperty("action", "rename"); addProperty("path", parent); addProperty("oldname", old); addProperty("newname", new)
            addProperty("reqid", UUID.randomUUID().toString())
        })
    }
    suspend fun removeOwned(parent: String, name: String) {
        require(name.startsWith(".meshfabric-upload-") || name.startsWith(".meshfabric-backup-"))
        send(JsonObject().apply {
            addProperty("action", "rm"); addProperty("path", parent)
            add("delfiles", com.google.gson.JsonArray().apply { add(name) })
            addProperty("reqid", UUID.randomUUID().toString())
        })
        poll(parent) { entries -> entries.none { it.name == name } }
    }
    suspend fun poll(parent: String, predicate: (List<RemoteUploadEntry>) -> Boolean): List<RemoteUploadEntry> {
        repeat(8) {
            val entries = list(parent)
            if (predicate(entries)) return entries
            delay(250L)
        }
        throw IOException("Remote upload finalization not yet confirmed")
    }
    override fun close() {
        synchronized(lock) {
            closed = true
            socket?.cancel()
            socket = null
            messages.cancel()
        }
        gate.release(epoch, identity)
    }
    companion object {
        suspend fun connect(mesh: MeshCentralClient, node: String, gate: DownloadNetworkAttempt, epoch: Long): UploadChannel {
            val relay = UploadChannel(mesh, node, gate, epoch)
            try { relay.open(); relay.receive("_ready"); return relay } catch (e: Exception) { relay.close(); throw e }
        }
    }
}

/** Publication journal stays with the job across reconnects; no payload offset is reused. */
internal class UploadPublication(val parent: String, val target: String) {
    private var temp: String? = null
    private var backup: String? = null
    private var original: RemoteUploadEntry? = null
    private var verified: RemoteUploadEntry? = null
    private var publishing = false
    private var committed = false
    private val garbage = linkedSetOf<String>()

    suspend fun recover(relay: UploadChannel): Boolean {
        if (publishing) {
            val entries = relay.list(parent)
            val final = entries.firstOrNull { it.name == target }
            val staged = entries.firstOrNull { it.name == temp }
            val saved = entries.firstOrNull { it.name == backup }
            if (staged == null && UploadWire.same(final, verified)) {
                committed = true
                cleanupBackup(relay, saved)
                return true
            }
            if (saved != null && UploadWire.same(saved, original)) {
                if (final != null) throw UploadConflict("Upload destination changed during publication; backup retained")
                relay.rename(parent, saved.name, target)
                relay.poll(parent) { list -> UploadWire.same(list.firstOrNull { it.name == target }, original) }
                backup = null
            } else if (!(UploadWire.same(final, original) || (original == null && final == null))) {
                throw UploadConflict("Cannot safely recover upload publication; remote files retained")
            }
            publishing = false
        }
        temp?.let { garbage.add(it) }
        temp = null
        verified = null
        original = null
        cleanupGarbage(relay)
        return committed
    }
    private suspend fun cleanupBackup(relay: UploadChannel, saved: RemoteUploadEntry?) {
        if (saved != null && UploadWire.same(saved, original)) {
            try { relay.removeOwned(parent, saved.name); backup = null } catch (e: CancellationException) { throw e } catch (_: IOException) { }
        }
    }
    private suspend fun cleanupGarbage(relay: UploadChannel) {
        for (name in garbage.toList()) {
            try { relay.removeOwned(parent, name); garbage.remove(name) } catch (e: CancellationException) { throw e } catch (_: IOException) { }
        }
    }
    suspend fun transfer(relay: UploadChannel, source: File, progress: (Long) -> Unit) {
        if (recover(relay)) return
        val name = ".meshfabric-upload-${UUID.randomUUID()}.part"
        temp = name
        relay.upload(source, parent, name, progress)
        val entries = relay.poll(parent) { list -> list.any { it.name == name && !it.directory && it.size == source.length() } }
        verified = entries.first { it.name == name }
        original = entries.firstOrNull { it.name == target }
        if (original?.directory == true) throw UploadConflict("Upload target is a directory")
        publishing = true
        original?.let { old ->
            val backupName = ".meshfabric-backup-${UUID.randomUUID()}"
            backup = backupName
            relay.rename(parent, target, backupName)
            relay.poll(parent) { list -> list.none { it.name == target } && UploadWire.same(list.firstOrNull { it.name == backupName }, old) }
        }
        relay.rename(parent, name, target)
        val final = relay.poll(parent) { list -> list.none { it.name == name } && UploadWire.same(list.firstOrNull { it.name == target }, verified) }
        committed = true
        cleanupBackup(relay, final.firstOrNull { it.name == backup })
    }
    suspend fun cancelCleanup(relay: UploadChannel) {
        if (recover(relay)) return
        cleanupGarbage(relay)
    }
}
