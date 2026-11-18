package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import java.util.UUID

internal data class GeneralShare(val id:String,val guest:String,val owner:String,val protocols:Long?,val viewOnly:Boolean?,val start:Long?,val expires:Long?)
internal fun generalSharesAllowed(rights:Long)=rights and 524296L==524296L
internal fun generalSharesRequest(node:String,id:String):JsonObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && node.none {it.isISOControl()})
    require(id.isNotBlank() && id.length<=128 && id.none {it.isISOControl()})
    return JsonObject().apply {addProperty("action","deviceShares");addProperty("nodeid",node);addProperty("responseid",id)}
}
internal fun generalSharesReply(p:JsonObject,node:String,request:String):List<GeneralShare>? {
    if(request.isBlank() || p.get("action")?.asString!="deviceShares") return null
    val target=p.get("nodeid")?.asString.orEmpty();val rid=p.get("responseid")?.asString.orEmpty()
    if(target.isNotBlank() && target!=node || rid.isNotBlank() && rid!=request || target!=node && rid!=request) return null
    require(!p.has("error") && !p.has("err") && (!p.has("result") || p.get("result").asString.equals("ok",true)))
    val rows=requireNotNull(p.get("deviceShares"));require(rows.isJsonArray && rows.asJsonArray.size()<=500)
    fun text(row:JsonObject,key:String,max:Int):String {
        val v=row.get(key) ?: return "";if(v.isJsonNull) return ""
        require(v.isJsonPrimitive && v.asJsonPrimitive.isString)
        return v.asString.also {require(it.length<=max && it.none {c->c.isISOControl()} && Charsets.UTF_8.newEncoder().canEncode(it))}
    }
    fun number(row:JsonObject,key:String,max:Long):Long? {
        val v=row.get(key)?.takeUnless {it.isJsonNull} ?: return null
        require(v.isJsonPrimitive && v.asJsonPrimitive.isNumber)
        return v.asBigDecimal.longValueExact().also {require(it in 0L..max)}
    }
    return rows.asJsonArray.map {raw->
        require(raw.isJsonObject);val row=raw.asJsonObject
        val itemNode=text(row,"nodeid",4096);require(itemNode.isBlank() || itemNode==node)
        val id=text(row,"publicid",1024);require(id.isNotBlank())
        val view=row.get("viewOnly")?.takeUnless {it.isJsonNull}?.let {require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean);it.asBoolean}
        GeneralShare(id,text(row,"guestName",1024),text(row,"userid",4096),number(row,"p",Int.MAX_VALUE.toLong()),view,number(row,"startTime",253402300799999L),number(row,"expireTime",253402300799999L))
    }.also {require(it.map {r->r.id}.distinct().size==it.size)}
}
internal data class GeneralSharesState(val phase:String="loading",val shares:List<GeneralShare> = emptyList(),val loadedAt:Long?=null,val code:String="")
internal class DeviceGeneralSharesReader(private val source:MeshCentralClient?,private val node:String) {
    val state=MutableStateFlow(GeneralSharesState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false
    private val actor=source?.userInfo?.value?.id.orEmpty()
    private var active=true;private var started=false;private var mode="user";private var tag="";private var readId="";private var socket:WebSocket?=null
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && actor.isNotBlank() && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && p.get("action")?.asString in setOf("getsysinfo","deviceShares","pong") && socket?.send(p.toString())==true
    @Synchronized private fun fail(code:String) {
        if(!active) return
        active=false;mode="closed";tag="";readId="";socket?.cancel();socket=null;jobs.cancel();state.value=GeneralSharesState(phase="error",code=code)
    }
    @Synchronized fun start() {
        if(started || !active) return
        started=true
        val a=actor.split('/');val n=node.split('/')
        if(!same() || cookie.isBlank() || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1]) {fail("identity");return}
        jobs.launch {delay(20_000);synchronized(this@DeviceGeneralSharesReader) {if(active && state.value.phase=="loading") fail("timeout")}}
        jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceGeneralSharesReader) {if(active && !same()) fail("identity")}}}
        try {socket=OkHttpHelper.createClient(tls).newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(
            Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceGeneralSharesReader) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceGeneralSharesReader) {
                if(!active) return
                if(!same() || text.length>2_000_000) {fail("connection");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("unverified")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralSharesReader) {fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralSharesReader) {fail("connection")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralSharesReader) {fail("connection")}}
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
                tag="";mode="shares";readId=UUID.randomUUID().toString()
                if(!send(generalSharesRequest(node,readId))) fail("connection")
            }
            "deviceShares"->if(mode=="shares") {
                val shares=generalSharesReply(p,node,readId) ?: return
                mode="idle";readId="";state.value=GeneralSharesState("ready",shares,System.currentTimeMillis())
            }
        }
    }
    @Synchronized fun close() {fail("closed")}
}
