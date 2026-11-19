package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*

internal fun deviceLastSeenValues(packet: JsonObject, actor: String, allowed: Set<String>): Map<String, Long>? = runCatching {
    val user = actor.split('/')
    require(user.size == 3 && user[0] == "user" && user[2].isNotBlank() && actor.length <= 4096 && actor.none { it.isISOControl() })
    require(packet.get("action")?.asString == "lastconnects" && !packet.has("error") && !packet.has("err"))
    packet.get("result")?.let { require(it.isJsonPrimitive && it.asString == "ok") }
    val values = packet.get("lastconnects")
    require(values != null && values.isJsonObject && values.asJsonObject.entrySet().size <= 100000)
    val result = linkedMapOf<String, Long>()
    for ((id, value) in values.asJsonObject.entrySet()) {
        val node = id.split('/')
        if (id !in allowed || id.length > 4096 || id.any { it.isISOControl() } || node.size != 3 || node[0] != "node" || node[1] != user[1] || node[2].isBlank()) continue
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) continue
        val time = runCatching { value.asBigDecimal.longValueExact().takeIf { it > 0L } }.getOrNull() ?: continue
        result[id] = time
    }
    result.toMap()
}.getOrNull()

internal class DeviceLastSeenBinding(val source: MeshCentralClient) {
    val url = source.serverUrl
    val cookie = source.authCookie
    val tls = source.ignoreSsl
    val actor = source.userInfo.value?.id.orEmpty()
    fun matches(): Boolean {
        val parts = actor.split('/')
        return parts.size == 3 && parts[0] == "user" && parts[2].isNotBlank() && url.isNotBlank() && cookie.isNotBlank() &&
            !source.isUserLogout && source.connectionState.value && source.serverUrl == url && source.authCookie == cookie &&
            source.ignoreSsl == tls && source.userInfo.value?.id == actor
    }
}
internal data class DeviceLastSeenSnapshot(val binding: DeviceLastSeenBinding? = null, val values: Map<String, Long> = emptyMap())

private fun deviceLastSeenOnce(binding: DeviceLastSeenBinding): Flow<DeviceLastSeenSnapshot> = callbackFlow {
    val lock = Any()
    var active = true
    var requested = false
    var socket: WebSocket? = null
    fun finish(values: Map<String, Long>?) = synchronized(lock) {
        if (active) {
            active = false
            trySend(DeviceLastSeenSnapshot(binding, values.orEmpty()))
            close()
            socket?.cancel()
        }
        Unit
    }
    val deadline = launch { delay(25000L); finish(null) }
    val identityWatch = launch { while (isActive) { delay(500L); if (!binding.matches()) finish(null) } }
    try {
        if (!binding.matches()) finish(null) else {
            socket = OkHttpHelper.createClient(binding.tls).newBuilder().followRedirects(false).followSslRedirects(false).build()
                .newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(binding.url, "/control.ashx?moreargs=1")).header("Cookie", binding.cookie).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { synchronized(lock) {
                        if (!active || !binding.matches()) { webSocket.cancel(); return }
                        val auth = JsonObject().apply {
                            addProperty("action", "urlargs")
                            add("args", JsonObject().apply { addProperty("auth", binding.source.extractRawToken(binding.cookie)) })
                        }
                        if (!webSocket.send(auth.toString()) || !webSocket.send("{\"action\":\"userinfo\"}")) finish(null)
                    } }
                    override fun onMessage(webSocket: WebSocket, text: String) { synchronized(lock) {
                        if (!active) return
                        if (!binding.matches() || text.length > 2000000) { finish(null); return }
                        runCatching {
                            val packet = JsonParser.parseString(text).asJsonObject
                            when (packet.get("action")?.asString) {
                                "ping" -> if (!webSocket.send("{\"action\":\"pong\"}")) finish(null)
                                "close", "stopped" -> finish(null)
                                "userinfo" -> {
                                    if (packet.getAsJsonObject("userinfo")?.get("_id")?.asString != binding.actor) { finish(null); return }
                                    if (!requested) {
                                        requested = true
                                        if (!webSocket.send("{\"action\":\"lastconnects\"}")) finish(null)
                                    }
                                }
                                "lastconnects" -> if (requested) {
                                    val allowed = binding.source.groupedDevices.value.flatMap { it.second }.map { it.id }.toSet()
                                    finish(deviceLastSeenValues(packet, binding.actor, allowed))
                                }
                            }
                        }.onFailure { finish(null) }
                    } }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { finish(null) }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { finish(null); webSocket.close(code, "") }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish(null) }
                })
            synchronized(lock) { if (!active) socket?.cancel() }
        }
    } catch (_: Exception) { finish(null) }
    awaitClose { synchronized(lock) { active = false; socket?.cancel() }; deadline.cancel(); identityWatch.cancel() }
}

internal fun deviceLastSeenSnapshots(binding: DeviceLastSeenBinding): Flow<DeviceLastSeenSnapshot> = flow {
    emit(DeviceLastSeenSnapshot(binding))
    while (currentCoroutineContext().isActive && binding.matches()) {
        emitAll(deviceLastSeenOnce(binding))
        delay(30000L)
    }
}
