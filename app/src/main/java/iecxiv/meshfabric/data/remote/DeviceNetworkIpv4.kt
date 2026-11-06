package iecxiv.meshfabric.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.DeviceIpv4

/** Only interface address fields are eligible; never node.ip, gateways, netmasks or agent addresses. */
internal object DeviceNetworkIpv4 {
    fun nodeKey(id: String): String = id.trim().removePrefix("node/").removePrefix("/")
    fun fullNodeId(id: String): String = id.trim().let { if (it.startsWith("node/")) it else "node//$it" }
    fun text(objectValue: JsonObject, field: String): String? = objectValue.get(field)?.let {
        if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else null
    }
    private fun objectValue(value: JsonElement?): JsonObject? = runCatching {
        when {
            value == null || value.isJsonNull -> null
            value.isJsonObject -> value.asJsonObject
            value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                JsonParser.parseString(value.asString).takeIf { it.isJsonObject }?.asJsonObject
            else -> null
        }
    }.getOrNull()
    fun validAddress(value: String): String? {
        val parts = value.trim().split('.')
        if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || it.any { c -> c !in '0'..'9' } }) return null
        val octets = parts.map { it.toInt() }
        if (octets.any { it !in 0..255 } || octets[0] == 0 || octets[0] == 127 || octets[0] >= 224) return null
        return octets.joinToString(".")
    }
    fun decode(message: JsonObject): List<DeviceIpv4> {
        val modern = objectValue(message.get("netif2"))
        val legacy = objectValue(message.get("netif"))
        val interfaces = modern?.takeIf { it.entrySet().isNotEmpty() } ?: legacy ?: modern ?: return emptyList()
        val namesByAddress = linkedMapOf<String, MutableSet<String>>()
        for ((name, records) in interfaces.entrySet().sortedBy { it.key }) {
            if (name.equals("lo", true) || name.matches(Regex("lo[0-9]+", RegexOption.IGNORE_CASE))) continue
            val entries = when {
                records.isJsonArray -> records.asJsonArray.toList()
                records.isJsonObject -> listOf(records)
                else -> emptyList()
            }
            for (record in entries) {
                if (!record.isJsonObject) continue
                val entry = record.asJsonObject
                val internal = entry.get("internal")
                if (internal?.isJsonPrimitive == true && internal.asString.equals("true", true)) continue
                val address = text(entry, "address")?.let(::validAddress) ?: continue
                namesByAddress.getOrPut(address) { sortedSetOf() }.add(name)
            }
        }
        return namesByAddress.map { (address, names) -> DeviceIpv4(names.joinToString("/"), address) }
    }
}
