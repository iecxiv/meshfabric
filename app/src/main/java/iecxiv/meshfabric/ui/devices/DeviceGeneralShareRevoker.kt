package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.security.MessageDigest
import java.util.UUID

internal fun generalShareFingerprint(id:String)=MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8)).take(8).joinToString("") {"%02x".format(it.toInt() and 255)}
internal fun generalShareRevokeList(p:JsonObject,node:String,request:String):List<GeneralShare>? {
    if(request.isBlank() || p.get("responseid")?.asString!=request) return null
    return generalSharesReply(p,node,request)
}
internal fun generalShareRevokePacket(node:String,request:String,actor:String,reviewed:GeneralShare,current:List<GeneralShare>,rights:Long):JsonObject {
    val a=actor.split('/');val n=node.split('/')
    require(a.size==3 && a[0]=="user" && a[2].isNotBlank() && n.size==3 && a[1]==n[1] && actor.none {it.isISOControl()})
    require(generalSharesAllowed(rights) && reviewed.owner==actor)
    require(reviewed.id.isNotBlank() && reviewed.id.length<=1024 && reviewed.id.none {it.isISOControl()} && Charsets.UTF_8.newEncoder().canEncode(reviewed.id))
    require(current.singleOrNull {it.id==reviewed.id}==reviewed)
    return generalSharesRequest(node,request).apply {addProperty("action","removeDeviceShare");addProperty("publicid",reviewed.id)}
}
internal data class GeneralShareRevokeState(val phase:String="checking",val code:String="",val attempted:Boolean=false)
internal class DeviceGeneralShareRevoker(private val source:MeshCentralClient?,private val node:String,private val reviewed:GeneralShare) {
    val state=MutableStateFlow(GeneralShareRevokeState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false
    private val actor=source?.userInfo?.value?.id.orEmpty()
    private var active=true;private var started=false;private var attempted=false;private var mode="user"
    private var socket:WebSocket?=null;private var timer:Job?=null;private var tag="";private var readId="";private var rights=0L
    private val writeId=UUID.randomUUID().toString()
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && actor.isNotBlank() && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun finish(phase:String,code:String) {
        if(!active) return
        active=false;mode="closed";timer?.cancel();tag="";readId="";socket?.cancel();socket=null;jobs.cancel()
        state.value=GeneralShareRevokeState(phase,code,attempted)
    }
    private fun fail(code:String)=finish("error",if(attempted) "unconfirmed" else code)
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceGeneralShareRevoker) {if(active) fail("timeout")}}}
    private fun read():Boolean {readId=UUID.randomUUID().toString();return send(generalSharesRequest(node,readId))}
    @Synchronized fun start() {
        if(started || !active) return
        started=true
        val a=actor.split('/');val n=node.split('/')
        if(!same() || cookie.isBlank() || reviewed.owner!=actor || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1]) {fail("identity");return}
        deadline()
        jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceGeneralShareRevoker) {if(active && !same()) fail("identity")}}}
        try {socket=OkHttpHelper.createClient(tls).newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(
            Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceGeneralShareRevoker) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceGeneralShareRevoker) {
                if(!active) return
                if(!same() || text.length>2_000_000) {fail("connection");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("unverified")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralShareRevoker) {fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralShareRevoker) {fail("connection")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralShareRevoker) {fail("connection")}}
        })} catch(_:Exception) {fail("connection")}
    }
    private fun receive(p:JsonObject) {
        when(p.get("action")?.asString) {
            "ping"->send(packet("pong"))
            "close","stopped"->fail("denied")
            "userinfo"->{
                if(p.getAsJsonObject("userinfo")?.get("_id")?.asString!=actor) {fail("identity");return}
                if(mode=="user") {mode="meta";tag=UUID.randomUUID().toString();if(!send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})) fail("connection")}
            }
            "getsysinfo"->if(mode=="meta" && tag.isNotBlank() && p.get("tag")?.asString==tag && p.get("nodeid")?.asString==node) {
                val meta=generalMeta(p,node,tag) ?: run {fail("unverified");return}
                if(!generalSharesAllowed(meta.rights)) {fail("denied");return}
                rights=meta.rights;tag="";mode="preflight";if(!read()) fail("connection")
            }
            "deviceShares"->if(mode in setOf("preflight","confirm")) {
                val shares=generalShareRevokeList(p,node,readId) ?: return
                readId=""
                if(mode=="preflight") {
                    val change=runCatching {generalShareRevokePacket(node,writeId,actor,reviewed,shares,rights)}.getOrNull() ?: run {fail("changed");return}
                    if(!same()) {fail("identity");return}
                    attempted=true;mode="confirm";state.value=GeneralShareRevokeState("verifying",attempted=true);deadline()
                    if(!send(change) || !read()) {fail("unconfirmed");return}
                    jobs.launch {repeat(6) {delay(1000);synchronized(this@DeviceGeneralShareRevoker) {if(active && mode=="confirm" && !read()) fail("unconfirmed")}}}
                } else if(shares.none {it.id==reviewed.id}) finish("absent","readback")
            }
            "removeDeviceShare"->if(mode=="confirm" && p.get("responseid")?.asString==writeId && (p.has("error") || p.has("err") || p.has("result") && !p.get("result").asString.equals("ok",true))) fail("rejected")
        }
    }
    @Synchronized fun close() {fail("closed")}
}
