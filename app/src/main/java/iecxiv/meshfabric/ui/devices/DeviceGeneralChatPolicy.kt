package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import java.net.URLEncoder

internal fun generalChatId(node:String,actor:String):String {
    fun valid(value:String,kind:String):Boolean {val p=value.split('/');return p.size==3 && p[0]==kind && p[2].isNotBlank() && value.length<=4096 && value.none {it.isISOControl()}}
    require(valid(node,"node") && valid(actor,"user") && node.split('/')[1]==actor.split('/')[1])
    return "meshmessenger/$node/$actor"
}
internal fun generalChatRelayPath(node:String,actor:String,token:String):String {
    require(token.isNotBlank() && token.length<=16384 && token.none {it.isISOControl()})
    fun encode(s:String)=URLEncoder.encode(s,"UTF-8")
    return "/meshrelay.ashx?id=${encode(generalChatId(node,actor))}&auth=${encode(token)}"
}
internal fun generalChatTextValid(value:String)=value.isNotBlank() && value.length<=4096 && value.none {it<' ' && it!='\n' && it!='\r' && it!='\t'} && Charsets.UTF_8.newEncoder().canEncode(value)
internal fun generalChatMarker(value:String):Boolean?=when(value) {"c"->false;"cr"->true;else->null}
internal fun generalChatCanSend(phase:String,text:String)=phase=="connected" && generalChatTextValid(text)
internal fun generalChatPacket(text:String):JsonObject {require(generalChatTextValid(text));return JsonObject().apply {addProperty("action","chat");addProperty("msg",text)}}
internal data class GeneralChatEntry(val id:String,val local:Boolean,val text:String,val status:String="received")
internal data class GeneralChatState(val meta:GeneralMeta?=null,val phase:String="loading",val code:String="",val recorded:Boolean=false,val entries:List<GeneralChatEntry> = emptyList())
internal fun generalChatHistory(entries:List<GeneralChatEntry>):List<GeneralChatEntry> {
    val bounded=entries.takeLast(100).toMutableList();var length=bounded.sumOf {it.text.length}
    while(length>64000 && bounded.isNotEmpty()) length-=bounded.removeAt(0).text.length
    return bounded
}
