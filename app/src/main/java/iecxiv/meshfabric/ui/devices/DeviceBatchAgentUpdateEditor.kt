package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.UUID

internal data class DeviceBatchAgentUpdateDraft(val nodeId: String, val mesh: String, val os: String, val version: String?)
internal data class DeviceBatchAgentUpdateState(val phase: String = "loading", val draft: DeviceBatchAgentUpdateDraft? = null, val code: String = "")

internal fun deviceBatchAgentUpdateIdentity(actor: String, node: String): Boolean {
    val a = actor.split('/'); val n = node.split('/')
    return a.size == 3 && n.size == 3 && a[0] == "user" && n[0] == "node" &&
        a[1] == n[1] && a[2].isNotBlank() && n[2].isNotBlank() &&
        actor.length <= 4096 && node.length <= 4096 && (actor + node).none { it.isISOControl() }
}

internal fun deviceBatchAgentUpdateDraft(node: String, meta: GeneralMeta,
    inventory: List<Pair<MeshGroup, List<MeshNode>>>, version: String?): DeviceBatchAgentUpdateDraft? = runCatching {
    agentActionPacket(node, AgentConsoleAction.FORCE_UPDATE, meta, inventory, 0)
    DeviceBatchAgentUpdateDraft(node, meta.mesh, meta.os, version)
}.getOrNull()

internal fun deviceBatchAgentUpdatePacket(reviewed: DeviceBatchAgentUpdateDraft, node: String,
    meta: GeneralMeta, inventory: List<Pair<MeshGroup, List<MeshNode>>>, version: String?): JsonObject {
    require(deviceBatchAgentUpdateDraft(node, meta, inventory, version) == reviewed)
    return agentActionPacket(node, AgentConsoleAction.FORCE_UPDATE, meta, inventory, 0)
}

internal class DeviceBatchAgentUpdateEditor(private val source: MeshCentralClient, private val original: MeshNode) {
    private val mutable = MutableStateFlow(DeviceBatchAgentUpdateState())
    val state = mutable.asStateFlow()
    private val identity = deviceBatchPowerIdentity(source)
    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var started = false
    private var closed = false
    private var requested = false
    private var attempted = false
    private var tag = ""
    private var reviewed: DeviceBatchAgentUpdateDraft? = null
    private var timer: Job? = null
    private fun same() = deviceBatchPowerSame(source, identity)
    private fun packet(action: String) = JsonObject().apply { addProperty("action", action) }
    private fun send(value: JsonObject) = !closed && same() && socket?.send(value.toString()) == true

    @Synchronized private fun finish(phase: String, code: String = "") {
        if (closed) return
        closed = true; timer?.cancel(); tag = ""
        val previous = socket; socket = null
        // Graceful close preserves queued frames; queued never means installed.
        if (phase == "queued") previous?.close(1000, "") else previous?.cancel()
        jobs.cancel()
        mutable.value = mutable.value.copy(phase = phase, code = code)
    }
    private fun fail(code: String) = finish("error", if (attempted) "unconfirmed" else code)
    private fun read(): Boolean {
        tag = UUID.randomUUID().toString()
        timer?.cancel()
        timer = jobs.launch { delay(25000L); synchronized(this@DeviceBatchAgentUpdateEditor) { if (!closed) fail("timeout") } }
        return send(packet("getsysinfo").apply {
            addProperty("nodeid", original.id); addProperty("tag", tag)
            addProperty("nodeinfo", true); addProperty("cache", true)
        })
    }

    @Synchronized fun start() {
        if (started || closed) return
        started = true
        if (!same() || identity.cookie.isBlank() || !deviceBatchAgentUpdateIdentity(identity.actor, original.id)) { fail("identity"); return }
        timer = jobs.launch { delay(25000L); synchronized(this@DeviceBatchAgentUpdateEditor) { if (!closed) fail("timeout") } }
        jobs.launch {
            while (isActive) {
                delay(100L)
                synchronized(this@DeviceBatchAgentUpdateEditor) { if (!closed && !same()) fail("identity") }
            }
        }
        try {
            socket = OkHttpHelper.createClient(identity.tls).newBuilder().followRedirects(false).followSslRedirects(false).build()
                .newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(identity.url, "/control.ashx?moreargs=1"))
                    .header("Cookie", identity.cookie).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        synchronized(this@DeviceBatchAgentUpdateEditor) {
                            if (closed || !same()) { webSocket.cancel(); return }
                            val auth = packet("urlargs").apply {
                                add("args", JsonObject().apply { addProperty("auth", source.extractRawToken(identity.cookie)) })
                            }
                            if (!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
                        }
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        synchronized(this@DeviceBatchAgentUpdateEditor) {
                            if (closed) return
                            if (!same() || text.length > 2000000) { fail("invalid"); return }
                            runCatching { receive(JsonParser.parseString(text).asJsonObject) }.onFailure { fail("invalid") }
                        }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        synchronized(this@DeviceBatchAgentUpdateEditor) { if (!closed) fail("connection") }
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        synchronized(this@DeviceBatchAgentUpdateEditor) { if (!closed) fail("connection"); webSocket.close(code, "") }
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        synchronized(this@DeviceBatchAgentUpdateEditor) { if (!closed) fail("connection") }
                    }
                })
        } catch (_: Exception) { fail("connection") }
    }

    private fun receive(p: JsonObject) {
        when (p.get("action")?.asString) {
            "ping" -> send(packet("pong"))
            "close", "stopped" -> fail("denied")
            "userinfo" -> {
                if (p.getAsJsonObject("userinfo")?.get("_id")?.asString != identity.actor) { fail("identity"); return }
                if (mutable.value.phase == "loading" && tag.isBlank() && !read()) fail("connection")
            }
            "getsysinfo" -> {
                if (tag.isBlank() || p.get("tag")?.asString != tag || p.get("nodeid")?.asString != original.id) return
                val meta = generalMeta(p, original.id, tag) ?: run { fail("unverified"); return }
                tag = ""; timer?.cancel()
                if (meta.mesh != original.meshid) { fail("changed"); return }
                val version = agentConsoleServerVersion(p)
                val draft = deviceBatchAgentUpdateDraft(original.id, meta, source.groupedDevices.value, version)
                    ?: run { finish("skipped", "unavailable"); return }
                if (!requested) { mutable.value = DeviceBatchAgentUpdateState("ready", draft); return }
                if (draft != reviewed || !same()) { fail("changed"); return }
                val change = deviceBatchAgentUpdatePacket(requireNotNull(reviewed), original.id, meta, source.groupedDevices.value, version)
                attempted = true
                if (send(change)) finish("queued", "installation_unverified") else fail("connection")
            }
        }
    }

    @Synchronized fun submit(draft: DeviceBatchAgentUpdateDraft): Boolean {
        if (closed || requested || attempted || !same() || mutable.value.phase != "ready" || mutable.value.draft != draft) return false
        requested = true; reviewed = draft
        mutable.value = mutable.value.copy(phase = "checking")
        if (!read()) { fail("connection"); return false }
        return true
    }
    @Synchronized fun close() { if (!closed) fail("closed") }
}
