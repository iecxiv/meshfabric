package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
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

internal enum class GeneralDeviceAction(val type:Int?) {WAKE(null),RESTART(3),SHUTDOWN(2),SLEEP(4)}
internal data class GeneralDeviceActionDraft(val action:GeneralDeviceAction,val mesh:String,val os:String)
internal fun generalDeviceActionAvailable(node:String,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>,action:GeneralDeviceAction):Boolean {
    val group=inventory.singleOrNull {it.first.id==meta.mesh} ?: return false
    if(group.first.mtype!=2) return false
    val current=inventory.flatMap {it.second}.singleOrNull {it.id==node} ?: return false
    if(current.meshid!=meta.mesh || group.second.count {it.id==node}!=1) return false
    return if(action==GeneralDeviceAction.WAKE) meta.rights and 64L!=0L else meta.rights and 262152L==262152L && current.conn and 1!=0
}
internal fun generalDeviceActionPacket(node:String,id:String,draft:GeneralDeviceActionDraft,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>):JsonObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && node.none {it.isISOControl()})
    require(id.isNotBlank() && id.length<=128 && id.none {it.isISOControl()} && draft.mesh==meta.mesh && draft.os==meta.os && generalDeviceActionAvailable(node,meta,inventory,draft.action))
    return JsonObject().apply {addProperty("action",if(draft.action==GeneralDeviceAction.WAKE) "wakedevices" else "poweraction");add("nodeids",JsonArray().apply {add(node)});addProperty("responseid",id);draft.action.type?.let {addProperty("actiontype",it)}}
}
internal fun generalDeviceActionAck(p:JsonObject,node:String,id:String,action:GeneralDeviceAction):String? {
    if(id.isBlank() || p.get("responseid")?.asString!=id || p.get("action")?.asString!=(if(action==GeneralDeviceAction.WAKE) "wakedevices" else "poweraction")) return null
    val target=p.get("nodeid")?.asString.orEmpty();if(target.isNotBlank() && target!=node) return null
    p.get("nodeids")?.let {if(!it.isJsonArray || it.asJsonArray.size()!=1 || it.asJsonArray[0].asString!=node) return null}
    if(p.has("error") || p.has("err")) return "rejected"
    return when(p.get("result")?.asString?.lowercase()) {"ok"->"accepted";"denied","error","forbidden","access denied"->"rejected";else->null}
}
internal data class GeneralDeviceActionsState(val meta:GeneralMeta?=null,val phase:String="loading",val code:String="")
internal class DeviceGeneralActionsEditor(private val source:MeshCentralClient?,private val node:String) {
    val state=MutableStateFlow(GeneralDeviceActionsState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false;private val actor=source?.userInfo?.value?.id.orEmpty()
    private var active=true;private var started=false;private var requested=false;private var attempted=false
    private var socket:WebSocket?=null;private var tag="";private var target:GeneralDeviceActionDraft?=null;private var sentAction:GeneralDeviceAction?=null;private var timer:Job?=null
    private val requestId=UUID.randomUUID().toString()
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && source.userInfo.value?.id==actor && actor.isNotBlank() && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun fail(code:String) {
        if(!active) return
        active=false;timer?.cancel();tag="";target=null;socket?.cancel();socket=null;jobs.cancel()
        state.value=GeneralDeviceActionsState(phase="error",code=if(attempted) "unconfirmed" else code)
    }
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceGeneralActionsEditor) {if(active) fail("timeout")}}}
    private fun read():Boolean {tag=UUID.randomUUID().toString();return send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})}
    @Synchronized fun start() {
        if(started || !active) return
        started=true
        val a=actor.split('/');val n=node.split('/')
        if(!same() || cookie.isBlank() || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1]) {fail("identity");return}
        deadline();jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceGeneralActionsEditor) {if(active && !same()) fail("identity")}}}
        try {socket=OkHttpHelper.createClient(tls).newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceGeneralActionsEditor) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceGeneralActionsEditor) {
                if(!active) return
                if(!same() || text.length>2_000_000) {fail("connection");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("invalid")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralActionsEditor) {fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralActionsEditor) {fail("connection")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralActionsEditor) {fail("connection")}}
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
                timer?.cancel();tag=""
                val draft=target
                if(draft==null) {state.value=GeneralDeviceActionsState(meta,"ready");return}
                val change=runCatching {generalDeviceActionPacket(node,requestId,draft,meta,source?.groupedDevices?.value.orEmpty())}.getOrNull() ?: run {fail("changed");return}
                target=null;attempted=true;sentAction=draft.action
                if(!send(change)) {fail("unconfirmed");return}
                state.value=GeneralDeviceActionsState(meta,"sent");deadline()
            }
            "wakedevices","poweraction"->if(attempted && state.value.phase=="sent") sentAction?.let {action->generalDeviceActionAck(p,node,requestId,action)?.let {phase->timer?.cancel();state.value=state.value.copy(phase=phase)}}
        }
    }
    @Synchronized fun submit(draft:GeneralDeviceActionDraft):Boolean {
        val meta=state.value.meta ?: return false
        if(!active || !same() || requested || attempted || state.value.phase!="ready") return false
        if(runCatching {generalDeviceActionPacket(node,requestId,draft,meta,source?.groupedDevices?.value.orEmpty())}.isFailure) {fail("changed");return false}
        requested=true;target=draft;state.value=state.value.copy(phase="checking");deadline()
        if(!read()) {fail("connection");return false}
        return true
    }
    @Synchronized fun close() {fail("closed")}
}
