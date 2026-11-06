package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID

internal val inventoryActions = setOf("getsysinfo", "getnetworkinfo", "lastconnect")
internal data class DeviceInventoryState(
    val packets: Map<String, JsonObject> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val pending: Set<String> = inventoryActions
)

internal fun inventoryRequest(action: String, nodeId: String, responseId: String): JsonObject {
    require(action in inventoryActions)
    return JsonObject().apply {
        addProperty("action", action)
        addProperty("nodeid", nodeId)
        addProperty("responseid", responseId)
        if (action == "getsysinfo") {
            addProperty("cache", true)
            addProperty("nodeinfo", true)
        }
    }
}

internal fun inventoryReplyMatches(packet: JsonObject, action: String, nodeId: String, responseId: String): Boolean {
    if (packet.string("action") != action) return false
    val replyId = packet.string("responseid")
    if (replyId.isNotEmpty() && replyId != responseId) return false
    val replyNode = packet.string("nodeid")
    if (replyNode.isNotEmpty() && replyNode != nodeId) return false
    val nested = packet.get("node")?.takeIf { it.isJsonObject }?.asJsonObject?.string("_id").orEmpty()
    return nested.isEmpty() || nested == nodeId
}

internal fun JsonObject.string(key: String): String = get(key)
    ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

/** A dedicated, read-only socket per dialog; never shares callbacks with remote sessions. */
internal fun deviceInventory(source: MeshCentralClient, nodeId: String) = callbackFlow {
    val url = source.serverUrl
    val cookie = source.authCookie
    val ignoreSsl = source.ignoreSsl
    val lock = Any()
    var active = true
    var started = false
    var state = DeviceInventoryState()
    val ids = inventoryActions.associateWith { UUID.randomUUID().toString() }
    var socket: WebSocket? = null
    fun finish(reason: String) = synchronized(lock) {
        if (active) {
            active = false
            state = state.copy(errors = state.errors + state.pending.associateWith { reason }, pending = emptySet())
            trySend(state)
            socket?.close(1000, "Inventory finished")
            close()
        }
    }
    trySend(state)
    val timeout = launch { delay(25_000); finish("timeout") }
    try {
        if (cookie.isBlank() || url.isBlank() || source.isUserLogout) {
            finish("disconnected")
        } else {
            socket = OkHttpHelper.createClient(ignoreSsl).newWebSocket(
                Request.Builder().url(OkHttpHelper.toWebSocketUrl(url, "/control.ashx?moreargs=1"))
                    .header("Cookie", cookie).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
                        if (!active) { webSocket.cancel(); return@synchronized }
                        val auth = JsonObject().apply {
                            addProperty("action", "urlargs")
                            add("args", JsonObject().apply { addProperty("auth", source.extractRawToken(cookie)) })
                        }
                        if (!webSocket.send(auth.toString()) || !webSocket.send("{\"action\":\"userinfo\"}")) finish("send")
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
                        if (!active) return@synchronized
                        if (text.length > 2_000_000) { finish("oversize"); return@synchronized }
                        val packet = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                            ?: return@synchronized
                        when (val action = packet.string("action")) {
                            "ping" -> webSocket.send("{\"action\":\"pong\"}")
                            "close", "stopped" -> finish("denied")
                            "userinfo" -> {
                                val user = packet.get("userinfo")?.takeIf { it.isJsonObject }?.asJsonObject
                                if (!started && !user?.string("_id").isNullOrEmpty()) {
                                    started = true
                                    inventoryActions.forEach { name ->
                                        if (!webSocket.send(inventoryRequest(name, nodeId, ids.getValue(name)).toString())) finish("send")
                                    }
                                }
                            }
                            in inventoryActions -> {
                                if (!started || action !in state.pending ||
                                    !inventoryReplyMatches(packet, action, nodeId, ids.getValue(action))) return@synchronized
                                val result = packet.string("result")
                                val denied = packet.string("error").isNotEmpty() ||
                                    (result.isNotEmpty() && !result.equals("ok", true))
                                state = if (denied) state.copy(errors = state.errors + (action to "denied"), pending = state.pending - action)
                                else state.copy(packets = state.packets + (action to packet), pending = state.pending - action)
                                trySend(state)
                                if (state.pending.isEmpty()) finish("")
                            }
                        }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { finish("connection") }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        finish("connection")
                        webSocket.close(code, "")
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish("connection") }
                })
            synchronized(lock) { if (!active) socket?.cancel() }
        }
    } catch (_: Exception) { finish("connection") }
    awaitClose {
        synchronized(lock) { active = false; socket?.cancel() }
        timeout.cancel()
    }
}
