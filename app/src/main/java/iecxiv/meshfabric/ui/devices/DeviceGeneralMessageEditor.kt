package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.util.UUID

internal const val GENERAL_MESSAGE_TIMEOUT=120000
internal data class GeneralMessageDraft(val title:String,val body:String,val mesh:String)
internal fun generalMessageTextValid(title:String,body:String)=
    title.isNotBlank() && title.length<=128 && title.none {it<' '} && Charsets.UTF_8.newEncoder().canEncode(title) &&
    body.isNotBlank() && body.length<=1024 && body.none {it<' ' && it!='\n' && it!='\r' && it!='\t'} && Charsets.UTF_8.newEncoder().canEncode(body)
internal fun generalMessageAvailable(node:String,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>):Boolean {
    if(meta.rights and 16392L!=16392L) return false
    val group=inventory.singleOrNull {it.first.id==meta.mesh} ?: return false
    if(group.first.mtype!=2) return false
    val current=inventory.flatMap {it.second}.singleOrNull {it.id==node} ?: return false
    return current.meshid==meta.mesh && current.conn and 1!=0 && group.second.count {it.id==node}==1
}
internal fun generalMessagePacket(node:String,id:String,draft:GeneralMessageDraft,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>):JsonObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && node.none {it.isISOControl()})
    require(id.isNotBlank() && id.length<=128 && draft.mesh==meta.mesh && generalMessageTextValid(draft.title,draft.body) && generalMessageAvailable(node,meta,inventory))
    return JsonObject().apply {addProperty("action","msg");addProperty("type","messagebox");addProperty("nodeid",node);addProperty("title",draft.title);addProperty("msg",draft.body);addProperty("timeout",GENERAL_MESSAGE_TIMEOUT);addProperty("responseid",id)}
}
internal fun generalMessageAck(p:JsonObject,node:String,id:String):String? {
    if(id.isBlank() || p.get("action")?.asString!="msg" || p.get("responseid")?.asString!=id) return null
    val type=p.get("type")?.asString.orEmpty();val target=p.get("nodeid")?.asString.orEmpty()
    if(type.isNotBlank() && type!="messagebox" || target.isNotBlank() && target!=node) return null
    if(p.has("error") || p.has("err")) return "rejected"
    return when(p.get("result")?.asString?.lowercase()) {"ok"->"accepted";"denied","forbidden","error","access denied"->"rejected";else->null}
}
internal data class GeneralMessageState(val meta:GeneralMeta?=null,val phase:String="loading",val code:String="")
internal class DeviceGeneralMessageEditor(private val source:MeshCentralClient?,private val node:String) {
    val state=MutableStateFlow(GeneralMessageState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false
    private val actor=source?.userInfo?.value?.id.orEmpty()
    private var active=true;private var requested=false;private var attempted=false
    private var socket:WebSocket?=null;private var tag="";private var target:GeneralMessageDraft?=null;private var timer:Job?=null
    private val requestId=UUID.randomUUID().toString()
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && actor.isNotBlank() && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun fail(code:String) {timer?.cancel();target=null;tag="";state.value=GeneralMessageState(phase="error",code=if(attempted) "unconfirmed" else code)}
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceGeneralMessageEditor) {if(active) fail("timeout")}}}
    private fun read():Boolean {tag=UUID.randomUUID().toString();return send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})}
    @Synchronized fun start() {
        val a=actor.split('/');val n=node.split('/')
        if(!same() || cookie.isBlank() || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1]) {fail("disconnected");return}
        deadline()
        jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceGeneralMessageEditor) {if(active && !same()) {fail("disconnected");socket?.cancel()}}}}
        try {socket=OkHttpHelper.createClient(tls).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceGeneralMessageEditor) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceGeneralMessageEditor) {
                if(!active) return
                if(!same()) {fail("disconnected");return}
                if(text.length>2_000_000) {fail("invalid");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("invalid")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralMessageEditor) {if(active) fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralMessageEditor) {if(active) fail("connection");webSocket.close(code,"")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralMessageEditor) {if(active) fail("connection")}}
        })} catch(_:Exception) {fail("connection")}
    }
    private fun receive(p:JsonObject) {
        when(p.get("action")?.asString) {
            "ping"->send(packet("pong"))
            "close","stopped"->fail("denied")
            "userinfo"->{
                if(p.getAsJsonObject("userinfo")?.get("_id")?.asString!=actor) {fail("identity");return}
                if(state.value.phase=="loading" && tag.isEmpty() && !read()) fail("connection")
            }
            "getsysinfo"->if(tag.isNotBlank() && p.get("tag")?.asString==tag && p.get("nodeid")?.asString==node && state.value.phase in setOf("loading","checking")) {
                val meta=generalMeta(p,node,tag) ?: run {fail("unverified");return}
                if(!generalMessageAvailable(node,meta,source?.groupedDevices?.value.orEmpty())) {fail("unavailable");return}
                timer?.cancel();tag=""
                val draft=target
                if(draft==null) {state.value=GeneralMessageState(meta,"ready");return}
                val change=runCatching {generalMessagePacket(node,requestId,draft,meta,source?.groupedDevices?.value.orEmpty())}.getOrNull() ?: run {fail("changed");return}
                target=null;attempted=true
                if(!send(change)) {fail("unconfirmed");return}
                state.value=GeneralMessageState(meta,"sent")
            }
            "msg"->if(attempted && state.value.phase in setOf("sent","accepted")) generalMessageAck(p,node,requestId)?.let {state.value=state.value.copy(phase=it)}
        }
    }
    @Synchronized fun submit(reviewed:GeneralMessageDraft):Boolean {
        val meta=state.value.meta ?: return false
        if(!active || !same() || requested || attempted || state.value.phase!="ready") return false
        if(runCatching {generalMessagePacket(node,requestId,reviewed,meta,source?.groupedDevices?.value.orEmpty())}.isFailure) return false
        requested=true;target=reviewed;state.value=state.value.copy(phase="checking");deadline()
        if(!read()) {fail("connection");return false}
        return true
    }
    @Synchronized fun close() {
        if(!active) return
        active=false;timer?.cancel();target=null;socket?.cancel();socket=null;jobs.cancel()
        state.value=GeneralMessageState(phase="error",code=if(attempted) "unconfirmed" else "disconnected")
    }
}
