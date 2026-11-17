package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

internal const val AGENT_CORE_FILE_LIMIT=1048576
internal class AgentCoreFile(name:String,bytes:ByteArray) {
    val name=name
    private val content=bytes.copyOf()
    val size=content.size
    val sha256:String
    init {
        require(name.length in 4..128 && name.endsWith(".js",true) && name.none {it.isISOControl() || it in "/\\\""})
        require(size in 1..AGENT_CORE_FILE_LIMIT && content.none {it==0.toByte()})
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content))
        sha256=MessageDigest.getInstance("SHA-256").digest(content).joinToString("") {"%02x".format(it.toInt() and 255)}
    }
    fun body():RequestBody=content.toRequestBody("application/javascript; charset=utf-8".toMediaType())
    override fun toString()="AgentCoreFile(redacted)"
}
internal fun readAgentCoreFile(name:String,input:InputStream):AgentCoreFile {
    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true) {val n=input.read(buffer,0,minOf(buffer.size,AGENT_CORE_FILE_LIMIT+1-out.size()));if(n<0) break;require(n>0);out.write(buffer,0,n);require(out.size()<=AGENT_CORE_FILE_LIMIT)}
    return AgentCoreFile(name,out.toByteArray())
}
internal fun agentCoreMainDomain(node:String,actor:String):Boolean {
    val n=node.split('/');val a=actor.split('/')
    return n.size==3 && n[0]=="node" && n[1].isEmpty() && n[2].isNotBlank() && node.length<=4096 && node.none {it.isISOControl()} && a.size==3 && a[0]=="user" && a[1].isEmpty() && a[2].isNotBlank() && actor.none {it.isISOControl()}
}
internal fun agentCoreUploadUrl(server:String):HttpUrl {
    val base=requireNotNull(server.toHttpUrlOrNull())
    require(base.scheme=="https" && base.username.isEmpty() && base.password.isEmpty() && base.query==null && base.fragment==null)
    return base.newBuilder().encodedPath(base.encodedPath.trimEnd('/')+"/uploadmeshcorefile.ashx").build()
}
internal fun agentCoreUploadBody(node:String,auth:String,file:AgentCoreFile):MultipartBody {
    require(agentCoreMainDomain(node,"user//local") && auth.isNotBlank() && auth.length<=16384 && auth.none {it.isISOControl()})
    return MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("auth",auth).addFormDataPart("attrib",node).addFormDataPart("files",file.name,file.body()).build()
}
internal data class AgentCoreUploadState(val phase:String="loading",val code:String="",val attempted:Boolean=false)
internal class DeviceAgentCoreUploader(private val source:MeshCentralClient,private val node:String) {
    val state=MutableStateFlow(AgentCoreUploadState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source.serverUrl;private val cookie=source.authCookie;private val tls=source.ignoreSsl;private val actor=source.userInfo.value?.id.orEmpty()
    private var active=true;private var started=false;private var requested=false;private var attempted=false
    private var socket:WebSocket?=null;private var call:Call?=null;private var timer:Job?=null;private var tag=""
    private var meta:GeneralMeta?=null;private var target:AgentCoreFile?=null
    private fun same()=!source.isUserLogout && source.connectionState.value && actor.isNotBlank() && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=active && same() && socket?.send(p.toString())==true
    @Synchronized private fun finish(phase:String,code:String) {
        if(!active) return
        active=false;timer?.cancel();tag="";target=null;call?.cancel();call=null;val old=socket;socket=null;old?.cancel();jobs.cancel()
        state.value=AgentCoreUploadState(phase,code,attempted)
    }
    private fun fail(code:String)=finish("error",if(attempted) "unconfirmed" else code)
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceAgentCoreUploader) {fail("timeout")}}}
    private fun read():Boolean {tag=UUID.randomUUID().toString();return send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})}
    @Synchronized fun start() {
        if(started || !active) return
        started=true
        if(!same() || cookie.isBlank() || !agentCoreMainDomain(node,actor) || runCatching {agentCoreUploadUrl(url)}.isFailure) {fail("domain_or_identity");return}
        deadline();jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceAgentCoreUploader) {if(active && (!same() || !attempted && meta?.let {!agentConsoleAvailable(node,it,source.groupedDevices.value)}==true)) fail("identity_or_connection")}}}
        try {socket=OkHttpHelper.createClient(tls).newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build().newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceAgentCoreUploader) {
                if(!active || !same()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceAgentCoreUploader) {
                if(!active) return
                if(!same() || text.length>2_000_000) {fail("connection");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("invalid")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceAgentCoreUploader) {if(!attempted) fail("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceAgentCoreUploader) {if(!attempted) fail("connection")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceAgentCoreUploader) {if(!attempted) fail("connection")}}
        })} catch(_:Exception) {fail("connection")}
    }
    private fun receive(p:JsonObject) {
        when(p.get("action")?.asString) {
            "ping"->send(packet("pong"))
            "close","stopped"->fail("denied")
            "userinfo"->{
                if(p.getAsJsonObject("userinfo")?.get("_id")?.asString!=actor) {fail("identity");return}
                if(state.value.phase in setOf("loading","checking") && tag.isEmpty() && !read()) fail("connection")
            }
            "getsysinfo"->if(tag.isNotBlank() && p.get("tag")?.asString==tag && p.get("nodeid")?.asString==node && state.value.phase in setOf("loading","checking")) {
                val current=generalMeta(p,node,tag) ?: run {fail("unverified");return}
                if(!agentActionFullRights(current.rights) || !agentConsoleAvailable(node,current,source.groupedDevices.value)) {fail("denied");return}
                tag=""
                if(!requested) {meta=current;timer?.cancel();state.value=AgentCoreUploadState("ready");return}
                val before=meta ?: run {fail("unverified");return}
                if(before.mesh!=current.mesh || before.name!=current.name || before.os!=current.os) {fail("changed");return}
                meta=current;state.value=AgentCoreUploadState("cookie")
                if(!send(packet("authcookie"))) fail("connection")
            }
            "authcookie"->if(state.value.phase=="cookie" && requested && !attempted) {
                val raw=requireNotNull(p.get("cookie"));require(raw.isJsonPrimitive && raw.asJsonPrimitive.isString)
                upload(raw.asString)
            }
        }
    }
    private fun upload(auth:String) {
        val current=meta ?: run {fail("unverified");return}
        if(!same() || !agentActionFullRights(current.rights) || !agentConsoleAvailable(node,current,source.groupedDevices.value)) {fail("changed");return}
        val body=agentCoreUploadBody(node,auth,requireNotNull(target))
        val client=OkHttpHelper.createDownloadClient(tls).newBuilder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).callTimeout(30,TimeUnit.SECONDS).build()
        val request=Request.Builder().url(agentCoreUploadUrl(url)).post(body).build()
        call=client.newCall(request);target=null;attempted=true;timer?.cancel();state.value=AgentCoreUploadState("uploading",attempted=true)
        call!!.enqueue(object:Callback {
            override fun onFailure(call:Call,e:java.io.IOException) {synchronized(this@DeviceAgentCoreUploader) {fail("unconfirmed")}}
            override fun onResponse(call:Call,response:Response) {response.use {synchronized(this@DeviceAgentCoreUploader) {
                if(!active) return
                if(!same()) {fail("identity");return}
                if(it.code==200) finish("received","application_unconfirmed") else fail("http")
            }}}
        })
    }
    @Synchronized fun submit(file:AgentCoreFile):Boolean {
        if(!active || requested || attempted || !same() || state.value.phase!="ready") return false
        target=file;requested=true;state.value=AgentCoreUploadState("checking");deadline()
        if(!send(packet("userinfo"))) fail("connection")
        return true
    }
    @Synchronized fun close() {fail("closed")}
}
