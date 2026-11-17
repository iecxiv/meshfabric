package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.util.UUID

internal const val DEVICE_NOTIFY_WEB_MASK=6L
internal fun deviceNotifyValue(user:JsonObject,actor:String,node:String):Long {
    val a=actor.split('/');val n=node.split('/')
    require(a.size==3 && a[0]=="user" && a[2].isNotBlank() && n.size==3 && n[0]=="node" && n[2].isNotBlank() && a[1]==n[1])
    require(user.get("_id")?.asString==actor)
    val map=user.get("notify") ?: return 0L
    require(map.isJsonObject)
    val raw=map.asJsonObject.get(node) ?: return 0L
    require(raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber)
    val value=raw.asDouble
    require(value.isFinite() && value%1.0==0.0 && value>=0.0 && value<=4294967295.0)
    return value.toLong()
}
internal fun deviceNotifyMerge(before:Long,selected:Long):Long {
    require(before in 0L..0xffffffffL && selected>=0L && selected and DEVICE_NOTIFY_WEB_MASK==selected)
    return (before and DEVICE_NOTIFY_WEB_MASK.inv()) or selected
}
internal fun deviceNotifyPacket(node:String,before:Long,selected:Long):JsonObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && !node.contains('\u0000'))
    val next=deviceNotifyMerge(before,selected);require(next!=before)
    return JsonObject().apply {addProperty("action","changeusernotify");addProperty("nodeid",node);addProperty("notify",next)}
}
internal data class DeviceNotifyState(val value:Long?=null,val phase:String="loading",val code:String="") {
    val canEdit:Boolean get()=value!=null && phase in setOf("ready","confirmed")
}
internal class DeviceNotificationEditor(private val source:MeshCentralClient?,private val node:String) {
    val state=MutableStateFlow(DeviceNotifyState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false
    private val actor=source?.userInfo?.value?.id.orEmpty()
    private var socket:WebSocket?=null;private var active=true;private var mode="initial-user";private var tag=""
    private var before=0L;private var target:Long?=null;private var sent=false
    private var timer:Job?=null;private var poll:Job?=null
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls && source.userInfo.value?.id==actor && actor.isNotBlank()
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    private fun user()=send(packet("userinfo"))
    private fun node():Boolean {tag=UUID.randomUUID().toString();return send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})}
    @Synchronized private fun fail(code:String) {
        timer?.cancel();poll?.cancel();mode="failed";target=null
        state.value=DeviceNotifyState(phase="error",code=if(sent) "unconfirmed" else code)
    }
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceNotificationEditor) {if(active) fail("timeout")}}}
    @Synchronized fun start() {
        if(!same() || cookie.isBlank()) {fail("disconnected");return}
        deadline()
        jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceNotificationEditor) {if(active && !same()) {fail("disconnected");socket?.cancel()}}}}
        try {socket=OkHttpHelper.createClient(tls).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceNotificationEditor) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceNotificationEditor) {
                if(!active || !same()) {fail("disconnected");return}
                if(text.length>2_000_000) {fail("invalid");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("invalid")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceNotificationEditor) {if(active) fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceNotificationEditor) {if(active) fail("connection");webSocket.close(code,"")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceNotificationEditor) {if(active) fail("connection")}}
        })} catch(_:Exception) {fail("connection")}
    }
    private fun receive(p:JsonObject) {
        when(p.get("action")?.asString) {
            "ping"->send(packet("pong"))
            "close","stopped"->fail("denied")
            "userinfo"->{
                val current=deviceNotifyValue(p.getAsJsonObject("userinfo"),actor,node)
                when(mode) {
                    "initial-user"->{before=current;mode="initial-node";if(!node()) fail("connection")}
                    "preflight-user"->{
                        if(current!=before) {fail("conflict");return}
                        mode="preflight-node";if(!node()) fail("connection")
                    }
                    "confirm"->if(current==target) {
                        timer?.cancel();poll?.cancel();mode="idle";sent=false;target=null
                        state.value=DeviceNotifyState(current,"confirmed")
                    }
                }
            }
            "getsysinfo"->if(mode in setOf("initial-node","preflight-node") && p.get("tag")?.asString==tag && p.get("nodeid")?.asString==node) {
                if(generalMeta(p,node,tag)==null) {fail("unverified");return}
                if(mode=="initial-node") {timer?.cancel();mode="idle";state.value=DeviceNotifyState(before,"ready")}
                else {
                    val next=requireNotNull(target)
                    val change=deviceNotifyPacket(node,before,next and DEVICE_NOTIFY_WEB_MASK)
                    sent=true;mode="confirm"
                    if(!send(change)) {fail("unconfirmed");return}
                    if(!user()) {fail("unconfirmed");return}
                    poll=jobs.launch {repeat(10) {delay(1000);synchronized(this@DeviceNotificationEditor) {if(active && mode=="confirm" && !user()) fail("unconfirmed")}}}
                }
            }
            "changeusernotify"->if(mode=="confirm" && (p.has("error") || p.has("err") || (p.has("result") && !p.get("result").asString.equals("ok",true)))) fail("denied")
        }
    }
    @Synchronized fun save(baseline:Long,selected:Long):Boolean {
        if(!active || !same() || !state.value.canEdit) return false
        if(state.value.value!=baseline) {fail("conflict");return false}
        val next=runCatching {deviceNotifyMerge(baseline,selected)}.getOrNull() ?: return false
        if(next==baseline) return false
        before=baseline;target=next;sent=false;mode="preflight-user"
        state.value=DeviceNotifyState(baseline,"saving");deadline()
        if(!user()) {fail("connection");return false}
        return true
    }
    @Synchronized fun close() {
        if(!active) return
        active=false;timer?.cancel();poll?.cancel();socket?.cancel();socket=null;jobs.cancel()
        state.value=DeviceNotifyState(phase="error",code=if(sent) "unconfirmed" else "disconnected")
    }
}
