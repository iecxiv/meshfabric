package iecxiv.meshfabric.ui.management

import org.json.JSONArray
import org.json.JSONObject

internal fun accountServerInfo(packet:JSONObject):JSONObject =
    JSONObject((packet.optJSONObject("serverinfo") ?: packet).toString())

internal fun webNoticeNumber(value:Any?):Long? = when(value) {
    is String -> value.takeIf { it.matches(Regex("[0-9]{1,10}")) }?.toLongOrNull()
    is Number -> value.toDouble().takeIf { it.isFinite() && it >= 0 && it <= 4294967295.0 && it % 1.0 == 0.0 }?.toLong()
    else -> null
}?.takeIf { it in 0..0xffffffffL }

internal fun webNoticeState(packet:JSONObject):JSONObject? {
    val event=(if(packet.optString("action")=="event") packet.optJSONObject("event") else packet) ?: return null
    if(event.optString("action")!="userWebState") return null
    val value=event.opt("state")
    val text=when(value) { is String->value; is JSONObject->value.toString(); else->return null }
    if(text.length>10000) return null
    return runCatching { JSONObject(text) }.getOrNull()?.takeIf { webNoticeMask(it)!=null }
}

internal fun webNoticeMask(state:JSONObject):Long? =
    if(!state.has("notifications")) 0L else webNoticeNumber(state.opt("notifications"))

internal fun webNoticeOptions(mask:Long)=AccountNoticeOptions(
    sound=mask and 1L!=0L,groupName=mask and 16L!=0L,
    connections=mask and 2L!=0L,disconnections=mask and 4L!=0L,amt=mask and 8L!=0L)

internal fun webNoticeFlags(options:AccountNoticeOptions):Long =
    (if(options.sound) 1L else 0L) or (if(options.connections) 2L else 0L) or
    (if(options.disconnections) 4L else 0L) or (if(options.amt) 8L else 0L) or
    (if(options.groupName) 16L else 0L)

internal fun mergeWebNoticeState(state:JSONObject,before:Long,options:AccountNoticeOptions):JSONObject? {
    val previous=webNoticeMask(state) ?: return null
    if(previous!=before) return null
    val updated=(previous and 31L.inv()) or webNoticeFlags(options)
    val result=JSONObject(state.toString()).put("notifications",updated.toString())
    return result.takeIf { it.toString().length<=10000 }
}

internal fun webNoticeKind(packet:JSONObject)=if(packet.has("amtMessage") && !packet.isNull("amtMessage")) "amt" else "general"
internal fun noticeNodeKey(id:String)=id.removePrefix("node//").removePrefix("node/").trim()

internal fun noticeConnectivity(event:JSONObject):Pair<String,Int>? {
    val action=event.optString("action")
    if(action !in setOf("changenode","nodeconnect","nodedisconnect")) return null
    val node=event.optJSONObject("node")
    val id=event.optString("nodeid").ifBlank { node?.optString("_id").orEmpty() }.ifBlank { (event.opt("node") as? String).orEmpty() }
    val key=noticeNodeKey(id)
    if(key.isBlank()) return null
    val value=when {
        node?.has("conn")==true -> node.opt("conn")
        event.has("conn") -> event.opt("conn")
        action=="nodedisconnect" -> 0
        else -> return null
    }
    val number=(value as? Number)?.toDouble() ?: return null
    if(!number.isFinite() || number<0 || number>Int.MAX_VALUE || number%1.0!=0.0) return null
    return key to number.toInt()
}

internal fun noticeSnapshotNodes(packet:JSONObject):List<JSONObject> {
    val raw=packet.opt("nodes")
    fun records(a:JSONArray)=(0 until a.length()).mapNotNull { a.optJSONObject(it) }
    return when(raw) {
        is JSONArray -> records(raw)
        is JSONObject -> raw.keys().asSequence().flatMap { key ->
            when(val value=raw.opt(key)) {
                is JSONArray -> records(value).asSequence()
                is JSONObject -> sequenceOf(value)
                else -> emptySequence()
            }
        }.toList()
        else -> emptyList()
    }
}
