package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okio.ByteString
import java.util.UUID

internal class DeviceGeneralChatSession(private val source:MeshCentralClient?,private val node:String) {
    val state=MutableStateFlow(GeneralChatState())
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source?.serverUrl.orEmpty();private val cookie=source?.authCookie.orEmpty();private val tls=source?.ignoreSsl ?: false
    private val actor=source?.userInfo?.value?.id.orEmpty()
    private val token=source?.userAuthCookie.orEmpty().ifBlank {source?.extractRawToken(cookie).orEmpty()}
    private var active=true;private var started=false;private var requested=false;private var invited=false;private var paired=false
    private var control:WebSocket?=null;private var relay:WebSocket?=null;private var timer:Job?=null
    private var tag="";private var expectedMesh="";private var pending:GeneralChatEntry?=null
    private fun same()=source!=null && !source.isUserLogout && source.connectionState.value && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun available(meta:GeneralMeta)=same() && generalMessageAvailable(node,meta,source?.groupedDevices?.value.orEmpty())
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun entryStatus(status:String) {val id=pending?.id ?: return;state.value=state.value.copy(entries=state.value.entries.map {if(it.id==id) it.copy(status=status) else it})}
    @Synchronized private fun stop(code:String) {
        if(!active) return
        active=false;paired=false;timer?.cancel();tag="";entryStatus("not_sent");pending=null
        control?.cancel();relay?.cancel();control=null;relay=null;jobs.cancel()
        state.value=state.value.copy(phase="closed",code=code)
    }
    private fun deadline(ms:Long,code:String) {timer?.cancel();timer=jobs.launch {delay(ms);synchronized(this@DeviceGeneralChatSession) {if(active) stop(code)}}}
    private fun read():Boolean {
        tag=UUID.randomUUID().toString();deadline(20_000,"timeout")
        return same() && control?.send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)}.toString())==true
    }
    @Synchronized fun start() {
        if(started || !active) return
        started=true
        if(!same() || cookie.isBlank() || runCatching {generalChatId(node,actor)}.isFailure || token.isBlank()) {stop("identity");return}
        deadline(20_000,"timeout")
        jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceGeneralChatSession) {
            val meta=state.value.meta
            if(active && (!same() || meta!=null && !available(meta))) stop("unavailable")
        }}}
        try {control=OkHttpHelper.createClient(tls).newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(
            Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {synchronized(this@DeviceGeneralChatSession) {
                if(!active || !same()) {ws.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source!!.extractRawToken(cookie))})}
                if(!ws.send(auth.toString()) || !ws.send(packet("userinfo").toString())) stop("connection")
            }}
            override fun onMessage(ws:WebSocket,text:String) {synchronized(this@DeviceGeneralChatSession) {
                if(!active) return
                if(!same() || text.length>2_000_000) {stop("connection");return}
                runCatching {receiveControl(JsonParser.parseString(text).asJsonObject)}.onFailure {stop("invalid")}
            }}
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralChatSession) {stop("connection")}}
            override fun onClosing(ws:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralChatSession) {stop("connection")}}
            override fun onClosed(ws:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralChatSession) {stop("connection")}}
        })} catch(_:Exception) {stop("connection")}
    }
    private fun receiveControl(p:JsonObject) {
        when(p.get("action")?.asString) {
            "ping"->control?.send(packet("pong").toString())
            "close","stopped"->stop("denied")
            "userinfo"->{
                if(p.getAsJsonObject("userinfo")?.get("_id")?.asString!=actor) {stop("identity");return}
                if(state.value.phase=="loading" && tag.isEmpty() && !read()) stop("connection")
            }
            "getsysinfo"->if(tag.isNotBlank() && p.get("tag")?.asString==tag && p.get("nodeid")?.asString==node) {
                val meta=generalMeta(p,node,tag) ?: run {stop("unverified");return}
                timer?.cancel();tag=""
                if(!available(meta) || expectedMesh.isNotBlank() && expectedMesh!=meta.mesh) {stop("unavailable");return}
                state.value=state.value.copy(meta=meta)
                when(state.value.phase) {
                    "loading"->state.value=state.value.copy(phase="ready")
                    "checking"->openRelay()
                    "checking_send"->{
                        val entry=pending ?: return
                        if(!paired || !same()) {stop("connection");return}
                        entryStatus("unknown")
                        val sent=relay?.send(generalChatPacket(entry.text).toString())==true
                        entryStatus(if(sent) "queued" else "unknown");pending=null
                        if(!sent) {stop("connection");return}
                        state.value=state.value.copy(phase="connected")
                    }
                }
            }
        }
    }
    @Synchronized fun connect(mesh:String):Boolean {
        val meta=state.value.meta ?: return false
        if(!active || requested || state.value.phase!="ready" || mesh!=meta.mesh || !available(meta)) return false
        requested=true;expectedMesh=mesh;state.value=state.value.copy(phase="checking")
        if(!read()) {stop("connection");return false}
        return true
    }
    private fun openRelay() {
        if(!active || relay!=null || !same()) {stop("connection");return}
        state.value=state.value.copy(phase="connecting");deadline(20_000,"timeout")
        try {relay=OkHttpHelper.createClient(tls).newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(
            Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,generalChatRelayPath(node,actor,token))).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {synchronized(this@DeviceGeneralChatSession) {
                val meta=state.value.meta
                if(!active || !same() || meta==null || !available(meta) || invited) {ws.cancel();stop("unavailable");return}
                invited=true
                if(control?.send(packet("meshmessenger").apply {addProperty("nodeid",node)}.toString())!=true) {stop("invitation_unknown");return}
                state.value=state.value.copy(phase="waiting");deadline(120_000,"waiting_timeout")
            }}
            override fun onMessage(ws:WebSocket,text:String) {synchronized(this@DeviceGeneralChatSession) {receiveRelay(text)}}
            override fun onMessage(ws:WebSocket,bytes:ByteString) {synchronized(this@DeviceGeneralChatSession) {if(bytes.size<=32768) receiveRelay(bytes.utf8()) else stop("invalid")}}
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceGeneralChatSession) {stop("connection")}}
            override fun onClosing(ws:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralChatSession) {stop("connection")}}
            override fun onClosed(ws:WebSocket,code:Int,reason:String) {synchronized(this@DeviceGeneralChatSession) {stop("connection")}}
        })} catch(_:Exception) {stop("connection")}
    }
    private fun receiveRelay(text:String) {
        if(!active) return
        if(!same() || text.length>32768) {stop("connection");return}
        val marker=generalChatMarker(text)
        if(!paired && marker!=null && state.value.phase=="waiting") {
            timer?.cancel();paired=true;state.value=state.value.copy(phase="connected",recorded=marker)
            if(relay?.send(packet("random").apply {addProperty("random",1)}.toString())!=true) stop("connection")
            return
        }
        if(!paired) return
        val p=runCatching {JsonParser.parseString(text).asJsonObject}.getOrNull() ?: return
        runCatching {
            if(p.get("ctrlChannel")?.asString=="102938") {
                if(p.get("type")?.asString=="ping") relay?.send(JsonObject().apply {addProperty("ctrlChannel","102938");addProperty("type","pong")}.toString())
                return
            }
            when(p.get("action")?.asString) {
                "chat"->{
                    val value=p.get("msg")?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString ?: return
                    if(!generalChatTextValid(value)) return
                    state.value=state.value.copy(entries=generalChatHistory(state.value.entries+GeneralChatEntry(UUID.randomUUID().toString(),false,value)))
                }
                "file"->{val id=p.get("id") ?: return;if(id.isJsonPrimitive && id.toString().length<=128) relay?.send(packet("fileUploadCancel").apply {add("id",id)}.toString())}
            }
        }.onFailure {stop("invalid")}
    }
    @Synchronized fun send(text:String):Boolean {
        val meta=state.value.meta ?: return false
        if(!active || !paired || !available(meta) || !generalChatCanSend(state.value.phase,text) || pending!=null) return false
        val entry=GeneralChatEntry(UUID.randomUUID().toString(),true,text,"checking")
        pending=entry;state.value=state.value.copy(phase="checking_send",entries=generalChatHistory(state.value.entries+entry))
        if(!read()) stop("connection")
        return true
    }
    @Synchronized fun close() {stop("closed")}
}
