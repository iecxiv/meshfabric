package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.DeviceInventoryState
import java.util.Locale

internal data class InventoryValue(val path: String, val value: String)
internal data class InventorySection(val key: String, val rows: List<InventoryValue>)

/** Preserve source paths and scalar values, including false and zero; never infer units. */
internal fun inventorySections(state: DeviceInventoryState): List<InventorySection> {
    val groups = linkedMapOf<String, MutableList<InventoryValue>>()
    fun add(group: String, path: String, element: JsonElement?, depth: Int = 0) {
        if (element == null || element.isJsonNull || depth > 40) return
        when {
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                add(group, if (path.isEmpty()) key else "$path / $key", value, depth + 1)
            }
            element.isJsonArray -> element.asJsonArray.forEachIndexed { index, value ->
                add(group, "$path [${index + 1}]", value, depth + 1)
            }
            element.isJsonPrimitive -> {
                val value = element.asString
                if (value.isNotBlank()) groups.getOrPut(group) { mutableListOf() }.add(InventoryValue(path, value))
            }
        }
    }
    fun hardware(element: JsonElement?, path: String = "") {
        if (element == null || !element.isJsonObject) { add("extra", path, element); return }
        element.asJsonObject.entrySet().forEach { (key, value) ->
            val next = if (path.isEmpty()) key else "$path / $key"
            val lower = key.lowercase(Locale.ROOT)
            val group = when {
                lower.startsWith("bios") -> "bios"
                lower.startsWith("board") || lower.startsWith("baseboard") -> "board"
                lower.startsWith("cpu") || lower.contains("processor") -> "cpu"
                lower.startsWith("gpu") || lower.contains("video") -> "gpu"
                lower.contains("tpm") -> "tpm"
                lower.contains("memory") || lower == "ram" -> "memory"
                lower.contains("volume") || lower.contains("disk") || lower.contains("storage") -> "storage"
                lower.startsWith("product") || lower.startsWith("chassis") -> "system"
                lower in setOf("osinfo", "arch", "kernel_release", "kernel_build", "lastbootuptime") -> "os"
                lower == "agent" -> "agent"
                else -> "extra"
            }
            if (lower in setOf("windows", "linux", "identifiers", "darwin", "mobile") && value.isJsonObject)
                hardware(value, next)
            else add(group, next, value)
        }
    }
    state.packets["getsysinfo"]?.let { packet ->
        hardware(packet.get("hardware"))
        packet.get("node")?.takeIf { it.isJsonObject }?.asJsonObject?.let { node ->
            listOf("rname", "host", "osdesc", "lastbootuptime").forEach { add("os", it, node.get(it)) }
            listOf("agent", "agct", "firstconnect", "lastconnect").forEach { add("agent", it, node.get(it)) }
        }
        listOf("time", "updateTime", "timestamp").forEach { add("metadata", it, packet.get(it)) }
    }
    state.packets["getnetworkinfo"]?.let { packet ->
        // Includes netif and netif2, DNS and any additional platform-specific network fields.
        packet.entrySet().filter { it.key !in envelopeKeys }.forEach { (key, value) -> add("network", key, value) }
    }
    state.packets["lastconnect"]?.let { packet ->
        packet.entrySet().filter { it.key !in envelopeKeys }.forEach { (key, value) -> add("connection", key, value) }
    }
    val order = listOf("os", "agent", "connection", "network", "bios", "board", "system", "cpu", "gpu", "tpm", "memory", "storage", "extra", "metadata")
    return order.mapNotNull { key -> groups[key]?.takeIf { it.isNotEmpty() }?.let { InventorySection(key, it.distinct()) } }
}

private val envelopeKeys = setOf("action", "nodeid", "responseid", "_id", "type", "domain", "result", "error")

internal fun inventoryTitle(key: String, spanish: Boolean): String {
    val titles = if (spanish) mapOf("os" to "Sistema operativo", "agent" to "Agente Mesh", "connection" to "Última conexión",
        "network" to "Redes y DNS", "bios" to "BIOS", "board" to "Placa base", "system" to "Sistema",
        "cpu" to "CPU", "gpu" to "GPU", "tpm" to "TPM", "memory" to "Memoria", "storage" to "Almacenamiento",
        "extra" to "Otros datos del inventario", "metadata" to "Fecha del inventario (servidor)")
    else mapOf("os" to "Operating system", "agent" to "Mesh Agent", "connection" to "Last connection",
        "network" to "Networks and DNS", "bios" to "BIOS", "board" to "Motherboard", "system" to "System",
        "cpu" to "CPU", "gpu" to "GPU", "tpm" to "TPM", "memory" to "Memory", "storage" to "Storage",
        "extra" to "Additional inventory", "metadata" to "Inventory timestamp (server)")
    return titles[key] ?: key
}
