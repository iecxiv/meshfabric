package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.*
import iecxiv.meshfabric.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

internal data class BatchUploadSnapshot(val listing: DeviceBatchUploadListing, val sizes: Map<String, Long>)
internal data class BatchUploadResult(val detail: String)
private suspend fun <T : Any> uploadBounded(ms: Long, block: suspend CoroutineScope.() -> T): T =
    withTimeoutOrNull(ms, block) ?: throw IOException("File operation timed out")
private fun uploadText(p: JsonObject, key: String): String? = p.get(key)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
private fun uploadSameName(a: String, b: String, platform: DevicePlatform) = a.equals(b, platform == DevicePlatform.WINDOWS)
internal fun batchUploadPublishable(draft: DeviceBatchUploadDraft, snapshot: BatchUploadSnapshot): Boolean {
    val listing = snapshot.listing
    if (!listing.complete || listing.directoryState != DeviceBatchUploadDirectoryState.EXISTS ||
        listing.nodeId != draft.nodeId || listing.directory != draft.directory) return false
    val hits = listing.names.filter { uploadSameName(it, draft.file.name, draft.platform) }
    if (hits.isEmpty()) return true
    val name = hits.singleOrNull() ?: return false
    return draft.options.overwriteFileIfExists && name in listing.regularFiles && name !in listing.directories &&
        snapshot.sizes[name]?.let { it >= 0L } == true
}
internal fun batchUploadObserved(snapshot: BatchUploadSnapshot, draft: DeviceBatchUploadDraft, staging: String): Boolean {
    val listing = snapshot.listing
    if (!listing.complete || listing.nodeId != draft.nodeId || listing.directory != draft.directory ||
        listing.directoryState != DeviceBatchUploadDirectoryState.EXISTS || staging in listing.names) return false
    val name = listing.names.singleOrNull { uploadSameName(it, draft.file.name, draft.platform) } ?: return false
    return name in listing.regularFiles && name !in listing.directories && snapshot.sizes[name] == draft.file.size
}

private class BatchUploadLink(private val source: MeshCentralClient, private val node: MeshNode,
    private val identity: DeviceBatchPowerIdentity) : Closeable {
    private val lock = Any()
    private val messages = Channel<JsonObject>(32)
    private val connected = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var closed = false
    private var protocolStarted = false
    private val tunnel = UUID.randomUUID().toString()
    fun valid(): Boolean = !closed && deviceBatchPowerSame(source, identity) &&
        source.groupedDevices.value.flatMap { it.second }.singleOrNull { it.id == node.id }
            ?.let { it.meshid == node.meshid && it.conn and 1 != 0 } == true
    private fun fail() { synchronized(lock) { close() } }
    private fun accept(ws: WebSocket, text: String) {
        synchronized(lock) {
            if (socket !== ws || !valid()) { fail(); return }
            try {
                if (text.length > 2000000) throw IOException("File reply too large")
                if (text == "c" || text == "cr") {
                    if (!protocolStarted) {
                        protocolStarted = true
                        if (!ws.send("5")) throw IOException("Files protocol unavailable")
                        connected.complete(Unit)
                    }
                } else if (protocolStarted && text.trimStart().startsWith("{")) {
                    if (!messages.trySend(JsonParser.parseString(text).asJsonObject).isSuccess) throw IOException("File reply queue overflow")
                }
            } catch (_: Exception) { fail() }
        }
    }
    private fun open() {
        synchronized(lock) {
            check(valid())
            val token = source.relayAuthCookie.ifEmpty { source.userAuthCookie.ifEmpty { source.extractRawToken(identity.cookie) } }
            val auth = URLEncoder.encode(token, "UTF-8")
            val url = OkHttpHelper.toWebSocketUrl(identity.url,
                "/meshrelay.ashx?p=5&nodeid=${URLEncoder.encode(node.id, "UTF-8")}&id=$tunnel&rauth=$auth&auth=$auth")
            socket = OkHttpHelper.createClient(identity.tls).newBuilder().followRedirects(false).followSslRedirects(false).build()
                .newWebSocket(Request.Builder().url(url).header("Cookie", identity.cookie).build(), object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        synchronized(lock) {
                            if (socket !== ws || !valid()) { ws.cancel(); fail(); return }
                            runCatching { source.requestTunnel(node.id, 5, tunnel) }.onFailure { fail() }
                        }
                    }
                    override fun onMessage(ws: WebSocket, text: String) { accept(ws, text) }
                    override fun onMessage(ws: WebSocket, bytes: ByteString) {
                        if (bytes.size > 2000000) fail() else accept(ws, bytes.utf8())
                    }
                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { fail() }
                    override fun onClosing(ws: WebSocket, code: Int, reason: String) { fail(); ws.cancel() }
                    override fun onClosed(ws: WebSocket, code: Int, reason: String) { fail() }
                })
        }
        scope.launch { while (isActive) { delay(100); if (!valid()) { fail(); break } } }
    }
    fun send(p: JsonObject): Boolean = synchronized(lock) { valid() && socket?.send(p.toString()) == true }
    private fun binary(bytes: ByteArray): Boolean = synchronized(lock) {
        valid() && socket?.send(UploadWire.encode(bytes).toByteString()) == true
    }
    private suspend fun receive(action: String, id: String): JsonObject = uploadBounded(20000L) {
        while (true) {
            check(valid())
            val p = messages.receive()
            check(valid())
            if (uploadText(p, "action") == "uploaderror") throw IOException("Agent rejected transfer")
            if (uploadText(p, "reqid") != id) continue
            if (p.has("error") || p.has("err")) throw IOException("Agent rejected operation")
            val kind = uploadText(p, "action")
            if (action == "ls" && (kind in setOf("ls", "dir") || kind == null && listOf("dir", "files", "list").any { p.has(it) })) return@uploadBounded p
            if (kind == action) return@uploadBounded p
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }
    private suspend fun rawList(path: String): JsonObject {
        val id = UUID.randomUUID().toString()
        check(send(JsonObject().apply { addProperty("action", "ls"); addProperty("path", path); addProperty("reqid", id) }))
        return receive("ls", id).also { require(uploadText(it, "reqid") == id) }
    }
    suspend fun list(path: String, platform: DevicePlatform): BatchUploadSnapshot {
        val raw = rawList(path)
        val listing = requireNotNull(deviceBatchUploadDecodeListing(raw, node.id, path, platform, requireNotNull(uploadText(raw, "reqid"))))
        val array = listOf("dir", "files", "list").firstNotNullOf { raw.get(it)?.takeIf { value -> value.isJsonArray } }.asJsonArray
        val sizes = mutableMapOf<String, Long>()
        array.forEach { item ->
            val entry = item.asJsonObject
            val name = uploadText(entry, "name") ?: uploadText(entry, "n")
            if (name in listing.regularFiles) {
                val size = entry.get("s") ?: entry.get("size")
                if (size != null && size.isJsonPrimitive && size.asJsonPrimitive.isNumber) {
                    val bytes = size.asBigDecimal.longValueExact()
                    require(bytes >= 0L)
                    sizes[requireNotNull(name)] = bytes
                }
            }
        }
        return BatchUploadSnapshot(listing, sizes.toMap())
    }
    suspend fun directory(target: String, platform: DevicePlatform, create: Boolean,
        beforeMutation: suspend () -> Unit): BatchUploadSnapshot {
        val windows = platform == DevicePlatform.WINDOWS
        val root = if (windows) target.take(3) else "/"
        if (windows) {
            val raw = rawList("")
            require(uploadText(raw, "path") == "")
            val drives = requireNotNull(WindowsDriveSupport.rootListing(raw, true))
            require(drives.any { it.path.trimEnd('/').equals(root.trimEnd('/'), true) })
        }
        var parent = root
        val rest = target.removePrefix(root).split('/').filter { it.isNotEmpty() }
        for (part in rest) {
            val child = UploadWire.join(parent, part)
            var snapshot = list(parent, platform)
            var evidence = deviceBatchUploadDirectoryEvidence(snapshot.listing, child, platform)
            if (evidence == DeviceBatchUploadDirectoryState.MISSING) {
                if (!create) return BatchUploadSnapshot(DeviceBatchUploadListing(node.id, target, snapshot.listing.requestId,
                    emptyList(), true, DeviceBatchUploadDirectoryState.MISSING), emptyMap())
                beforeMutation()
                check(send(JsonObject().apply { addProperty("action", "mkdir"); addProperty("path", child); addProperty("reqid", UUID.randomUUID().toString()) }))
                for (attempt in 0 until 5) {
                    snapshot = list(parent, platform)
                    evidence = deviceBatchUploadDirectoryEvidence(snapshot.listing, child, platform)
                    if (evidence == DeviceBatchUploadDirectoryState.EXISTS) break
                    if (attempt < 4) delay(200L)
                }
            }
            require(evidence == DeviceBatchUploadDirectoryState.EXISTS) { "Directory existence not verified" }
            parent = child
        }
        return list(target, platform)
    }
    suspend fun put(input: java.io.InputStream, file: DeviceBatchUploadFile, parent: String, name: String, progress: (Long) -> Unit) {
        require(file.size in 0L..9007199254740991L)
        val id = UUID.randomUUID().toString()
        check(send(JsonObject().apply { addProperty("action", "upload"); addProperty("path", parent); addProperty("name", name); addProperty("size", file.size); addProperty("reqid", id) }))
        receive("uploadstart", id)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val buffer = ByteArray(65536)
        while (true) {
            currentCoroutineContext().ensureActive()
            var count = input.read(buffer)
            if (count < 0) break
            if (count == 0) { val byte = input.read(); if (byte < 0) break; buffer[0] = byte.toByte(); count = 1 }
            require(total <= file.size && count.toLong() <= file.size - total)
            check(binary(buffer.copyOf(count)))
            receive("uploadack", id)
            digest.update(buffer, 0, count); total += count; progress(total)
        }
        val hash = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        require(total == file.size && hash == file.sha256) { "Local source changed" }
        check(send(JsonObject().apply { addProperty("action", "uploaddone"); addProperty("reqid", id) }))
        receive("uploaddone", id)
    }
    fun rename(parent: String, old: String, new: String) {
        check(send(JsonObject().apply { addProperty("action", "rename"); addProperty("path", parent); addProperty("oldname", old); addProperty("newname", new); addProperty("reqid", UUID.randomUUID().toString()) }))
    }
    suspend fun observed(path: String, platform: DevicePlatform, predicate: (BatchUploadSnapshot) -> Boolean): BatchUploadSnapshot {
        repeat(8) { attempt -> val s = list(path, platform); if (predicate(s)) return s; if (attempt < 7) delay(250L) }
        throw IOException("Publication not observed")
    }
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true; socket?.cancel(); socket = null
            connected.completeExceptionally(IOException("File channel closed")); messages.close(IOException("File channel closed")); scope.cancel()
        }
    }
    companion object {
        suspend fun connect(source: MeshCentralClient, node: MeshNode, identity: DeviceBatchPowerIdentity): BatchUploadLink {
            val link = BatchUploadLink(source, node, identity)
            try { link.open(); uploadBounded(25000L) { link.connected.await() }; return link }
            catch (failure: Throwable) { link.close(); throw failure }
        }
    }
}

private suspend fun uploadMeta(source: MeshCentralClient, node: MeshNode, identity: DeviceBatchPowerIdentity): GeneralMeta = coroutineScope {
    check(deviceBatchPowerSame(source, identity))
    val editor = DeviceGeneralEditor(source, node, this)
    val guard = launch { while (isActive) { delay(100); if (!deviceBatchPowerSame(source, identity)) editor.close() } }
    try {
        editor.start()
        val state = uploadBounded(30000L) { editor.state.first { it.phase in setOf("ready", "error") } }
        check(state.phase == "ready" && deviceBatchPowerSame(source, identity))
        val meta = requireNotNull(state.meta)
        require(meta.mesh == node.meshid && deviceBatchUploadAvailable(node.id, meta, source.groupedDevices.value))
        meta
    } finally { guard.cancel(); editor.close() }
}
internal suspend fun reviewBatchUpload(source: MeshCentralClient, node: MeshNode, identity: DeviceBatchPowerIdentity,
    options: DeviceBatchUploadOptions, file: DeviceBatchUploadFile): DeviceBatchUploadDraft = withContext(Dispatchers.IO) {
    require(deviceBatchAgentUpdateIdentity(identity.actor, node.id))
    require(file.size in 0L..9007199254740991L)
    val meta = uploadMeta(source, node, identity)
    val platform = identifyDevicePlatform(null, meta.os)
    val target = requireNotNull(deviceBatchUploadRoute(options, platform))
    BatchUploadLink.connect(source, node, identity).use { link ->
        val listing = link.directory(target, platform, false) { error("Read-only review") }.listing
        requireNotNull(deviceBatchUploadDraft(identity.actor, node.id, meta, source.groupedDevices.value, options, file, listing, listing.requestId))
    }
}
internal suspend fun transferBatchUpload(source: MeshCentralClient, node: MeshNode, identity: DeviceBatchPowerIdentity,
    draft: DeviceBatchUploadDraft, prepared: PreparedDeviceBatchUpload,
    mutation: () -> Unit, progress: (Long) -> Unit, journal: (String) -> Unit): BatchUploadResult = withContext(Dispatchers.IO) {
    require(prepared.metadata == draft.file && draft.nodeId == node.id && draft.actorId == identity.actor)
    suspend fun authorize() {
        currentCoroutineContext().ensureActive()
        val meta = uploadMeta(source, node, identity)
        require(meta.mesh == draft.mesh && meta.os == draft.os && deviceBatchUploadRoute(draft.options, draft.platform) == draft.directory)
    }
    val operation = currentCoroutineContext()
    prepared.openVerified { operation.ensureActive() }.use { input ->
        authorize()
        BatchUploadLink.connect(source, node, identity).use { link ->
            val directory = link.directory(draft.directory, draft.platform, draft.options.createDirectoryIfMissing) { authorize(); mutation() }
            require(batchUploadPublishable(draft, directory)) { "Destination conflict" }
            val staging = ".meshfabric-upload-${UUID.randomUUID()}.part"
            var backup: String? = null
            journal("Staging: " + UploadWire.join(draft.directory, staging))
            authorize(); mutation()
            link.put(input, draft.file, draft.directory, staging, progress)
            val staged = link.observed(draft.directory, draft.platform) { staging in it.listing.regularFiles && it.sizes[staging] == draft.file.size }
            require(batchUploadPublishable(draft, staged)) { "Destination changed during upload" }
            authorize()
            val current = link.list(draft.directory, draft.platform)
            require(batchUploadPublishable(draft, current)) { "Destination changed before publication" }
            val old = current.listing.names.singleOrNull { uploadSameName(it, draft.file.name, draft.platform) }
            if (old != null) {
                require(draft.options.overwriteFileIfExists && old in current.listing.regularFiles)
                val backupName = ".meshfabric-backup-${UUID.randomUUID()}"
                backup = backupName
                journal("Backup retained: " + UploadWire.join(draft.directory, backupName))
                mutation(); link.rename(draft.directory, old, backupName)
                link.observed(draft.directory, draft.platform) { s ->
                    s.listing.names.none { uploadSameName(it, draft.file.name, draft.platform) } && backupName in s.listing.regularFiles && s.sizes[backupName] == current.sizes[old]
                }
            }
            authorize()
            val beforePublish = link.list(draft.directory, draft.platform)
            require(beforePublish.listing.names.none { uploadSameName(it, draft.file.name, draft.platform) }) { "Destination appeared before rename" }
            mutation(); link.rename(draft.directory, staging, draft.file.name)
            link.observed(draft.directory, draft.platform) { batchUploadObserved(it, draft, staging) }
            BatchUploadResult("Protocol acknowledged; type/size observed; remote SHA-256 not verified." +
                (backup?.let { " Backup retained: " + UploadWire.join(draft.directory, it) } ?: ""))
        }
    }
}
