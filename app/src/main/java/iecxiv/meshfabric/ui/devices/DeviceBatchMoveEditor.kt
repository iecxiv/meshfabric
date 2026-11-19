package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.UUID

internal enum class DeviceBatchMoveAck { ACCEPTED, REJECTED }
internal fun deviceBatchMovePacket(
    reviewed: DeviceBatchMoveDraft, node: MeshNode, actor: String,
    groups: List<DeviceBatchMoveGroup>, meta: GeneralMeta, responseId: String
): JsonObject {
    require(responseId.isNotBlank() && responseId.length <= 128 && responseId.none { it.isISOControl() })
    require(deviceBatchMoveStillMatches(reviewed, node, actor, groups, meta))
    return JsonObject().apply {
        addProperty("action", "changeDeviceMesh")
        add("nodeids", JsonArray().apply { add(reviewed.nodeId) })
        addProperty("meshid", reviewed.destinationGroupId)
        addProperty("responseid", responseId)
    }
}

internal fun deviceBatchMoveAck(packet: JsonObject, expected: String): DeviceBatchMoveAck? = runCatching {
    if (expected.isBlank() || packet.get("action")?.asString != "changeDeviceMesh" ||
        packet.get("responseid")?.asString != expected) return@runCatching null
    if (listOf("error", "err").any { packet.has(it) && !packet.get(it).isJsonNull }) return@runCatching DeviceBatchMoveAck.REJECTED
    val result = packet.get("result") ?: return@runCatching null
    if (!result.isJsonPrimitive || !result.asJsonPrimitive.isString) return@runCatching null
    if (result.asString.equals("ok", true)) DeviceBatchMoveAck.ACCEPTED else DeviceBatchMoveAck.REJECTED
}.getOrNull()

internal fun deviceBatchMoveObserved(packet: JsonObject, node: String, tag: String, destination: String): Boolean =
    tag.isNotBlank() && destination.isNotBlank() && generalMeta(packet, node, tag)?.mesh == destination

internal data class DeviceBatchMoveEditorState(
    val phase: String = "loading", val draft: DeviceBatchMoveDraft? = null, val code: String = ""
)

internal class DeviceBatchMoveEditor(
    private val source: MeshCentralClient,
    private val original: MeshNode,
    private val destination: String
) {
    private val mutable = MutableStateFlow(DeviceBatchMoveEditorState())
    val state = mutable.asStateFlow()
    private val identity = deviceBatchPowerIdentity(source)
    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var started = false
    private var closed = false
    private var requested = false
    private var attempted = false
    private var awaitingUser = false
    private var groupTag = ""
    private var nodeTag = ""
    private val writeId = UUID.randomUUID().toString()
    private var site = 0L
    private var groups: List<DeviceBatchMoveGroup>? = null
    private var meta: GeneralMeta? = null
    private var reviewed: DeviceBatchMoveDraft? = null
    private var deadline: Job? = null
    private var poll: Job? = null

    private fun same() = deviceBatchPowerSame(source, identity)
    private fun packet(action: String) = JsonObject().apply { addProperty("action", action) }
    private fun send(value: JsonObject) = !closed && same() && socket?.send(value.toString()) == true
    private fun currentNode(): MeshNode? = source.groupedDevices.value.flatMap { it.second }
        .singleOrNull { it.id == original.id }?.takeIf { it.meshid == original.meshid }

    @Synchronized private fun finish(phase: String, code: String = "") {
        if (closed) return
        closed = true
        deadline?.cancel(); poll?.cancel()
        awaitingUser = false; groupTag = ""; nodeTag = ""; groups = null; meta = null
        socket?.cancel(); socket = null
        jobs.cancel()
        mutable.value = mutable.value.copy(phase = phase, code = code)
    }
    private fun fail(code: String) = finish("error", if (attempted) "unconfirmed" else code)
    private fun armTimeout() {
        deadline?.cancel()
        deadline = jobs.launch {
            delay(25000L)
            synchronized(this@DeviceBatchMoveEditor) { if (!closed) fail("timeout") }
        }
    }
    private fun readNode(): Boolean {
        nodeTag = UUID.randomUUID().toString()
        return send(packet("getsysinfo").apply {
            addProperty("nodeid", original.id); addProperty("tag", nodeTag)
            addProperty("nodeinfo", true); addProperty("cache", true)
        })
    }
    private fun beginRead(): Boolean {
        groups = null; meta = null; groupTag = ""; nodeTag = ""; awaitingUser = true
        armTimeout()
        return send(packet("userinfo"))
    }

    @Synchronized fun start() {
        if (started || closed) return
        started = true
        if (!same() || identity.cookie.isBlank() || currentNode() == null) { fail("identity"); return }
        armTimeout()
        jobs.launch {
            while (isActive) {
                delay(100L)
                synchronized(this@DeviceBatchMoveEditor) { if (!closed && !same()) fail("identity") }
            }
        }
        try {
            socket = OkHttpHelper.createClient(identity.tls).newWebSocket(
                Request.Builder().url(OkHttpHelper.toWebSocketUrl(identity.url, "/control.ashx?moreargs=1"))
                    .header("Cookie", identity.cookie).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        synchronized(this@DeviceBatchMoveEditor) {
                            if (closed || !same()) { webSocket.cancel(); return }
                            val auth = packet("urlargs").apply {
                                add("args", JsonObject().apply { addProperty("auth", source.extractRawToken(identity.cookie)) })
                            }
                            if (!webSocket.send(auth.toString()) || !beginRead()) fail("connection")
                        }
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        synchronized(this@DeviceBatchMoveEditor) {
                            if (closed) return
                            if (!same() || text.length > 2000000) { fail("invalid"); return }
                            runCatching { receive(JsonParser.parseString(text).asJsonObject) }.onFailure { fail("invalid") }
                        }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        synchronized(this@DeviceBatchMoveEditor) { if (!closed) fail("connection") }
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        synchronized(this@DeviceBatchMoveEditor) { if (!closed) fail("connection"); webSocket.close(code, "") }
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        synchronized(this@DeviceBatchMoveEditor) { if (!closed) fail("connection") }
                    }
                }
            )
        } catch (_: Exception) { fail("connection") }
    }

    private fun receive(p: JsonObject) {
        when (p.get("action")?.asString) {
            "ping" -> send(packet("pong"))
            "close", "stopped" -> fail("denied")
            "userinfo" -> {
                val user = p.getAsJsonObject("userinfo") ?: run { fail("identity"); return }
                if (user.get("_id")?.asString != identity.actor) { fail("identity"); return }
                if (!awaitingUser) return
                awaitingUser = false
                site = if (user.has("siteadmin")) deviceBatchMoveReadMask(user.get("siteadmin"))
                    ?: run { fail("invalid"); return } else 0L
                groupTag = UUID.randomUUID().toString()
                if (!send(packet("meshes").apply { addProperty("tag", groupTag); addProperty("responseid", groupTag) }) || !readNode()) fail("connection")
            }
            "meshes" -> {
                if (groupTag.isBlank() || p.get("tag")?.asString != groupTag) return
                groupTag = ""
                if (p.has("error") || p.has("err") || (p.has("result") && p.get("result").asString != "ok")) { fail("denied"); return }
                val raw = p.get("meshes") ?: run { fail("invalid"); return }
                val records = when {
                    raw.isJsonArray -> raw.asJsonArray.map { it.asJsonObject }
                    raw.isJsonObject -> raw.asJsonObject.entrySet().map { (id, value) ->
                        value.asJsonObject.deepCopy().apply {
                            if (!has("_id")) addProperty("_id", id)
                            require(get("_id").asString == id)
                        }
                    }
                    else -> { fail("invalid"); return }
                }
                groups = deviceBatchMoveReadGroups(records, identity.actor, site)?.map { it.group }
                    ?: run { fail("invalid"); return }
                prepared()
            }
            "getsysinfo" -> {
                if (nodeTag.isBlank() || p.get("tag")?.asString != nodeTag || p.get("nodeid")?.asString != original.id) return
                val tag = nodeTag
                nodeTag = ""
                if (attempted) {
                    if (deviceBatchMoveObserved(p, original.id, tag, destination)) {
                        finish("verified")
                        source.requestServerData()
                    }
                } else {
                    meta = generalMeta(p, original.id, tag) ?: run { fail("unverified"); return }
                    prepared()
                }
            }
            "changeDeviceMesh" -> if (attempted && poll == null) {
                when (deviceBatchMoveAck(p, writeId)) {
                    DeviceBatchMoveAck.REJECTED -> finish("rejected", "denied")
                    DeviceBatchMoveAck.ACCEPTED -> {
                        if (!readNode()) { fail("connection"); return }
                        poll = jobs.launch {
                            while (isActive) {
                                delay(2000L)
                                synchronized(this@DeviceBatchMoveEditor) {
                                    if (!closed && nodeTag.isBlank() && !readNode()) fail("connection")
                                }
                            }
                        }
                    }
                    null -> Unit
                }
            }
        }
    }

    private fun prepared() {
        val receivedGroups = groups ?: return
        val receivedMeta = meta ?: return
        val current = currentNode() ?: run { fail("changed"); return }
        val draft = deviceBatchMoveDraft(current, destination, identity.actor, receivedGroups, receivedMeta)
        if (draft == null) { finish("skipped", "unavailable"); return }
        if (!requested) {
            deadline?.cancel()
            mutable.value = DeviceBatchMoveEditorState("ready", draft)
            return
        }
        if (draft != reviewed || !same()) { fail("changed"); return }
        val change = deviceBatchMovePacket(requireNotNull(reviewed), current, identity.actor, receivedGroups, receivedMeta, writeId)
        attempted = true
        mutable.value = mutable.value.copy(phase = "moving")
        armTimeout()
        if (!send(change)) fail("connection")
    }

    @Synchronized fun submit(draft: DeviceBatchMoveDraft): Boolean {
        if (closed || requested || attempted || !same() || mutable.value.phase != "ready" || mutable.value.draft != draft) return false
        requested = true; reviewed = draft
        mutable.value = mutable.value.copy(phase = "checking")
        if (!beginRead()) { fail("connection"); return false }
        return true
    }

    @Synchronized fun close() { if (!closed) fail("closed") }
}
