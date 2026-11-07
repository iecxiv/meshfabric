package iecxiv.meshfabric.ui.management

import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ProfileState(
val ready: Boolean = false, val loading: Boolean = false, val busy: Boolean = false,
val user: JSONObject? = null, val users: List<JSONObject> = emptyList(),
val groups: List<JSONObject> = emptyList(), val notice: String = "disconnected",
val readError: Boolean = false
) {
val site: Long get() = user?.optLong("siteadmin", 0L) ?: 0L
val id: String get() = user?.optString("_id").orEmpty()
val canWrite: Boolean get() = ready && !loading && !busy && !readError
fun rights(group: JSONObject): Long = group.optJSONObject("links")?.optJSONObject(id)?.optLong("rights", 0L) ?: 0L
}

internal class ProfileSession(private val source: MeshCentralClient) {
val state = MutableStateFlow(ProfileState())
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private var socket: WebSocket? = null
private var generation = 0L
private var pendingId = ""
private var pendingAction = ""
private var timer: Job? = null
private var readTimer: Job? = null
private val reads = mutableSetOf<String>()
private var closed = false

@Synchronized fun start() {
if (closed || socket != null || source.isUserLogout || !source.connectionState.value || source.authCookie.isBlank()) return
val epoch = ++generation
state.value = ProfileState(notice = "connecting")
runCatching {
val request = Request.Builder().url(OkHttpHelper.toWebSocketUrl(source.serverUrl, "/control.ashx?moreargs=1"))
.header("Cookie", source.authCookie).build()
socket = OkHttpHelper.createClient(source.ignoreSsl).newWebSocket(request, object : WebSocketListener() {
override fun onOpen(webSocket: WebSocket, response: Response) {
synchronized(this@ProfileSession) {
if (epoch != generation) { webSocket.cancel(); return }
webSocket.send(JSONObject().put("action", "urlargs").put("args", JSONObject().put("auth", source.extractRawToken(source.authCookie))).toString())
webSocket.send(JSONObject().put("action", "userinfo").toString())
}
}
override fun onMessage(webSocket: WebSocket, text: String) {
synchronized(this@ProfileSession) {
if (epoch != generation) return
if (text.length > 2_000_000) { stop("oversize"); return }
runCatching { JSONObject(text) }.getOrNull()?.let { receive(it) }
}
}
override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, "") }
override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { failed(epoch) }
override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { failed(epoch) }
})
timer = scope.launch { delay(20_000); synchronized(this@ProfileSession) { if (epoch == generation && !state.value.ready) stop("timeout") } }
}.onFailure { stop("disconnected") }
}
@Synchronized private fun failed(epoch: Long) { if (epoch == generation) stop("disconnected") }
@Synchronized fun stop(reason: String = "disconnected") {
val uncertain = pendingId.isNotBlank()
generation++; socket?.cancel(); socket = null
timer?.cancel(); readTimer?.cancel(); reads.clear(); pendingId = ""; pendingAction = ""
state.value = ProfileState(notice = if (uncertain) "unknown" else reason)
}
@Synchronized fun close() { closed = true; stop(); scope.cancel() }
private fun send(action: String): Boolean = socket?.send(JSONObject().put("action", action).toString()) == true
@Synchronized fun refresh() {
if (!state.value.ready || state.value.busy) return
send("userinfo"); loadLists()
}
private fun loadLists() {
reads.clear(); reads.add("meshes")
if (ProfilePermissions.manageUsers(state.value.site)) reads.add("users")
state.update { it.copy(loading = true, readError = false, users = if ("users" in reads) it.users else emptyList()) }
if (reads.toList().any { !send(it) }) { stop(); return }
val epoch = generation
readTimer?.cancel()
readTimer = scope.launch { delay(20_000); synchronized(this@ProfileSession) {
if (epoch == generation && reads.isNotEmpty()) {
reads.clear(); state.update { it.copy(loading = false, readError = true, notice = "readFailed") }
}
} }
}
private fun records(value: Any?): List<JSONObject>? {
val result = mutableListOf<JSONObject>()
when (value) {
is JSONArray -> for (index in 0 until value.length()) {
val item = value.optJSONObject(index) ?: return null
result.add(JSONObject(item.toString()))
}
is JSONObject -> for (key in value.keys()) {
val item = value.optJSONObject(key) ?: return null
result.add(JSONObject(item.toString()).apply { if (optString("_id").isBlank()) put("_id", key) })
}
else -> return null
}
if (result.any { it.optString("_id").isBlank() }) return null
if (result.map { it.optString("_id") }.distinct().size != result.size) return null
return result.sortedBy { it.optString("name").lowercase() }
}
private fun receive(packet: JSONObject) {
val action = packet.optString("action")
when (action) {
"ping" -> send("pong")
"close", "stopped" -> { stop(); return }
"userinfo" -> {
val user = packet.optJSONObject("userinfo") ?: return
if (user.optString("_id").isBlank()) return
val first = !state.value.ready
if (state.value.id.isNotBlank() && state.value.id != user.optString("_id")) { stop(); return }
state.update { it.copy(ready = true, user = JSONObject(user.toString()), notice = if (first) "ready" else it.notice) }
if (first) { timer?.cancel(); loadLists() }
if (!ProfilePermissions.manageUsers(state.value.site)) {
reads.remove("users")
state.update { it.copy(users = emptyList(), loading = reads.isNotEmpty()) }
}
}
"users", "meshes" -> {
if (action !in reads) return
val list = records(packet.opt(action))
reads.remove(action)
state.update { current ->
if (list == null) current.copy(loading = reads.isNotEmpty(), readError = true, notice = "readFailed")
else if (action == "users") current.copy(users = list, loading = reads.isNotEmpty())
else current.copy(groups = list, loading = reads.isNotEmpty())
}
if (reads.isEmpty()) readTimer?.cancel()
}
}
if (pendingId.isNotBlank() && packet.optString("responseid") == pendingId && action == pendingAction) {
timer?.cancel(); pendingId = ""; pendingAction = ""
val ok = !packet.has("error") && packet.optString("result").equals("ok", true)
val rejected = packet.has("error") || (packet.has("result") && !ok)
state.update { it.copy(busy = false, notice = if (ok) "confirmed" else if (rejected) "rejected" else "unknown") }
if (ok) { loadLists(); source.requestServerData() }
}
}
@Synchronized fun submit(packet: JSONObject): Boolean {
val current = state.value
if (!current.canWrite || source.isUserLogout || !source.connectionState.value) return false
val action = packet.optString("action")
val target = packet.optString("userid").ifBlank { packet.optString("meshid") }
val group = current.groups.firstOrNull { it.optString("_id") == target }
val user = current.users.firstOrNull { it.optString("_id") == target }
val permitted = when (action) {
"adduser" -> ProfilePermissions.manageUsers(current.site) && packet.optString("username").isNotBlank() && packet.optString("pass").isNotEmpty()
"edituser" -> ProfilePermissions.manageUsers(current.site) && user != null
"deleteuser" -> ProfilePermissions.manageUsers(current.site) && user != null && target != current.id
"createmesh" -> ProfilePermissions.createGroup(current.site) && packet.optString("meshname").isNotBlank()
"editmesh" -> group != null && ProfilePermissions.editGroup(current.site, current.rights(group))
"deletemesh" -> group != null && ProfilePermissions.deleteGroup(current.site, current.rights(group))
else -> false
}
if (!permitted) return false
pendingId = UUID.randomUUID().toString(); pendingAction = action
val payload = JSONObject(packet.toString()).put("responseid", pendingId)
if (socket?.send(payload.toString()) != true) { pendingId = ""; pendingAction = ""; return false }
state.update { it.copy(busy = true, notice = "sent") }
val epoch = generation
timer?.cancel(); timer = scope.launch { delay(20_000); synchronized(this@ProfileSession) {
if (epoch == generation && pendingId.isNotBlank()) {
pendingId = ""; pendingAction = ""; state.update { it.copy(busy = false, notice = "unknown") }
}
} }
return true
}
}
