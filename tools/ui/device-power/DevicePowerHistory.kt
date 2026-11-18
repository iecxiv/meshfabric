package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import java.time.*
import java.util.UUID

internal data class PowerPoint(val at:Long,val state:Int)
internal data class PowerHistory(val points:List<PowerPoint>,val sampledAt:Long,val currentOnly:Boolean=false)
internal data class PowerSpan(val start:Long,val end:Long,val state:Int?,val future:Boolean=false)
internal data class PowerLoad(val key:String,val phase:String="loading",val history:PowerHistory?=null)
internal fun decodePower(a:JsonArray,now:Long):PowerHistory {
    require(now>=0 && a.size()<=50001)
    if(a.size()==0) return PowerHistory(emptyList(),now)
    require(a.size()>=3 && a.size()%2==1)
    fun number(i:Int):Double { val v=a[i];require(v.isJsonPrimitive && v.asJsonPrimitive.isNumber);return v.asDouble.also { require(it.isFinite() && it>=0) } }
    fun state(i:Int)=number(i).also { require(it%1.0==0.0 && it<=255) }.toInt()
    state(0)
    val first=number(1)
    if(first>=100000000000.0) {
        require(a.size()==3 && state(0)==state(2) && first<=now.toDouble())
        return PowerHistory(listOf(PowerPoint(first.toLong(),state(2))),now,true)
    }
    fun millis(v:Double):Long { require(v<=Long.MAX_VALUE.toDouble()/1000);return (v*1000).toLong() }
    var time=millis(first)
    require(time<=now)
    val points=mutableListOf(PowerPoint(time,state(2)))
    for(i in 3 until a.size() step 2) {
        val delta=millis(number(i));require(delta<=now-time);time+=delta
        points.add(PowerPoint(time,state(i+1)))
    }
    return PowerHistory(points,now)
}
internal fun powerDays(now:Long,zone:ZoneId):List<LocalDate> { val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();return (6 downTo 0).map { today.minusDays(it.toLong()) } }
internal fun powerSpans(h:PowerHistory,start:Long,end:Long):List<PowerSpan> {
    require(end>start)
    val limit=minOf(end,h.sampledAt)
    if(limit<=start) return listOf(PowerSpan(start,end,null,true))
    var at=start
    var state=h.points.lastOrNull { it.at<=start }?.state
    val spans=mutableListOf<PowerSpan>()
    for(p in h.points) if(p.at>start && p.at<limit) { if(p.at>at) spans.add(PowerSpan(at,p.at,state));at=p.at;state=p.state }
    if(at<limit) spans.add(PowerSpan(at,limit,state))
    if(limit<end) spans.add(PowerSpan(limit,end,null,true))
    return spans
}
internal class PowerBinding(val source:MeshCentralClient,val node:String) {
    val url=source.serverUrl;val cookie=source.authCookie;val tls=source.ignoreSsl
    private val actor=source.userInfo.value?.id
    fun matches()=!source.isUserLogout && source.serverUrl==url && source.authCookie==cookie && source.ignoreSsl==tls && source.userInfo.value?.id==actor
}
internal fun devicePowerTimeline(b:PowerBinding,key:String)=callbackFlow {
    val lock=Any();var active=true;var started=false;var socket:WebSocket?=null
    val tag=UUID.randomUUID().toString()
    fun finish(value:PowerLoad)=synchronized(lock) { if(active) { active=false;trySend(value);close();socket?.cancel() };Unit }
    val timeout=launch { delay(25000);finish(PowerLoad(key,"timeout")) }
    val identity=launch { while(isActive) { delay(500);if(!b.matches() || !b.source.connectionState.value) finish(PowerLoad(key,"disconnected")) } }
    try {
        if(!b.matches() || b.cookie.isBlank() || !b.source.connectionState.value) finish(PowerLoad(key,"disconnected"))
        else {
            socket=OkHttpHelper.createClient(b.tls).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(b.url,"/control.ashx?moreargs=1")).header("Cookie",b.cookie).build(),object:WebSocketListener() {
                override fun onOpen(ws:WebSocket,response:Response) { synchronized(lock) {
                    if(!active || !b.matches()) { ws.cancel();return }
                    val auth=JsonObject().apply { addProperty("action","urlargs");add("args",JsonObject().apply { addProperty("auth",b.source.extractRawToken(b.cookie)) }) }
                    if(!ws.send(auth.toString()) || !ws.send("{\"action\":\"userinfo\"}")) finish(PowerLoad(key,"connection"))
                } }
                override fun onMessage(ws:WebSocket,text:String) { synchronized(lock) {
                    if(!active || !b.matches()) { finish(PowerLoad(key,"disconnected"));return }
                    if(text.length>2000000) { finish(PowerLoad(key,"invalid"));return }
                    val p=runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
                    runCatching { when(p.get("action")?.asString) {
                        "ping"->ws.send("{\"action\":\"pong\"}")
                        "close","stopped"->finish(PowerLoad(key,"denied"))
                        "userinfo"->if(!started && !p.getAsJsonObject("userinfo")?.get("_id")?.asString.isNullOrBlank()) {
                            started=true
                            val request=JsonObject().apply { addProperty("action","powertimeline");addProperty("nodeid",b.node);addProperty("tag",tag) }
                            if(!ws.send(request.toString())) finish(PowerLoad(key,"connection"))
                        }
                        "powertimeline"->if(p.get("nodeid")?.asString==b.node && p.get("tag")?.asString==tag) {
                            if(p.has("error") || (p.has("result") && p.get("result").asString!="ok")) finish(PowerLoad(key,"denied"))
                            else finish(PowerLoad(key,"ready",decodePower(p.getAsJsonArray("timeline"),System.currentTimeMillis())))
                        }
                    } }.onFailure { finish(PowerLoad(key,"invalid")) }
                } }
                override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) { finish(PowerLoad(key,"connection")) }
                override fun onClosing(ws:WebSocket,code:Int,reason:String) { finish(PowerLoad(key,"connection"));ws.close(code,"") }
                override fun onClosed(ws:WebSocket,code:Int,reason:String) { finish(PowerLoad(key,"connection")) }
            })
            synchronized(lock) { if(!active) socket?.cancel() }
        }
    } catch(_:Exception) { finish(PowerLoad(key,"connection")) }
    awaitClose { synchronized(lock) { active=false;socket?.cancel() };timeout.cancel();identity.cancel() }
}
