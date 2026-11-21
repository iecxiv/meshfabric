package iecxiv.meshfabric.data.remote
import com.google.gson.JsonObject
internal object NodeEventConnectivity {
    private fun text(json: JsonObject, key: String): String? = json.get(key)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }
    fun action(json: JsonObject): String = text(json,"action").orEmpty()
    fun nodeId(json: JsonObject): String? {
        val node=json.get("node")
        return text(json,"nodeid") ?: node?.takeIf { it.isJsonObject }?.asJsonObject?.let { text(it,"_id") }
            ?: text(json,"node")
    }
    fun keys(id: String): List<String> {
        val raw=id.trim()
        val clean=when {
            raw.startsWith("node//") -> raw.removePrefix("node//")
            raw.startsWith("node/") -> raw.removePrefix("node/")
            else -> raw
        }
        return listOf(raw,clean,"node//$clean","node/$clean").distinct()
    }
    fun connection(json: JsonObject): Int? {
        val action=action(json)
        if (action !in setOf("nodeconnect","nodedisconnect","changenode")) return null
        val nested=json.get("node")?.takeIf { it.isJsonObject }?.asJsonObject
        val value=if(json.has("conn")) json.get("conn") else nested?.get("conn")
        if(value==null) return if(action=="nodedisconnect") 0 else null
        if(!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) return null
        return value.asString.toIntOrNull()?.takeIf { it>=0 }
    }
    fun becameReachable(before: Int, after: Int, action: String): Boolean =
        action!="nodedisconnect" && after>0 && (before==0 || ((before and 1)==0 && (after and 1)!=0))
}
