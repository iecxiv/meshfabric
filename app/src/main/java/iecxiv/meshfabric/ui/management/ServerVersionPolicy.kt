package iecxiv.meshfabric.ui.management

import com.google.gson.Gson
import com.google.gson.JsonParser

internal data class ServerVersionTags(val current:String?,val stable:String?,val latest:String?)
internal fun serverUpdateAllowed(rights:Long)=(rights and 16L)!=0L
internal fun validServerVersion(value:String):Boolean=value.length in 5..100 && Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?").matches(value)
internal fun serverVersionTags(raw:String):ServerVersionTags?=runCatching {
    val tags=JsonParser.parseString(raw).asJsonObject.getAsJsonObject("tags") ?: return null
    fun version(key:String):String? {
        val value=tags.get(key) ?: return null
        if(!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString.takeIf(::validServerVersion)
    }
    ServerVersionTags(version("current"),version("stable"),version("latest"))
}.getOrNull()
internal fun mayUpdateServer(tags:ServerVersionTags,version:String)=tags.current!=null && validServerVersion(version) && version!=tags.current && (version==tags.stable || version==tags.latest)
internal fun serverUpdateRequest(version:String):String {
    require(validServerVersion(version))
    return Gson().toJson(linkedMapOf("action" to "serverupdate","version" to version))
}
