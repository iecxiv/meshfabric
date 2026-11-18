package iecxiv.meshfabric.ui.devices

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

internal data class AgentConsoleLine(val kind:String,val text:String)
internal fun agentConsoleCommandValid(s:String)=s.isNotBlank() && s.length<=4096 && s.none {it.isISOControl()} && Charsets.UTF_8.newEncoder().canEncode(s)
internal fun agentConsoleAvailable(node:String,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>):Boolean {
    if(meta.rights and 16L==0L) return false
    val group=inventory.singleOrNull {it.first.id==meta.mesh} ?: return false
    val current=inventory.flatMap {it.second}.singleOrNull {it.id==node} ?: return false
    return group.first.mtype==2 && current.meshid==meta.mesh && current.conn and 1!=0 && group.second.count {it.id==node}==1
}
internal fun agentConsolePacket(node:String,text:String,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>):JsonObject {
    require(node.startsWith("node/") && node.split('/').size==3 && node.substringAfterLast('/').isNotBlank() && node.length<=4096 && node.none {it.isISOControl()})
    require(agentConsoleCommandValid(text) && agentConsoleAvailable(node,meta,inventory))
    return JsonObject().apply {addProperty("action","msg");addProperty("type","console");addProperty("nodeid",node);addProperty("value",text)}
}
internal fun agentConsoleOutput(p:JsonObject,node:String):String? {
    if(p.get("action")?.asString!="msg" || p.get("type")?.asString!="console" || p.get("nodeid")?.asString!=node) return null
    val raw=p.get("value") ?: return null
    require(raw.isJsonPrimitive && raw.asJsonPrimitive.isString && raw.asString.length<=131072)
    return raw.asString.replace("\r\n","\n").replace('\r','\n').map {if(it.isISOControl() && it!='\n' && it!='\t') '\uFFFD' else it}.joinToString("")
}
internal fun agentConsoleBounded(lines:List<AgentConsoleLine>):List<AgentConsoleLine> {
    val result=lines.takeLast(1000).toMutableList();var size=result.sumOf {it.text.length}
    while(size>256000 && result.isNotEmpty()) size-=result.removeAt(0).text.length
    return result
}
internal data class AgentConsoleState(val phase:String="closed",val code:String="",val lines:List<AgentConsoleLine> = emptyList(),val history:List<String> = emptyList(),val rights:Long?=null)
internal class DeviceAgentConsoleSession(private val source:MeshCentralClient,private val node:String) {
    val state=MutableStateFlow(AgentConsoleState())
    val agentVersion=MutableStateFlow<String?>(null)
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val url=source.serverUrl;private val cookie=source.authCookie;private val tls=source.ignoreSsl;private val actor=source.userInfo.value?.id.orEmpty()
    private var socket:WebSocket?=null;private var generation=0L;private var disposed=false;private var revoked=false
    private var tag="";private var pending:String?=null;private var pendingAction:AgentConsoleAction?=null;private var meta:GeneralMeta?=null;private var timer:Job?=null
    private fun identity()=!source.isUserLogout && actor.isNotBlank() && source.userInfo.value?.id==actor && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls
    private fun current(ws:WebSocket,g:Long)=!disposed && generation==g && socket===ws
    private fun append(kind:String,text:String) {state.value=state.value.copy(lines=agentConsoleBounded(state.value.lines+AgentConsoleLine(kind,text)))}
    private fun packet(action:String)=JsonObject().apply {addProperty("action",action)}
    private fun send(p:JsonObject)=!disposed && identity() && source.connectionState.value && socket?.send(p.toString())==true
    @Synchronized private fun retire(code:String,clear:Boolean=false) {
        generation++;timer?.cancel();tag="";meta=null
        val old=socket;socket=null;old?.cancel()
        if(clear) {agentVersion.value=null;revoked=true;pending=null;pendingAction=null;state.value=AgentConsoleState(code=code)} else {
            if(pending!=null) append("not_sent",requireNotNull(pending))
            pendingAction?.let {append("not_sent",it.wire)}
            pending=null;pendingAction=null;state.value=state.value.copy(phase="closed",code=code,rights=null)
        }
    }
    init {jobs.launch {while(isActive) {delay(500);synchronized(this@DeviceAgentConsoleSession) {
        if(!disposed && !revoked && !identity()) retire("identity",true)
        else if(state.value.phase!="closed" && (!source.connectionState.value || meta?.let {!agentConsoleAvailable(node,it,source.groupedDevices.value)}==true)) retire("disconnected")
    }}}}
    private fun deadline() {timer?.cancel();timer=jobs.launch {delay(20_000);synchronized(this@DeviceAgentConsoleSession) {if(state.value.phase in setOf("connecting","checking","checking_send")) retire("timeout")}}}
    private fun read():Boolean {tag=UUID.randomUUID().toString();deadline();return send(packet("getsysinfo").apply {addProperty("nodeid",node);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})}
    @Synchronized fun connect() {
        if(disposed || revoked || state.value.phase!="closed") return
        val a=actor.split('/');val n=node.split('/')
        if(!identity() || cookie.isBlank() || !source.connectionState.value || a.size!=3 || a[0]!="user" || a[2].isBlank() || n.size!=3 || n[0]!="node" || n[2].isBlank() || a[1]!=n[1]) {retire("unavailable");return}
        agentVersion.value=null
        state.value=state.value.copy(phase="connecting",code="");deadline();val g=++generation
        try {socket=OkHttpHelper.createClient(tls).newBuilder().followRedirects(false).followSslRedirects(false).build().newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(url,"/control.ashx?moreargs=1")).header("Cookie",cookie).build(),object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceAgentConsoleSession) {
                if(!current(webSocket,g) || !identity()) {webSocket.cancel();return}
                val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source.extractRawToken(cookie))})}
                if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) retire("connection")
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceAgentConsoleSession) {
                if(!current(webSocket,g)) return
                if(!identity()) {retire("identity",true);return}
                if(text.length>2_000_000) {retire("invalid");return}
                runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {retire("invalid")}
            }}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceAgentConsoleSession) {if(current(webSocket,g)) retire("connection")}}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceAgentConsoleSession) {if(current(webSocket,g)) retire("connection")}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceAgentConsoleSession) {if(current(webSocket,g)) retire("connection")}}
        })} catch(_:Exception) {retire("connection")}
    }
    private fun receive(p:JsonObject) {
        when(p.get("action")?.asString) {
            "ping"->send(packet("pong"))
            "close","stopped"->retire("denied")
            "userinfo"->{
                if(p.getAsJsonObject("userinfo")?.get("_id")?.asString!=actor) {retire("identity",true);return}
                if(state.value.phase=="connecting") {state.value=state.value.copy(phase="checking");if(!read()) retire("connection")}
            }
            "getsysinfo"->if(tag.isNotBlank() && p.get("tag")?.asString==tag && p.get("nodeid")?.asString==node && state.value.phase in setOf("checking","checking_send")) {
                val received=generalMeta(p,node,tag) ?: run {retire("unverified");return}
                if(!agentConsoleAvailable(node,received,source.groupedDevices.value)) {retire("denied");return}
                agentVersion.value=agentConsoleServerVersion(p)
                meta=received;tag="";timer?.cancel()
                val command=pending
                val action=pendingAction
                if(command!=null) {
                    val change=agentConsolePacket(node,command,received,source.groupedDevices.value)
                    pending=null;state.value=state.value.copy(history=(state.value.history.filterNot {it==command}+command).takeLast(50))
                    val sent=send(change);append(if(sent) "queued" else "unknown",command)
                    if(!sent) {retire("connection");return}
                } else if(action!=null) {
                    val change=runCatching {agentActionPacket(node,action,received,source.groupedDevices.value,source.userInfo.value?.siteadmin ?: 0)}.getOrNull()
                    pendingAction=null
                    if(change==null) {append("not_sent",action.wire);retire("changed");return}
                    val sent=send(change);append(if(sent) "queued" else "unknown",action.wire)
                    if(!sent) {retire("connection");return}
                }
                state.value=state.value.copy(phase="ready",code="",rights=received.rights)
            }
            "msg"->if(meta!=null && state.value.phase in setOf("ready","checking_send")) agentConsoleOutput(p,node)?.let {append("output",it)}
        }
    }
    @Synchronized fun submit(command:String):Boolean {
        if(disposed || revoked || !identity() || !source.connectionState.value || state.value.phase!="ready" || !agentConsoleCommandValid(command) || pending!=null || pendingAction!=null) return false
        pending=command;state.value=state.value.copy(phase="checking_send")
        if(!read()) retire("connection")
        return true
    }
    @Synchronized fun perform(action:AgentConsoleAction):Boolean {
        if(disposed || revoked || !identity() || !source.connectionState.value || state.value.phase!="ready" || pending!=null || pendingAction!=null) return false
        val known=meta ?: return false
        if(runCatching {agentActionPacket(node,action,known,source.groupedDevices.value,source.userInfo.value?.siteadmin ?: 0)}.isFailure) return false
        pendingAction=action;state.value=state.value.copy(phase="checking_send")
        if(!read()) retire("connection")
        return true
    }
    @Synchronized fun clearOutput() {state.value=state.value.copy(lines=emptyList())}
    @Synchronized fun disconnect() {if(!disposed && state.value.phase!="closed") retire("local")}
    @Synchronized fun close() {if(disposed) return;retire("closed");disposed=true;jobs.cancel()}
}
