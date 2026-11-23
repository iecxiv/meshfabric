package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject

internal fun desktopConsoleMessage(json: JsonObject): String? {
    val value = json.get("msg") ?: return null
    if (!value.isJsonPrimitive) return null
    return value.asString.trim().take(500).ifEmpty { null }
}
