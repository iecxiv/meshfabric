package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.util.UUID

internal data class GeneralMeta(val name:String,val tags:List<String>,val rights:Long,val os:String,val mesh:String)
internal data class GeneralDraft(val name:String,val tags:List<String>)
internal data class GeneralEditorState(val meta:GeneralMeta?=null,val phase:String="loading",val code:String="") {
    val canEdit:Boolean get()=meta!=null && meta.rights and 4L!=0L && phase in setOf("ready","confirmed")
}
internal fun generalDraft(name:String,tags:String):GeneralDraft {
    require(name.isNotBlank() && name.length<=256 && name.none { it<' ' })
    require(tags.length<=4096 && tags.none { it<' ' && it!='\n' })
    val values=tags.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    require(values.all { it.length<64 })
    return GeneralDraft(name,values)
}
internal fun generalMeta(packet:JsonObject,nodeId:String,tag:String):GeneralMeta?=runCatching {
    require(packet.get("action")?.asString=="getsysinfo" && packet.get("tag")?.asString==tag)
    require(packet.get("nodeid")?.asString==nodeId && packet.get("noinfo")?.asBoolean!=true)
    val node=packet.getAsJsonObject("node")
    require(node.get("_id")?.asString==nodeId)
    val rights=packet.get("rights")
    require(rights!=null && rights.isJsonPrimitive && rights.asJsonPrimitive.isNumber)
    val value=rights.asDouble
    require(value.isFinite() && value%1.0==0.0 && value>=-1 && value<=4294967295.0)
    val tags=node.get("tags")
    require(tags==null || tags.isJsonArray)
    val list=tags?.asJsonArray?.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString);it.asString }?.distinct()?.sorted().orEmpty()
    GeneralMeta(node.get("name")?.asString.orEmpty(),list,value.toLong(),node.get("osdesc")?.asString.orEmpty(),node.get("meshid")?.asString.orEmpty())
}.getOrNull()
internal fun generalChange(nodeId:String,responseId:String,before:GeneralMeta,after:GeneralDraft):JsonObject {
    require(before.rights and 4L!=0L)
    require(before.name!=after.name || before.tags.toSet()!=after.tags.toSet())
    return JsonObject().apply {
        addProperty("action","changedevice");addProperty("nodeid",nodeId);addProperty("responseid",responseId)
        if(before.name!=after.name) addProperty("name",after.name)
        if(before.tags.toSet()!=after.tags.toSet()) add("tags",JsonArray().apply { after.tags.forEach { add(it) } })
    }
}
internal fun generalMatches(meta:GeneralMeta,draft:GeneralDraft)=meta.name==draft.name && meta.tags.toSet()==draft.tags.toSet()

internal class DeviceGeneralEditor(private val source:MeshCentralClient?,private val node:MeshNode,private val scope:CoroutineScope) {
    val state=MutableStateFlow(GeneralEditorState())
    private val url=source?.serverUrl.orEmpty()
    private val cookie=source?.authCookie.orEmpty()
    private val tls=source?.ignoreSsl ?: false
    private var socket:WebSocket?=null
    private var active=true
    private var authenticated=false
    private var readTag=""
    private var writeTag=""
    private var before:GeneralMeta?=null
    private var target:GeneralDraft?=null
    private var sent=false
    private var poll:Job?=null
    private var deadline:Job?=null
    private var identityWatch:Job?=null
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply { addProperty("action",action) }
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun fail(code:String) {
        poll?.cancel();deadline?.cancel();target=null;readTag="";writeTag=""
        state.value=state.value.copy(phase="error",code=code)
    }
    private fun timeout() {
        deadline?.cancel()
        deadline=scope.launch { delay(25_000);synchronized(this@DeviceGeneralEditor) { if(active) fail(if(sent) "unconfirmed" else "timeout") } }
    }
    private fun read():Boolean {
        readTag=UUID.randomUUID().toString()
        return send(packet("getsysinfo").apply { addProperty("nodeid",node.id);addProperty("tag",readTag);addProperty("nodeinfo",true);addProperty("cache",true) })
    }
    @Synchronized fun start() {
        if(!same()) { fail("disconnected");return }
        timeout()
        identityWatch=scope.launch { while(isActive) { delay(500);synchronized(this@DeviceGeneralEditor) { if(active && !same()) close() } } }
        try {
            socket=OkHttpHelper.createClient(tls).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) { synchronized(this@DeviceGeneralEditor) {
                    if(!active || !same()) { webSocket.cancel();return }
                    val auth=packet("urlargs").apply { add("args",JsonObject().apply { addProperty("auth",source!!.extractRawToken(cookie)) }) }
                    if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("send")
                } }
                override fun onMessage(webSocket:WebSocket,text:String) { synchronized(this@DeviceGeneralEditor) {
                    if(!active || !same()) { close();return }
                    if(text.length>2_000_000) { fail("oversize");return }
                    val p=runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
                    runCatching { receive(p) }.onFailure { fail("invalid") }
                } }
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { synchronized(this@DeviceGeneralEditor) { if(active) fail(if(sent) "unconfirmed" else "connection") } }
                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { synchronized(this@DeviceGeneralEditor) { if(active) fail(if(sent) "unconfirmed" else "connection");webSocket.close(code,"") } }
                override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { synchronized(this@DeviceGeneralEditor) { if(active) fail(if(sent) "unconfirmed" else "connection") } }
            })
        } catch(_:Exception) { fail("connection") }
    }
    private fun receive(p:JsonObject) {
        when(p.get("action")?.asString) {
            "ping"->send(packet("pong"))
            "close","stopped"->fail("denied")
            "userinfo"->if(!authenticated && !p.getAsJsonObject("userinfo")?.get("_id")?.asString.isNullOrBlank()) { authenticated=true;if(!read()) fail("send") }
            "changedevice"->if(p.get("responseid")?.asString==writeTag) {
                if(p.has("error") || p.get("result")?.asString!="ok") fail("denied")
            }
            "getsysinfo"->{
                if(p.get("tag")?.asString!=readTag || p.get("nodeid")?.asString!=node.id) return
                val meta=generalMeta(p,node.id,readTag) ?: run { fail("unverified");return }
                val draft=target
                if(draft==null) { deadline?.cancel();state.value=GeneralEditorState(meta,"ready");return }
                if(!sent) {
                    val baseline=before!!
                    if(meta.rights and 4L==0L) { fail("denied");return }
                    if(meta.name!=baseline.name || meta.tags.toSet()!=baseline.tags.toSet()) { fail("conflict");return }
                    val change=generalChange(node.id,writeTag,meta,draft)
                    sent=true
                    if(!send(change)) { fail("unconfirmed");return }
                    poll=scope.launch { repeat(10) { delay(1000);synchronized(this@DeviceGeneralEditor) { if(active && target!=null && !read()) fail("unconfirmed") } } }
                } else if(generalMatches(meta,draft)) {
                    poll?.cancel();deadline?.cancel();target=null
                    state.value=GeneralEditorState(meta,"confirmed")
                    source?.requestServerData()
                }
            }
        }
    }
    @Synchronized fun save(draft:GeneralDraft) {
        if(!state.value.canEdit || !authenticated || !same()) { fail("denied");return }
        before=state.value.meta!!
        if(generalMatches(before!!,draft)) return
        target=draft;sent=false;writeTag=UUID.randomUUID().toString()
        state.value=state.value.copy(phase="saving",code="")
        timeout()
        if(!read()) fail("send")
    }
    @Synchronized fun close() {
        if(!active) return
        active=false;poll?.cancel();deadline?.cancel();identityWatch?.cancel();socket?.cancel();socket=null
        state.value=state.value.copy(meta=if(same()) state.value.meta else null,phase="error",code=if(sent && target!=null) "unconfirmed" else "disconnected")
        target=null
    }
}
