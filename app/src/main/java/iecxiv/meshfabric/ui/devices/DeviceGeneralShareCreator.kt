package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

internal data class GeneralShareCreateDraft(val guest:String,val mesh:String,val start:Long,val end:Long)
internal class GeneralShareCreatedLink(val id:String,val url:String) {override fun toString()="GeneralShareCreatedLink(redacted)"}
internal fun generalShareGuestValid(s:String)=s.isNotBlank() && s.length<=128 && s.none {it.isISOControl()} && Charsets.UTF_8.newEncoder().canEncode(s)
internal fun generalShareCreateDraft(guest:String,mesh:String,minutes:Int,now:Long):GeneralShareCreateDraft {
    require(generalShareGuestValid(guest) && mesh.isNotBlank() && minutes in listOf(15,30,60) && now in 1L..253402297199L)
    return GeneralShareCreateDraft(guest,mesh,now,now+minutes*60L)
}
internal fun generalShareCreatePacket(node:String,request:String,d:GeneralShareCreateDraft,meta:GeneralMeta,now:Long):JsonObject {
    require(generalShareGuestValid(d.guest) && d.mesh==meta.mesh && generalSharesAllowed(meta.rights))
    require(meta.rights and 0xffffffffL==0xffffffffL || meta.rights and 65536L==0L)
    require(d.start in 1L..253402297199L && d.end-d.start in listOf(900L,1800L,3600L) && now>=d.start && now-d.start<=120L && d.end-now>=60L)
    return generalSharesRequest(node,request).apply {addProperty("action","createDeviceShareLink");addProperty("guestname",d.guest);addProperty("p",2);addProperty("viewOnly",true);addProperty("consent",9);addProperty("start",d.start);addProperty("end",d.end)}
}
internal fun generalShareCreatedUrl(server:String,id:String,url:String):String {
    require(id.isNotBlank() && id.length<=1024 && id.none {it.isISOControl()} && url.length<=16384 && url.none {it.isISOControl()})
    val base=requireNotNull(server.toHttpUrlOrNull());val link=requireNotNull(url.toHttpUrlOrNull())
    require(base.scheme=="https" && link.scheme=="https" && base.host==link.host && base.port==link.port)
    require(base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null && link.username.isEmpty() && link.password.isEmpty() && link.fragment==null)
    require(link.encodedPath==base.encodedPath.trimEnd('/')+"/sharing")
    require(link.queryParameterValues("id").size==1 && link.queryParameter("id")==id)
    require(link.queryParameterNames.none {it.lowercase() in setOf("auth","rauth","cookie","password","token")})
    return url
}
internal fun generalShareCreateReply(p:JsonObject,node:String,request:String,server:String):GeneralShareCreatedLink? {
    if(request.isBlank() || p.get("action")?.asString!="createDeviceShareLink" || p.get("responseid")?.asString!=request) return null
    val target=p.get("nodeid")?.asString.orEmpty();if(target.isNotBlank() && target!=node) return null
    require(!p.has("error") && !p.has("err") && (!p.has("result") || p.get("result").asString.equals("ok",true)))
    val id=requireNotNull(p.get("publicid"));val url=requireNotNull(p.get("url"))
    require(id.isJsonPrimitive && id.asJsonPrimitive.isString && url.isJsonPrimitive && url.asJsonPrimitive.isString)
    return GeneralShareCreatedLink(id.asString,generalShareCreatedUrl(server,id.asString,url.asString))
}
internal fun generalShareCreateVerified(p:JsonObject,node:String,request:String,actor:String,d:GeneralShareCreateDraft,id:String,before:Set<String>):Boolean {
    if(id in before) return false
    val rows=generalShareRevokeList(p,node,request) ?: return false
    val row=rows.singleOrNull {it.id==id} ?: return false
    if(row.owner!=actor || row.guest!=d.guest || row.protocols!=2L || row.viewOnly!=true || row.start!=d.start*1000L || row.expires!=d.end*1000L) return false
    val raw=p.getAsJsonArray("deviceShares").single {it.asJsonObject.get("publicid").asString==id}.asJsonObject.get("consent") ?: return false
    if(!raw.isJsonPrimitive || !raw.asJsonPrimitive.isNumber) return false
    val consent=runCatching {raw.asBigDecimal.longValueExact()}.getOrNull() ?: return false
    return consent in 0L..0xffffffffL && consent and 9L==9L
}
internal data class GeneralShareCreateState(val phase:String="checking",val code:String="",val attempted:Boolean=false)
internal class DeviceGeneralShareCreator(private val source:MeshCentralClient?,private val node:String,private val draft:GeneralShareCreateDraft) {
    val state=MutableStateFlow(GeneralShareCreateState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false;private val actor=source?.userInfo?.value?.id.orEmpty()
    private var active=true;private var started=false;private var attempted=false;private var mode="user";private var socket:WebSocket?=null
    private var tag="";private var readId="";private var meta:GeneralMeta?=null;private var before=emptySet<String>();private var created:GeneralShareCreatedLink?=null
    private var timer:Job?=null;private var poll:Job?=null;private val writeId=UUID.randomUUID().toString()
    private fun now()=System.currentTimeMillis()/1000L
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && source.userInfo.value?.id==actor && actor.isNotBlank() && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun fail(code:String) {
        if(!active) return
        val uncertain=attempted && state.value.phase!="confirmed"
        active=false;mode="closed";timer?.cancel();poll?.cancel();created=null;before=emptySet();socket?.cancel();socket=null;jobs.cancel()
        state.value=GeneralShareCreateState("error",if(uncertain) "unconfirmed" else code,attempted)
    }
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceGeneralShareCreator) {if(active) fail("timeout")}}}
    private fun read():Boolean {readId=UUID.randomUUID().toString();return send(generalSharesRequest(node,readId))}
    @Synchronized fun start() {
        if(started || !active) return
        started=true
        val a=actor.split('/');val n=node.split('/')
        if(!same() || cookie.isBlank() || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1] || url.toHttpUrlOrNull()?.scheme!="https") {fail("identity");return}
        deadline();jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceGeneralShareCreator) {if(active && !same()) fail("identity") else if(active && now()>=draft.end) fail("expired")}}}
        try {socket=OkHttpHelper.createClient(tls).newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceGeneralShareCreator) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceGeneralShareCreator) {
                if(!active || mode=="idle") return
                if(!same() || text.length>2_000_000) {fail("connection");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("unverified")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralShareCreator) {if(mode!="idle") fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralShareCreator) {if(mode!="idle") fail("connection")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralShareCreator) {if(mode!="idle") fail("connection")}}
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
                val current=generalMeta(p,node,tag) ?: run {fail("unverified");return}
                generalShareCreatePacket(node,writeId,draft,current,now());meta=current;tag="";mode="before";if(!read()) fail("connection")
            }
            "deviceShares"->if(mode=="before") {
                val shares=generalShareRevokeList(p,node,readId) ?: return
                before=shares.map {it.id}.toSet();readId=""
                val change=generalShareCreatePacket(node,writeId,draft,requireNotNull(meta),now())
                attempted=true;mode="create";state.value=GeneralShareCreateState("creating",attempted=true);deadline()
                if(!send(change)) fail("unconfirmed")
            } else if(mode=="confirm" && generalShareCreateVerified(p,node,readId,actor,draft,requireNotNull(created).id,before)) {
                timer?.cancel();poll?.cancel();mode="idle";readId="";before=emptySet();state.value=GeneralShareCreateState("confirmed",attempted=true)
                val old=socket;socket=null;old?.cancel()
            }
            "createDeviceShareLink"->if(mode=="create") {
                val link=generalShareCreateReply(p,node,writeId,url) ?: return
                require(link.id !in before);created=link;mode="confirm";state.value=GeneralShareCreateState("verifying",attempted=true)
                if(!read()) {fail("unconfirmed");return}
                poll=jobs.launch {repeat(6) {delay(1000);synchronized(this@DeviceGeneralShareCreator) {if(active && mode=="confirm" && !read()) fail("unconfirmed")}}}
            }
        }
    }
    @Synchronized fun linkForUserAction():String?=if(active && same() && state.value.phase=="confirmed" && now()<draft.end) created?.url else null
    @Synchronized fun close() {fail("closed")}
}
