package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.DeviceIpv4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Replies need an explicitly requested node or a known response token; anonymous replies are rejected. */
internal class DeviceNetworkInfoCache(private val now: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private data class Pending(val token: String, val started: Long)
    private val pending = mutableMapOf<String, Pending>()
    private val attempts = mutableMapOf<String, Long>()
    private val _addresses = MutableStateFlow<Map<String, List<DeviceIpv4>>>(emptyMap())
    val addresses = _addresses.asStateFlow()

    @Synchronized fun request(nodeId: String): JsonObject? {
        val key = DeviceNetworkIpv4.nodeKey(nodeId)
        if (key.isBlank()) return null
        val time = now()
        if (pending[key]?.let { time - it.started < 15_000L } == true) return null
        if (attempts[key]?.let { time - it < 60_000L } == true) return null
        val token = "network-ipv4-" + UUID.randomUUID().toString()
        pending[key] = Pending(token, time)
        attempts[key] = time
        return JsonObject().apply {
            addProperty("action", "getnetworkinfo")
            addProperty("nodeid", DeviceNetworkIpv4.fullNodeId(nodeId))
            addProperty("responseid", token)
        }
    }
    @Synchronized fun failed(request: JsonObject) {
        val key = DeviceNetworkIpv4.nodeKey(DeviceNetworkIpv4.text(request, "nodeid") ?: return)
        if (pending[key]?.token == DeviceNetworkIpv4.text(request, "responseid")) pending.remove(key)
    }
    @Synchronized fun receive(message: JsonObject): Boolean {
        if (DeviceNetworkIpv4.text(message, "action") != "getnetworkinfo") return false
        val response = DeviceNetworkIpv4.text(message, "responseid")
        val explicit = DeviceNetworkIpv4.text(message, "nodeid")
        if (message.has("nodeid") && explicit == null) return false
        if (message.has("responseid") && response == null) return false
        val key = if (response != null) {
            val entry = pending.entries.firstOrNull { it.value.token == response } ?: return false
            if (explicit != null && DeviceNetworkIpv4.nodeKey(explicit) != entry.key) return false
            entry.key
        } else {
            explicit?.let(DeviceNetworkIpv4::nodeKey) ?: return false
        }
        val request = pending[key] ?: return false
        if (now() - request.started >= 15_000L) { pending.remove(key); return false }
        pending.remove(key)
        _addresses.value = _addresses.value + (key to DeviceNetworkIpv4.decode(message))
        return true
    }
    @Synchronized fun invalidate() { attempts.clear() }
    @Synchronized fun connectionChanged() { pending.clear(); attempts.clear() }
    @Synchronized fun clear() {
        connectionChanged()
        _addresses.value = emptyMap()
    }
}
