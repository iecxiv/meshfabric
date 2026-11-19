package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.*
import iecxiv.meshfabric.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.UUID

internal enum class ManagedBatchAction(val wire:String) {
    CLEAR_CORE("uploadagentcore"), DEFAULT_CORE("uploadagentcore"), UNINSTALL("uninstallagent"), REMOVE("removedevices")
}
internal data class ManagedBatchDraft(val action:ManagedBatchAction,val actor:String,val node:String,val mesh:String,val os:String)
internal data class ManagedBatchState(val phase:String="loading",val draft:ManagedBatchDraft?=null,val code:String="")
internal fun managedBatchAvailable(node:String,meta:GeneralMeta,inventory:List<Pair<MeshGroup,List<MeshNode>>>,action:ManagedBatchAction):Boolean {
    if(meta.rights !in -1L..0xffffffffL || (meta.rights and 0xffffffffL)!=0xffffffffL) return false
    val group=inventory.singleOrNull {it.first.id==meta.mesh} ?: return false
    val current=inventory.flatMap {it.second}.singleOrNull {it.id==node} ?: return false
    if(current.meshid!=meta.mesh || group.second.count {it.id==node}!=1 || group.first.mtype !in 1..4) return false
    return action==ManagedBatchAction.REMOVE || group.first.mtype==2 && (current.conn and 1)!=0
}
internal fun managedBatchPacket(draft:ManagedBatchDraft,actor:String,node:String,meta:GeneralMeta,
    inventory:List<Pair<MeshGroup,List<MeshNode>>>,id:String):JsonObject {
    require(deviceBatchAgentUpdateIdentity(actor,node) && draft.actor==actor && draft.node==node && draft.mesh==meta.mesh && draft.os==meta.os)
    val mesh=meta.mesh.split('/')
    require(mesh.size==3 && mesh[0]=="mesh" && mesh[1]==actor.split('/')[1] && mesh[2].isNotBlank() && meta.mesh.none {it.isISOControl()})
    require(id.isNotBlank() && id.length<=128 && id.none {it.isISOControl()})
    require(managedBatchAvailable(node,meta,inventory,draft.action))
    return JsonObject().apply {
        addProperty("action",draft.action.wire);add("nodeids",JsonArray().apply {add(node)});addProperty("responseid",id)
        when(draft.action) {ManagedBatchAction.CLEAR_CORE->addProperty("type","clear");ManagedBatchAction.DEFAULT_CORE->addProperty("type","default");else->Unit}
    }
}
internal fun managedBatchAck(p:JsonObject,draft:ManagedBatchDraft,id:String):String?=runCatching {
    if(id.isBlank() || p.get("responseid")?.asString!=id || p.get("action")?.asString!=draft.action.wire) return@runCatching null
    p.get("nodeid")?.let {if(it.asString!=draft.node) return@runCatching null}
    p.get("nodeids")?.let {if(!it.isJsonArray || it.asJsonArray.size()!=1 || it.asJsonArray[0].asString!=draft.node) return@runCatching null}
    if(p.has("error") || p.has("err")) return@runCatching "rejected"
    val result=p.get("result") ?: return@runCatching null
    if(!result.isJsonPrimitive || !result.asJsonPrimitive.isString) return@runCatching null
    when(result.asString.lowercase(java.util.Locale.ROOT)) {"ok"->"accepted";"denied","forbidden","error","access denied"->"rejected";else->null}
}.getOrNull()
internal fun managedBatchRemovalEvent(p:JsonObject,node:String):Boolean=runCatching {
    require(p.get("action")?.asString=="event")
    val event=p.getAsJsonObject("event")
    require(event.get("action")?.asString=="removenode")
    val raw=event.get("nodeid") ?: event.get("node") ?: return@runCatching false
    val id=if(raw.isJsonObject) raw.asJsonObject.get("_id")?.asString else raw.asString
    id==node
}.getOrDefault(false)
internal fun managedBatchRemovalObserved(accepted:Boolean,event:Boolean)=accepted && event

internal class DeviceManagedBatchEditor(private val source:MeshCentralClient,private val original:MeshNode,private val action:ManagedBatchAction) {
    private val mutable=MutableStateFlow(ManagedBatchState())
    val state=mutable.asStateFlow()
    private val identity=deviceBatchPowerIdentity(source)
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var socket:WebSocket?=null
    private var closed=false;private var started=false;private var requested=false;private var attempted=false
    private var accepted=false;private var removed=false;private var tag=""
    private var reviewed:ManagedBatchDraft?=null
    private val requestId=UUID.randomUUID().toString()
    private var timer:Job?=null
    private fun same()=deviceBatchPowerSame(source,identity)
    private fun packet(name:String)=JsonObject().apply {addProperty("action",name)}
    private fun send(p:JsonObject)=!closed && same() && socket?.send(p.toString())==true
    @Synchronized private fun finish(phase:String,code:String="") {
        if(closed) return
        closed=true;timer?.cancel();tag=""
        val previous=socket;socket=null
        if(phase=="queued") previous?.close(1000,"") else previous?.cancel()
        jobs.cancel();mutable.value=mutable.value.copy(phase=phase,code=code)
    }
    private fun fail(code:String)=finish("error",if(attempted) "unconfirmed" else code)
    private fun deadline(afterSend:Boolean=false) {
        timer?.cancel();timer=jobs.launch {delay(if(afterSend) 10000L else 25000L);synchronized(this@DeviceManagedBatchEditor) {
            if(!closed) {if(afterSend && action!=ManagedBatchAction.REMOVE) finish("queued","effect_unverified") else fail("timeout")}
        }}
    }
    private fun read():Boolean {
        tag=UUID.randomUUID().toString();deadline()
        return send(packet("getsysinfo").apply {addProperty("nodeid",original.id);addProperty("tag",tag);addProperty("nodeinfo",true);addProperty("cache",true)})
    }
    @Synchronized fun start() {
        if(started || closed) return
        started=true
        if(!same() || identity.cookie.isBlank() || !deviceBatchAgentUpdateIdentity(identity.actor,original.id)) {fail("identity");return}
        deadline()
        jobs.launch {while(isActive) {delay(100L);synchronized(this@DeviceManagedBatchEditor) {if(!closed && !same()) fail("identity")}}}
        try {socket=OkHttpHelper.createClient(identity.tls).newBuilder().followRedirects(false).followSslRedirects(false).build()
            .newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(identity.url,"/control.ashx?moreargs=1")).header("Cookie",identity.cookie).build(),object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@DeviceManagedBatchEditor) {
                    if(closed || !same()) {webSocket.cancel();return}
                    val auth=packet("urlargs").apply {add("args",JsonObject().apply {addProperty("auth",source.extractRawToken(identity.cookie))})}
                    if(!webSocket.send(auth.toString()) || !webSocket.send(packet("userinfo").toString())) fail("connection")
                }}
                override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@DeviceManagedBatchEditor) {
                    if(closed) return
                    if(!same() || text.length>2000000) {fail("invalid");return}
                    runCatching {receive(JsonParser.parseString(text).asJsonObject)}.onFailure {fail("invalid")}
                }}
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@DeviceManagedBatchEditor) {if(!closed) fail("connection")}}
                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceManagedBatchEditor) {if(!closed) fail("connection");webSocket.close(code,"")}}
                override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@DeviceManagedBatchEditor) {if(!closed) fail("connection")}}
            })} catch(_:Exception) {fail("connection")}
    }
    private fun receive(p:JsonObject) {
        val wire=p.get("action")?.asString
        when(wire) {
            "ping"->send(packet("pong"))
            "close","stopped"->fail("denied")
            "userinfo"->{
                if(p.getAsJsonObject("userinfo")?.get("_id")?.asString!=identity.actor) {fail("identity");return}
                if(mutable.value.phase=="loading" && tag.isBlank() && !read()) fail("connection")
            }
            "getsysinfo"->{
                if(tag.isBlank() || p.get("tag")?.asString!=tag || p.get("nodeid")?.asString!=original.id) return
                val meta=generalMeta(p,original.id,tag) ?: run {fail("unverified");return}
                tag="";timer?.cancel()
                if(meta.mesh!=original.meshid || !managedBatchAvailable(original.id,meta,source.groupedDevices.value,action)) {finish("skipped","unavailable");return}
                val draft=ManagedBatchDraft(action,identity.actor,original.id,meta.mesh,meta.os)
                if(!requested) {mutable.value=ManagedBatchState("ready",draft);return}
                if(draft!=reviewed) {fail("changed");return}
                val change=managedBatchPacket(draft,identity.actor,original.id,meta,source.groupedDevices.value,requestId)
                attempted=true
                if(!send(change)) {fail("connection");return}
                mutable.value=mutable.value.copy(phase="sent");deadline(true)
            }
            "event"->if(attempted && action==ManagedBatchAction.REMOVE && managedBatchRemovalEvent(p,original.id)) {
                removed=true;if(managedBatchRemovalObserved(accepted,removed)) {finish("removed");source.requestServerData()}
            }
            else->if(attempted && wire==action.wire) {
                val draft=reviewed ?: return
                when(managedBatchAck(p,draft,requestId)) {
                    "rejected"->finish("rejected","denied")
                    "accepted"->{
                        accepted=true
                        if(action!=ManagedBatchAction.REMOVE) finish("accepted","effect_unverified")
                        else if(managedBatchRemovalObserved(accepted,removed)) {finish("removed");source.requestServerData()}
                    }
                }
            }
        }
    }
    @Synchronized fun submit(draft:ManagedBatchDraft):Boolean {
        if(closed || requested || attempted || !same() || mutable.value.phase!="ready" || mutable.value.draft!=draft) return false
        requested=true;reviewed=draft;mutable.value=mutable.value.copy(phase="checking")
        if(!read()) {fail("connection");return false}
        return true
    }
    @Synchronized fun close() {if(!closed) fail("closed")}
}
