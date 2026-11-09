package iecxiv.meshfabric.ui.management
import com.google.gson.JsonObject
import java.time.Instant
internal const val SERVER_EVENTS_LIMIT=200
internal data class ServerEventsScope(val server:String,val actor:String,val session:Long,val authority:String)
internal data class ServerEventRecord(val action:String,val message:String,val actor:String,val nodeId:String,val nodeName:String,val time:Long?)
internal enum class ServerEventsPhase { IDLE, LOADING, READY, DENIED, ERROR, INVALID, TIMEOUT, INVALIDATED }
internal data class ServerEventsSnapshot(val phase:ServerEventsPhase=ServerEventsPhase.IDLE,val records:List<ServerEventRecord> = emptyList())
internal fun serverEventsRequest(tag:String):JsonObject {
    require(tag.isNotBlank() && tag.length<=128)
    return JsonObject().apply {addProperty("action","events");addProperty("tag",tag);addProperty("limit",SERVER_EVENTS_LIMIT)}
}
private fun JsonObject.eventText(key:String):String {
    val value=get(key) ?: return ""
    require(value.isJsonNull || (value.isJsonPrimitive && value.asJsonPrimitive.isString))
    return if(value.isJsonNull) "" else value.asString.also {require(it.length<=4096)}
}
private fun eventTime(item:JsonObject):Long?=runCatching {
    val value=item.get("time") ?: return@runCatching null
    if(!value.isJsonPrimitive) return@runCatching null
    val raw=value.asString
    val number=raw.toDoubleOrNull()
    if(number==null) Instant.parse(raw).toEpochMilli() else {
        require(number.isFinite() && number>0)
        val millis=if(number<100000000000.0) number*1000 else number
        require(millis<Long.MAX_VALUE.toDouble())
        millis.toLong()
    }
}.getOrNull()
internal class ServerEventsReadGate {
    private var epoch=0L
    private var binding:ServerEventsScope?=null
    private var tag=""
    @Volatile var state=ServerEventsSnapshot()
        private set
    @Synchronized fun begin(scope:ServerEventsScope,requestTag:String):Long {
        require(scope.server.isNotBlank() && scope.actor.isNotBlank() && scope.authority.isNotBlank())
        serverEventsRequest(requestTag)
        epoch++;binding=scope;tag=requestTag
        state=ServerEventsSnapshot(ServerEventsPhase.LOADING)
        return epoch
    }
    @Synchronized fun invalidate() {
        epoch++;binding=null;tag=""
        state=ServerEventsSnapshot(ServerEventsPhase.INVALIDATED)
    }
    @Synchronized fun timeout(attempt:Long) {
        if(attempt==epoch && state.phase==ServerEventsPhase.LOADING) {
            epoch++;tag="";state=ServerEventsSnapshot(ServerEventsPhase.TIMEOUT)
        }
    }
    @Synchronized fun receive(attempt:Long,scope:ServerEventsScope,packet:JsonObject):Boolean {
        if(attempt!=epoch || state.phase!=ServerEventsPhase.LOADING) return false
        if(scope!=binding) {invalidate();return false}
        fun field(key:String)=runCatching {packet.eventText(key)}.getOrDefault("")
        if(field("action")!="events" || field("tag")!=tag) return false
        val result=field("result").lowercase()
        if(packet.has("error") || packet.has("err") || (result.isNotBlank() && result!="ok")) {
            val denied=result in setOf("denied","forbidden","access denied") || field("error").lowercase() in setOf("denied","forbidden","access denied")
            state=ServerEventsSnapshot(if(denied) ServerEventsPhase.DENIED else ServerEventsPhase.ERROR)
            return true
        }
        val records=runCatching {
            val value=packet.get("events")
            require(value!=null && value.isJsonArray && value.asJsonArray.size()<=SERVER_EVENTS_LIMIT)
            value.asJsonArray.map {element->
                require(element.isJsonObject)
                val item=element.asJsonObject
                ServerEventRecord(item.eventText("action"),item.eventText("msg"),item.eventText("username"),item.eventText("nodeid"),item.eventText("nodename"),eventTime(item))
            }.sortedWith(compareByDescending<ServerEventRecord> {it.time ?: Long.MIN_VALUE})
        }.getOrNull()
        state=if(records==null) ServerEventsSnapshot(ServerEventsPhase.INVALID) else ServerEventsSnapshot(ServerEventsPhase.READY,records)
        return true
    }
}
