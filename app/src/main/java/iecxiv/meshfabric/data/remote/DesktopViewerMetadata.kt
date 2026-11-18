package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject

internal fun desktopViewerCount(metadata: JsonObject): Int? {
    val users = metadata.get("users")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    var total = 0L
    for ((_, value) in users.entrySet()) {
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        val count = value.asString.toLongOrNull() ?: return null
        if (count !in 1L..Int.MAX_VALUE.toLong()) return null
        total += count
        if (total > Int.MAX_VALUE) return null
    }
    return total.takeIf { it > 0L }?.toInt()
}
