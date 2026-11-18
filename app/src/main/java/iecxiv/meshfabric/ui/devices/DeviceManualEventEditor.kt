package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.net.URLEncoder
import java.util.UUID

internal fun manualEventEncoded(text:String):String {
    require(text.isNotBlank() && text.length<=1024 && text.none {it<' ' && it!='\n' && it!='\r' && it!='\t'} && Charsets.UTF_8.newEncoder().canEncode(text))
    return URLEncoder.encode(text,"UTF-8").replace("+","%20").replace("%21","!").replace("%27","'").replace("%28","(").replace("%29",")").replace("%7E","~").also {require(it.length<=4096)}
}
internal fun manualEventPacket(node:String,text:String,rights:Long):JsonObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && node.none {it.isISOControl()})
    require(rights and 4L!=0L)
    return JsonObject().apply {addProperty("action","setDeviceEvent");addProperty("nodeid",node);addProperty("msg",manualEventEncoded(text))}
}
internal data class ManualEventState(val phase:String="loading",val code:String="",val rights:Long?=null) {
    val canSubmit:Boolean get()=phase=="ready" && rights?.let {it and 4L!=0L}==true
}
internal class DeviceManualEventEditor(private val source:MeshCentralClient?,private val node:String) {
    val state=MutableStateFlow(ManualEventState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false
    private val actor=source?.userInfo?.value?.id.orEmpty()
    private var active=true;private var attempted=false;private var requested=false
    private var socket:WebSocket?=null;private var tag="";private var target:String?=null;private var timer:Job?=null
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && actor.isNotBlank() && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun fail(code:String) {timer?.cancel();target=null;tag="";state.value=ManualEventState("error",if(attempted) "unconfirmed" else code)}
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceManualEventEditor) {if(active) fail("timeout")}}}
    private fun read():Boolean {tag=UUID.randomUUID().toString();return send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})}
    @Synchronized fun start() {
        val a=actor.split('/');val n=node.split('/')
        if(!same() || cookie.isBlank() || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1]) {fail("disconnected");return}
        deadline()
        jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceManualEventEditor) {if(active && !same()) {fail("disconnected");socket?.cancel()}}}}
        try {socket=OkHttpHelper.createClient(tls).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceManualEventEditor) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceManualEventEditor) {
                if(!active) return
                if(!same()) {fail("disconnected");return}
                if(text.length>2_000_000) {fail("invalid");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("invalid")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceManualEventEditor) {if(active) fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceManualEventEditor) {if(active) fail("connection");webSocket.close(code,"")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceManualEventEditor) {if(active) fail("connection")}}
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
                if(meta.rights and 4L==0L) {fail("denied");return}
                val message=target
                timer?.cancel();tag="";
                if(message==null) {state.value=ManualEventState("ready",rights=meta.rights);return}
                val change=manualEventPacket(node,message,meta.rights)
                target=null;attempted=true
                if(!send(change)) {fail("unconfirmed");return}
                state.value=ManualEventState("sent")
            }
        }
    }
    @Synchronized fun submit(reviewedText:String):Boolean {
        if(!active || requested || attempted || !same() || !state.value.canSubmit || runCatching {manualEventEncoded(reviewedText)}.isFailure) return false
        requested=true;target=reviewedText;state.value=ManualEventState("checking");deadline()
        if(!read()) {fail("connection");return false}
        return true
    }
    @Synchronized fun close() {
        if(!active) return
        active=false;timer?.cancel();target=null;tag="";socket?.cancel();socket=null;jobs.cancel()
        state.value=ManualEventState("error",if(attempted) "unconfirmed" else "disconnected")
    }
}
