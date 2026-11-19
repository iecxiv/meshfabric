package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.util.UUID

internal fun generalToastTextValid(title: String, body: String): Boolean =
    title.isNotBlank() && title.length <= 160 && title.none { it.isISOControl() } && Charsets.UTF_8.newEncoder().canEncode(title) &&
    body.isNotBlank() && body.length <= 512 && body.none { it.isISOControl() && it !in "\n\r\t" } && Charsets.UTF_8.newEncoder().canEncode(body)

internal fun generalToastPacket(node: String, id: String, draft: GeneralMessageDraft, meta: GeneralMeta,
    inventory: List<Pair<MeshGroup, List<MeshNode>>>): JsonObject {
    require(node.startsWith("node/") && node.split('/').size == 3 && node.substringAfterLast('/').isNotBlank() && node.length <= 4096 && node.none { it.isISOControl() })
    require(id.isNotBlank() && id.length <= 128 && id.none { it.isISOControl() })
    require(draft.mesh == meta.mesh && generalToastTextValid(draft.title, draft.body) && generalMessageAvailable(node, meta, inventory))
    return JsonObject().apply {
        addProperty("action", "toast"); add("nodeids", JsonArray().apply { add(node) })
        addProperty("title", draft.title); addProperty("msg", draft.body); addProperty("responseid", id)
    }
}
internal fun generalToastAck(packet: JsonObject, node: String, id: String): String? = runCatching {
    if (id.isBlank() || packet.get("action")?.asString != "toast" || packet.get("responseid")?.asString != id) return@runCatching null
    packet.get("nodeid")?.let { if (!it.isJsonPrimitive || it.asString != node) return@runCatching null }
    packet.get("nodeids")?.let {
        if (!it.isJsonArray || it.asJsonArray.size() != 1 || it.asJsonArray[0].asString != node) return@runCatching null
    }
    if (packet.has("error") || packet.has("err")) "rejected" else when (packet.get("result")?.asString?.lowercase()) {
        "ok" -> "accepted"
        "denied", "forbidden", "error", "access denied" -> "rejected"
        else -> null
    }
}.getOrNull()

internal class DeviceToastEditor(private val source: MeshCentralClient, private val node: String) {
    val state = MutableStateFlow(GeneralMessageState())
    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val url = source.serverUrl
    private val cookie = source.authCookie
    private val tls = source.ignoreSsl
    private val actor = source.userInfo.value?.id.orEmpty()
    private val requestId = UUID.randomUUID().toString()
    private var active = true
    private var started = false
    private var requested = false
    private var attempted = false
    private var socket: WebSocket? = null
    private var tag = ""
    private var target: GeneralMessageDraft? = null
    private var timer: Job? = null
    private fun same() = !source.isUserLogout && source.connectionState.value && actor.isNotBlank() &&
        source.userInfo.value?.id == actor && source.serverUrl == url && source.authCookie == cookie && source.ignoreSsl == tls
    private fun packet(action: String) = JsonObject().apply { addProperty("action", action) }
    private fun send(value: JsonObject) = active && same() && socket?.send(value.toString()) == true
    @Synchronized private fun fail(code: String) {
        if (!active) return
        active = false; timer?.cancel(); tag = ""; target = null
        state.value = GeneralMessageState(phase = "error", code = if (attempted) "unconfirmed" else code)
        socket?.cancel(); socket = null; jobs.cancel()
    }
    private fun deadline() {
        timer?.cancel()
        timer = jobs.launch { delay(20000L); synchronized(this@DeviceToastEditor) { if (active) fail("timeout") } }
    }
    private fun read(): Boolean {
        tag = UUID.randomUUID().toString(); deadline()
        return send(packet("getsysinfo").apply { addProperty("nodeid", node); addProperty("tag", tag); addProperty("nodeinfo", true); addProperty("cache", true) })
    }
    @Synchronized fun start() {
        if (started || !active) return
        started = true
        val user = actor.split('/'); val device = node.split('/')
        if (!same() || cookie.isBlank() || user.size != 3 || user[0] != "user" || user[2].isBlank() || device.size != 3 || device[0] != "node" || device[2].isBlank() || user[1] != device[1]) { fail("identity"); return }
        deadline()
        jobs.launch { while (isActive) { delay(500L); synchronized(this@DeviceToastEditor) { if (active && !same()) fail("identity") } } }
        try {
            socket = OkHttpHelper.createClient(tls).newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(
                Request.Builder().url(OkHttpHelper.toWebSocketUrl(url, "/control.ashx?moreargs=1")).header("Cookie", cookie).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { synchronized(this@DeviceToastEditor) {
                        if (!active || !same()) { webSocket.cancel(); return }
                        val auth = packet("urlargs").apply { add("args", JsonObject().apply { addProperty("auth", source.extractRawToken(cookie)) }) }
                        if (!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
                    } }
                    override fun onMessage(webSocket: WebSocket, text: String) { synchronized(this@DeviceToastEditor) {
                        if (!active) return
                        if (!same() || text.length > 2000000) { fail("connection"); return }
                        runCatching { receive(JsonParser.parseString(text).asJsonObject) }.onFailure { fail("invalid") }
                    } }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { fail("connection") }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { fail("connection") }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { fail("connection") }
                })
        } catch (_: Exception) { fail("connection") }
    }
    private fun receive(value: JsonObject) {
        when (value.get("action")?.asString) {
            "ping" -> if (!send(packet("pong"))) fail("connection")
            "close", "stopped" -> fail("denied")
            "userinfo" -> {
                if (value.getAsJsonObject("userinfo")?.get("_id")?.asString != actor) { fail("identity"); return }
                if (state.value.phase == "loading" && tag.isEmpty() && !read()) fail("connection")
            }
            "getsysinfo" -> if (tag.isNotBlank() && value.get("tag")?.asString == tag && value.get("nodeid")?.asString == node && state.value.phase in setOf("loading", "checking")) {
                val meta = generalMeta(value, node, tag) ?: run { fail("unverified"); return }
                if (!generalMessageAvailable(node, meta, source.groupedDevices.value)) { fail("unavailable"); return }
                timer?.cancel(); tag = ""
                val draft = target
                if (draft == null) { state.value = GeneralMessageState(meta, "ready"); return }
                val change = runCatching { generalToastPacket(node, requestId, draft, meta, source.groupedDevices.value) }.getOrNull()
                    ?: run { fail("changed"); return }
                target = null; attempted = true
                if (!send(change)) { fail("unconfirmed"); return }
                state.value = GeneralMessageState(meta, "sent"); deadline()
            }
            "toast" -> if (attempted && state.value.phase == "sent") generalToastAck(value, node, requestId)?.let { phase ->
                active = false; timer?.cancel(); state.value = state.value.copy(phase = phase)
                socket?.cancel(); socket = null; jobs.cancel()
            }
        }
    }
    @Synchronized fun submit(draft: GeneralMessageDraft): Boolean {
        val meta = state.value.meta ?: return false
        if (!active || !same() || requested || attempted || state.value.phase != "ready") return false
        if (runCatching { generalToastPacket(node, requestId, draft, meta, source.groupedDevices.value) }.isFailure) return false
        requested = true; target = draft; state.value = state.value.copy(phase = "checking")
        if (!read()) { fail("connection"); return false }
        return true
    }
    @Synchronized fun close() { if (active) fail("closed") }
}
