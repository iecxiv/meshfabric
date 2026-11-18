package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import iecxiv.meshfabric.ui.management.RemoteActionFeedback
import iecxiv.meshfabric.ui.management.commandInterpreters
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class GeneralRunDraft(val command:String,val interpreter:String,val os:String)
internal fun generalRunTextValid(value:String)=value.isNotBlank() && value.length<=64000 && !value.contains('\u0000') && Charsets.UTF_8.newEncoder().canEncode(value)
internal fun generalRunAvailable(node:String,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>):Boolean {
    if(meta.rights and 131072L==0L) return false
    val group=inventory.singleOrNull {it.first.id==meta.mesh && it.first.mtype==2} ?: return false
    val matches=inventory.flatMap {it.second}.filter {it.id==node}
    val current=matches.singleOrNull() ?: return false
    return current.meshid==group.first.id && current.conn and 1!=0 && group.second.count {it.id==node}==1
}
internal fun generalRunPacket(node:String,id:String,draft:GeneralRunDraft,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>):JSONObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && node.none {it.isISOControl()})
    require(id.isNotBlank() && id.length<=128 && generalRunTextValid(draft.command) && draft.os==meta.os && generalRunAvailable(node,meta,inventory))
    val interpreter=requireNotNull(commandInterpreters(meta.os).singleOrNull {it.key==draft.interpreter})
    return JSONObject().put("action","runcommands").put("nodeids",JSONArray().put(node)).put("type",interpreter.type).put("cmds",draft.command).put("runAsUser",0).put("reply",true).put("responseid",id)
}
internal data class GeneralRunState(val meta:GeneralMeta?=null,val phase:String="loading",val code:String="")
internal class DeviceGeneralRunEditor(private val source:MeshCentralClient?,private val node:String,private val title:String) {
    val state=MutableStateFlow(GeneralRunState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false
    private val actor=source?.userInfo?.value?.id.orEmpty()
    private var active=true;private var requested=false;private var attempted=false
    private var socket:WebSocket?=null;private var tag="";private var target:GeneralRunDraft?=null;private var timer:Job?=null
    private var approvedMeta:GeneralMeta?=null
    private val runId=UUID.randomUUID().toString()
    val feedback=RemoteActionFeedback(jobs,sendPacket={p->
        val ids=p.optJSONArray("nodeids")
        active && same() && p.optString("action")=="runcommands" && ids?.length()==1 && ids.optString(0)==node && p.optString("responseid")==runId &&
            approvedMeta?.let {generalRunAvailable(node,it,source?.groupedDevices?.value.orEmpty())}==true && socket?.send(p.toString())==true
    },nativeClipboard={_,_->null})
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && actor.isNotBlank() && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun fail(code:String) {
        timer?.cancel();target=null;tag="";approvedMeta=null;feedback.interrupted()
        state.value=GeneralRunState(phase="error",code=if(attempted) "unconfirmed" else code)
    }
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceGeneralRunEditor) {if(active) fail("timeout")}}}
    private fun read():Boolean {tag=UUID.randomUUID().toString();return send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})}
    @Synchronized fun start() {
        val a=actor.split('/');val n=node.split('/')
        if(!same() || cookie.isBlank() || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1]) {fail("disconnected");return}
        deadline()
        jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceGeneralRunEditor) {if(active && !same()) {fail("disconnected");socket?.cancel()}}}}
        try {socket=OkHttpHelper.createClient(tls).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceGeneralRunEditor) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceGeneralRunEditor) {
                if(!active) return
                if(!same()) {fail("disconnected");return}
                if(text.length>2_000_000) {fail("invalid");return}
                runCatching {
                    val p=JsonParser.parseString(text).asJsonObject
                    val action=p.get("action")?.asString
                    if(action=="runcommands" || action=="msg" && p.get("type")?.asString=="runcommands") feedback.receive(JSONObject(text)) else receive(p)
                }.onFailure {fail("invalid")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralRunEditor) {if(active) fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralRunEditor) {if(active) fail("connection");webSocket.close(code,"")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralRunEditor) {if(active) fail("connection")}}
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
                if(!generalRunAvailable(node,meta,source?.groupedDevices?.value.orEmpty())) {fail("unavailable");return}
                timer?.cancel();tag=""
                val draft=target
                if(draft==null) {state.value=GeneralRunState(meta,"ready");return}
                val change=runCatching {generalRunPacket(node,runId,draft,meta,source?.groupedDevices?.value.orEmpty())}.getOrNull() ?: run {fail("changed");return}
                target=null;approvedMeta=meta;attempted=true
                if(!feedback.submit(title,change)) {fail("unconfirmed");return}
                state.value=GeneralRunState(meta,"sent")
            }
        }
    }
    @Synchronized fun submit(reviewed:GeneralRunDraft):Boolean {
        val meta=state.value.meta ?: return false
        if(!active || !same() || requested || attempted || state.value.phase!="ready") return false
        if(runCatching {generalRunPacket(node,runId,reviewed,meta,source?.groupedDevices?.value.orEmpty())}.isFailure) return false
        requested=true;target=reviewed;state.value=state.value.copy(phase="checking");deadline()
        if(!read()) {fail("connection");return false}
        return true
    }
    @Synchronized fun close() {
        if(!active) return
        active=false;timer?.cancel();target=null;approvedMeta=null;socket?.cancel();socket=null;feedback.interrupted();jobs.cancel()
        state.value=GeneralRunState(phase="error",code=if(attempted) "unconfirmed" else "disconnected")
    }
}
