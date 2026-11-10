package iecxiv.meshfabric.ui.management
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.UUID
internal data class LogEventsBinding(val server:String,val actor:String,val cookie:String,val ignoreSsl:Boolean,val site:Int,val auth:String)
internal class LogEventsReader(private val current:()->LogEventsBinding?,private val open:(LogEventsBinding,WebSocketListener)->WebSocket={b,l->
    OkHttpHelper.createClient(b.ignoreSsl).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(b.server,"/control.ashx?moreargs=1")).header("Cookie",b.cookie).build(),l)
}) {
    private val jobs=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutable=MutableStateFlow(ServerEventsSnapshot())
    val state=mutable.asStateFlow()
    private val gate=ServerEventsReadGate()
    private var socket:WebSocket?=null
    private var binding:LogEventsBinding?=null
    private var epoch=0L
    private var attempt=0L
    private var queryScope:ServerEventsScope?=null
    private var requested=legacyServerEventsQuery
    private var timer:Job?=null
    init {jobs.launch {while(isActive) {delay(500);synchronized(this@LogEventsReader) {if(binding!=null && current()!=binding) stop(ServerEventsPhase.INVALIDATED)}}}}
    @Synchronized fun load(selection:ServerEventsQuery=legacyServerEventsQuery) {
        requested=selection
        stop(ServerEventsPhase.IDLE)
        val b=current() ?: run {mutable.value=ServerEventsSnapshot(ServerEventsPhase.INVALIDATED,selection=requested);return}
        if(b.server.isBlank() || b.actor.isBlank() || b.cookie.isBlank()) {mutable.value=ServerEventsSnapshot(ServerEventsPhase.INVALIDATED,selection=requested);return}
        binding=b;val generation=epoch
        mutable.value=ServerEventsSnapshot(ServerEventsPhase.LOADING,selection=requested)
        runCatching {socket=open(b,object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@LogEventsReader) {
                if(generation!=epoch || current()!=binding) {webSocket.cancel();return}
                val args=JsonObject().apply {addProperty("action","urlargs");add("args",JsonObject().apply {addProperty("auth",b.auth)})}
                if(!webSocket.send(args.toString()) || !webSocket.send("{\"action\":\"userinfo\"}")) stop(ServerEventsPhase.ERROR)
            }}
            override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@LogEventsReader) {
                if(generation!=epoch) return
                if(current()!=binding) {stop(ServerEventsPhase.INVALIDATED);return}
                if(requested.limit!=null && text.length>2_000_000) {invalid(ServerEventsIssue(ServerEventsIssueKind.FRAME_SIZE,length=text.length));return}
                val parsed=try {JsonParser.parseString(text)} catch(_:Exception) {invalid(ServerEventsIssue(ServerEventsIssueKind.JSON));return}
                if(!parsed.isJsonObject) {invalid(ServerEventsIssue(ServerEventsIssueKind.ENVELOPE,type=eventValueType(parsed)));return}
                val packet=parsed.asJsonObject
                try {receive(webSocket,packet)} catch(e:ServerEventsDecodeException) {invalid(e.issue)} catch(_:Exception) {
                    val identity=packet.get("action")?.let {it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString=="userinfo"}==true
                    invalid(ServerEventsIssue(if(identity) ServerEventsIssueKind.IDENTITY else ServerEventsIssueKind.INTERNAL))
                }
            }}
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {webSocket.close(code,"");failed(generation)}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {failed(generation)}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {failed(generation)}
        })}.onFailure {stop(ServerEventsPhase.ERROR)}
        if(binding!=null) timer=jobs.launch {delay(20_000);synchronized(this@LogEventsReader) {if(generation==epoch) timeout()}}
    }
    private fun receive(ws:WebSocket,p:JsonObject) {
        val b=binding ?: return
        fun str(o:JsonObject,k:String)=o.get(k)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString.orEmpty()
        when(str(p,"action")) {
            "ping"->if(!ws.send("{\"action\":\"pong\"}")) stop(ServerEventsPhase.ERROR)
            "userinfo"->{
                val rawUser=p.get("userinfo")
                if(rawUser==null || !rawUser.isJsonObject) eventDecodeFailure(ServerEventsIssueKind.IDENTITY,"userinfo",rawUser)
                val user=rawUser.asJsonObject
                val site=user.get("siteadmin")?.asLong?.toInt() ?: 0
                if(str(user,"_id")!=b.actor || site!=b.site) {stop(ServerEventsPhase.INVALIDATED);return}
                val authority=site.toString()+":"+(user.get("links")?.toString() ?: "unavailable")
                val previous=queryScope
                if(previous!=null) {if(previous.authority!=authority) stop(ServerEventsPhase.INVALIDATED);return}
                val scope=ServerEventsScope(b.server,b.actor,epoch,authority);queryScope=scope
                val tag=UUID.randomUUID().toString();attempt=gate.begin(scope,tag,requested)
                if(!ws.send(serverEventsRequest(tag,requested).toString())) stop(ServerEventsPhase.ERROR)
            }
            "events"->{
                val scope=queryScope ?: return
                if(gate.receive(attempt,scope,p)) {timer?.cancel();mutable.value=gate.state.copy(selection=requested)}
            }
            "event"->{
                val event=p.get("event")?.takeIf {it.isJsonObject}?.asJsonObject
                if(event!=null && str(event,"action") in setOf("accountchange","accountremove","usergroupchange","usergroupdelete","meshchange","deletemesh")) stop(ServerEventsPhase.INVALIDATED)
            }
            "close","stopped"->stop(ServerEventsPhase.ERROR)
        }
    }
    private fun invalid(issue:ServerEventsIssue) {stop(ServerEventsPhase.INVALID);mutable.value=ServerEventsSnapshot(ServerEventsPhase.INVALID,issue=issue,selection=requested)}
    @Synchronized fun timeout() {if(mutable.value.phase==ServerEventsPhase.LOADING) stop(ServerEventsPhase.TIMEOUT)}
    @Synchronized private fun failed(generation:Long) {if(generation==epoch) stop(ServerEventsPhase.ERROR)}
    @Synchronized private fun stop(phase:ServerEventsPhase) {
        epoch++;timer?.cancel();timer=null;binding=null;queryScope=null;gate.invalidate()
        val previous=socket;socket=null;mutable.value=ServerEventsSnapshot(phase,selection=requested);previous?.cancel()
    }
    @Synchronized fun pause(selection:ServerEventsQuery?=null) {if(selection==null || selection==requested) stop(ServerEventsPhase.INVALIDATED)}
    @Synchronized fun close() {pause();jobs.cancel()}
}
