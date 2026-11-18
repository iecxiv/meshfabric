package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.util.UUID

internal fun generalNotesValid(value:String)=value.length<=64000 && !value.contains('\u0000') && Charsets.UTF_8.newEncoder().canEncode(value)
internal fun generalNotesReply(p:JsonObject,node:String,request:String):String? {
    if(p.get("action")?.asString!="getNotes" || request.isBlank()) return null
    val id=p.get("id")?.asString.orEmpty();val rid=p.get("responseid")?.asString.orEmpty()
    if(id.isNotBlank() && id!=node || rid.isNotBlank() && rid!=request || id!=node && rid!=request) return null
    require(!p.has("error") && !p.has("err") && (!p.has("result") || p.get("result").asString.equals("ok",true)))
    val raw=requireNotNull(p.get("notes"))
    require(raw.isJsonNull || raw.isJsonPrimitive && raw.asJsonPrimitive.isString)
    return (if(raw.isJsonNull) "" else raw.asString).also {require(it.length<=64000)}
}
internal fun generalNotesPacket(node:String,request:String,value:String,rights:Long):JsonObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && node.none {it.isISOControl()})
    require(request.isNotBlank() && request.length<=128 && rights and 128L!=0L && generalNotesValid(value))
    return JsonObject().apply {addProperty("action","setNotes");addProperty("id",node);addProperty("responseid",request);addProperty("notes",value)}
}
internal data class GeneralNotesState(val original:String?=null,val phase:String="loading",val rights:Long?=null,val code:String="") {
    val canEdit:Boolean get()=original!=null && phase in setOf("ready","confirmed") && rights?.let {it and 128L!=0L}==true
}
internal class DeviceGeneralNotesEditor(private val source:MeshCentralClient?,private val node:String) {
    val state=MutableStateFlow(GeneralNotesState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false
    private val actor=source?.userInfo?.value?.id.orEmpty()
    private var socket:WebSocket?=null;private var active=true;private var mode="initial-user"
    private var metaTag="";private var readId="";private var writeId="";private var before="";private var target:String?=null;private var attempted=false
    private var rights:Long?=null;private var timer:Job?=null;private var poll:Job?=null
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && actor.isNotBlank() && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun fail(code:String) {
        timer?.cancel();poll?.cancel();mode="failed";target=null;metaTag="";readId="";rights=null
        state.value=state.value.copy(phase="error",rights=null,code=if(attempted) "unconfirmed" else code)
    }
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceGeneralNotesEditor) {if(active) fail("timeout")}}}
    private fun readMeta():Boolean {metaTag=UUID.randomUUID().toString();return send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",metaTag);addProperty("nodeinfo",true);addProperty("cache",true)})}
    private fun readNotes():Boolean {readId=UUID.randomUUID().toString();return send(packet("getNotes").apply {addProperty("id",node);addProperty("responseid",readId)})}
    @Synchronized fun start() {
        val a=actor.split('/');val n=node.split('/')
        if(!same() || cookie.isBlank() || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1]) {fail("disconnected");return}
        deadline()
        jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceGeneralNotesEditor) {if(active && !same()) {fail("disconnected");socket?.cancel()}}}}
        try {socket=OkHttpHelper.createClient(tls).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceGeneralNotesEditor) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceGeneralNotesEditor) {
                if(!active) return
                if(!same()) {fail("disconnected");return}
                if(text.length>2_000_000) {fail("invalid");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("invalid")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralNotesEditor) {if(active) fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralNotesEditor) {if(active) fail("connection");webSocket.close(code,"")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralNotesEditor) {if(active) fail("connection")}}
        })} catch(_:Exception) {fail("connection")}
    }
    private fun receive(p:JsonObject) {
        when(p.get("action")?.asString) {
            "ping"->send(packet("pong"))
            "close","stopped"->fail("denied")
            "userinfo"->{
                if(p.getAsJsonObject("userinfo")?.get("_id")?.asString!=actor) {fail("identity");return}
                if(mode=="initial-user") {mode="initial-meta";if(!readMeta()) fail("connection")}
            }
            "getsysinfo"->if(mode in setOf("initial-meta","preflight-meta") && metaTag.isNotBlank() && p.get("tag")?.asString==metaTag && p.get("nodeid")?.asString==node) {
                val meta=generalMeta(p,node,metaTag) ?: run {fail("unverified");return}
                rights=meta.rights;metaTag=""
                if(mode=="preflight-meta" && meta.rights and 128L==0L) {fail("denied");return}
                mode=if(mode=="initial-meta") "initial-notes" else "preflight-notes"
                if(!readNotes()) fail("connection")
            }
            "getNotes"->if(mode in setOf("initial-notes","preflight-notes","confirm")) {
                val notes=generalNotesReply(p,node,readId) ?: return
                when(mode) {
                    "initial-notes"->{timer?.cancel();mode="idle";state.value=GeneralNotesState(notes,"ready",rights)}
                    "preflight-notes"->{
                        if(notes!=before) {fail("conflict");return}
                        val change=generalNotesPacket(node,writeId,requireNotNull(target),requireNotNull(rights))
                        attempted=true;mode="confirm"
                        if(!send(change) || !readNotes()) {fail("unconfirmed");return}
                        poll=jobs.launch {repeat(10) {delay(1000);synchronized(this@DeviceGeneralNotesEditor) {if(active && mode=="confirm" && !readNotes()) fail("unconfirmed")}}}
                    }
                    "confirm"->if(notes==target) {
                        timer?.cancel();poll?.cancel();target=null;attempted=false;mode="idle"
                        state.value=GeneralNotesState(notes,"confirmed",rights)
                    }
                }
            }
            "setNotes"->if(mode=="confirm" && p.get("responseid")?.asString==writeId && (p.has("error") || p.has("err") || p.has("result") && !p.get("result").asString.equals("ok",true))) fail("denied")
        }
    }
    @Synchronized fun save(baseline:String,draft:String):Boolean {
        if(!active || !same() || !state.value.canEdit || !generalNotesValid(draft) || baseline==draft) return false
        if(state.value.original!=baseline) {fail("conflict");return false}
        before=baseline;target=draft;attempted=false;writeId=UUID.randomUUID().toString();mode="preflight-meta"
        state.value=state.value.copy(phase="saving",code="");deadline()
        if(!readMeta()) {fail("connection");return false}
        return true
    }
    @Synchronized fun close() {
        if(!active) return
        active=false;timer?.cancel();poll?.cancel();target=null;socket?.cancel();socket=null;jobs.cancel()
        state.value=state.value.copy(phase="error",rights=null,code=if(attempted) "unconfirmed" else "disconnected")
    }
}
