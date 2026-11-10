package iecxiv.meshfabric.ui.management
import com.google.gson.JsonObject
internal sealed class TraceSourcesRead {
    data class Known(val sources:Set<String>):TraceSourcesRead()
    object Missing:TraceSourcesRead()
    object Invalid:TraceSourcesRead()
}
internal fun decodeTraceSources(packet:JsonObject):TraceSourcesRead {
    val action=packet.get("action")
    if(action==null || !action.isJsonPrimitive || !action.asJsonPrimitive.isString || action.asString!="traceinfo" || packet.has("error") || packet.has("err")) return TraceSourcesRead.Invalid
    if(packet.has("result")) {
        val result=packet.get("result")
        if(!result.isJsonPrimitive || !result.asJsonPrimitive.isString || !result.asString.equals("ok",true)) return TraceSourcesRead.Invalid
    }
    val value=packet.get("traceSources") ?: return TraceSourcesRead.Missing
    if(value.isJsonNull) return TraceSourcesRead.Known(emptySet())
    if(!value.isJsonArray || value.asJsonArray.size()>256) return TraceSourcesRead.Invalid
    val sources=linkedSetOf<String>()
    for(item in value.asJsonArray) {
        if(!item.isJsonPrimitive || !item.asJsonPrimitive.isString) return TraceSourcesRead.Invalid
        val name=item.asString
        if(name.isBlank() || name.length>100) return TraceSourcesRead.Invalid
        sources.add(name)
    }
    return TraceSourcesRead.Known(sources)
}
