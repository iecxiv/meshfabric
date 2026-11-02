package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject

internal sealed class ClipboardControlReply {
    data class Content(val nodeId: String, val text: String) : ClipboardControlReply()
    data class WriteResult(val nodeId: String, val success: Boolean) : ClipboardControlReply()
}

internal object ClipboardControlProtocol {
    fun cleanNodeId(value: String): String =
        value.removePrefix("node//").removePrefix("node/").trim()

    private fun string(json: JsonObject, key: String): String? {
        val value = json.get(key) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    fun decode(json: JsonObject, defaultNodeId: String? = null): ClipboardControlReply? {
        val action = string(json, "action")
        val type = if (action == "msg" || action == null) string(json, "type") else action
        if (type !in setOf("getclip", "clipboard", "setclip")) return null
        val node = cleanNodeId(string(json, "nodeid") ?: string(json, "node") ?: defaultNodeId ?: return null)
        if (node.isEmpty()) return null
        val successValue = json.get("success")
        val success = if (successValue?.isJsonPrimitive == true && successValue.asJsonPrimitive.isBoolean)
            successValue.asBoolean else null
        if (type == "setclip") return success?.let { ClipboardControlReply.WriteResult(node, it) }
        if (success == false) return null
        for (field in listOf("data", "text", "clip", "value")) {
            val text = string(json, field)
            if (text != null) return ClipboardControlReply.Content(node, text)
        }
        return null
    }

    fun command(type: String, nodeId: String, text: String? = null): JsonObject? {
        if (type != "getclip" && type != "setclip") return null
        val clean = cleanNodeId(nodeId)
        if (clean.isEmpty() || (type == "setclip" && text == null)) return null
        return JsonObject().apply {
            addProperty("action", "msg")
            addProperty("type", type)
            addProperty("nodeid", "node//$clean")
            if (type == "setclip") addProperty("data", text)
        }
    }
}

enum class ClipboardTransferState { IDLE, WAITING, RECEIVED, CONFIRMED, FAILED, TIMED_OUT }
