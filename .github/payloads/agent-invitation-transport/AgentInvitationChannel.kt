package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import okio.ByteString
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

internal sealed class InvitationDelivery {
    data class Reply(val outcome:AgentInvitationOutcome,val observed:Boolean=false):InvitationDelivery()
    data class Shares(val records:List<JsonObject>):InvitationDelivery()
    object NotSent:InvitationDelivery()
    object ReadFailed:InvitationDelivery()
    object Unconfirmed:InvitationDelivery()
    object ContextChanged:InvitationDelivery()
}
internal interface InvitationScheduler {
    fun later(delay:Long,task:()->Unit)
    fun close()
}
internal class InvitationTimer:InvitationScheduler {
    private val timer=Timer("agent-invitation",true)
    override fun later(delay:Long,task:()->Unit) {timer.schedule(object:TimerTask() {override fun run()=task()},delay)}
    override fun close() {timer.cancel()}
}
internal class InvitationBinding(val server:String,val actor:String,private val cookie:String,val ignoreSsl:Boolean) {
    fun matches(source:MeshCentralClient)=source.connectionState.value && !source.isUserLogout && source.serverUrl==server && source.authCookie==cookie && source.ignoreSsl==ignoreSsl && source.userInfo.value?.id==actor
    fun request():Request=Request.Builder().url(OkHttpHelper.toWebSocketUrl(server,"/control.ashx?moreargs=1")).header("Cookie",cookie).build()
    override fun toString()="InvitationBinding(redacted)"
}

internal class AgentInvitationChannel(
    private val binding:InvitationBinding,
    private val factory:WebSocket.Factory,
    private val current:()->Boolean,
    private val result:(InvitationDelivery)->Unit,
    private val clock:InvitationScheduler=InvitationTimer()
) {
    private var used=false
    private var done=false
    private var sent=false
    private var phase=0
    private var socket:WebSocket?=null
    private var request=JsonObject()
    private var ack=JsonObject()
    private var action=""
    private var target=""
    private var nonce=""
    private var readNonce=""
    private var reads=0
    private var accepted:AgentInvitationOutcome?=null
    private fun text(o:JsonObject,k:String)=o.get(k)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString
    private fun number(o:JsonObject,k:String)=o.get(k)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isNumber}?.asString?.toLongOrNull()
    private fun bool(o:JsonObject,k:String)=o.get(k)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isBoolean}?.asBoolean
    private fun live()=runCatching {current()}.getOrDefault(false)
    @Synchronized fun start(packet:JsonObject):Boolean {
        if(used || done || !live()) return false
        val a=text(packet,"action") ?: return false
        if(a !in setOf("createInviteLink","inviteAgent","createDeviceShareLink","removeDeviceShare","deviceShares")) return false
        val kind=if(a in setOf("createInviteLink","inviteAgent")) "mesh" else "node"
        val t=text(packet,if(kind=="mesh") "meshid" else "nodeid") ?: return false
        val p=t.split('/');val actor=binding.actor.split('/')
        if(p.size!=3 || actor.size!=3 || actor[0]!="user" || p[0]!=kind || p[1]!=actor[1] || p[2].isBlank()) return false
        if(a=="createDeviceShareLink" && ((number(packet,"expire") ?: 0L)<=0L || (number(packet,"p") ?: 0L) !in 1L..7L)) return false
        used=true;action=a;target=t;nonce=UUID.randomUUID().toString()
        request=packet.deepCopy().apply {addProperty("responseid",nonce)}
        try {
            val created=factory.newWebSocket(binding.request(),listener)
            if(done) created.cancel() else socket=created
            if(!done) {clock.later(20_000) {synchronized(this) {if(!done) fail()}};watch()}
        } catch(_:Exception) {fail()}
        return true
    }
    private fun watch() {clock.later(250) {synchronized(this) {if(!done) {if(!live()) finish(InvitationDelivery.ContextChanged) else watch()}}}}
    private fun finish(value:InvitationDelivery) {
        if(done) return
        done=true;clock.close();socket?.cancel();socket=null
        request=JsonObject();ack=JsonObject();accepted=null
        result(value)
    }
    private fun fail()=finish(if(action=="deviceShares") InvitationDelivery.ReadFailed else if(sent) InvitationDelivery.Unconfirmed else InvitationDelivery.NotSent)
    @Synchronized fun close() {if(!done) fail()}
    private fun sendRead() {
        if(done || !live()) {finish(InvitationDelivery.ContextChanged);return}
        phase=2;reads++;readNonce=UUID.randomUUID().toString()
        val p=AgentInvitationContract.listShares(target,readNonce)
        if(socket?.send(p.toString())!=true) fail()
    }
    private fun records(p:JsonObject):List<JsonObject>? {
        val a=p.get("deviceShares")?.takeIf {it.isJsonArray}?.asJsonArray ?: return null
        if(a.size()>1000 || a.any {!it.isJsonObject}) return null
        val list=a.map {it.asJsonObject.deepCopy()}
        val ids=list.map {text(it,"publicid") ?: return null}
        if(ids.any {it.isBlank() || it.length>128} || ids.distinct().size!=ids.size) return null
        return list
    }
    private fun observe(p:JsonObject) {
        if(text(p,"responseid")==readNonce && p.has("result") && !text(p,"result").orEmpty().equals("ok",true)) {
            if(action=="deviceShares") finish(InvitationDelivery.Reply(AgentInvitationOutcome.Rejected(text(p,"result").orEmpty().take(200)))) else fail()
            return
        }
        if(text(p,"nodeid")!=target) return
        if(p.has("responseid") && text(p,"responseid")!=readNonce) return
        if(p.has("result") && !text(p,"result").orEmpty().equals("ok",true)) {fail();return}
        val list=records(p) ?: run {fail();return}
        if(action=="deviceShares") {finish(InvitationDelivery.Shares(list));return}
        val outcome=accepted
        val verified=when(outcome) {
            is AgentInvitationOutcome.ShareCreated->{
                val row=list.singleOrNull {text(it,"publicid")==outcome.publicId}
                if(row==null) false else {
                    val matches=number(row,"p")==number(request,"p") && text(row,"guestName")==text(request,"guestname") && number(row,"consent")==number(request,"consent") && (bool(row,"viewOnly") ?: false)==(bool(ack,"viewOnly") ?: false) && number(row,"expireTime")==number(ack,"expire") && (number(row,"expireTime") ?: 0)>System.currentTimeMillis() && (!row.has("url") || text(row,"url")==outcome.url)
                    if(!matches) {fail();return}
                    true
                }
            }
            AgentInvitationOutcome.ShareRemoved->list.none {text(it,"publicid")==text(request,"publicid")}
            else->false
        }
        if(verified && outcome!=null) finish(InvitationDelivery.Reply(outcome,true)) else if(reads<3) {
            phase=3;clock.later(500) {synchronized(this) {if(!done && phase==3) sendRead()}}
        } else fail()
    }
    private fun receive(p:JsonObject) {
        val a=text(p,"action") ?: return
        if(a=="ping") {if(socket?.send("{\"action\":\"pong\"}")!=true) fail();return}
        if(a in setOf("close","stopped")) {fail();return}
        if(a=="event") {
            val event=p.get("event")?.takeIf {it.isJsonObject}?.asJsonObject
            if(event!=null && text(event,"action") in setOf("accountchange","accountremove","meshchange","deletemesh","usergroupchange","changenode","removenode")) finish(InvitationDelivery.ContextChanged)
            return
        }
        if(a=="userinfo") {
            val user=p.get("userinfo")?.takeIf {it.isJsonObject}?.asJsonObject
            if(user==null || text(user,"_id")!=binding.actor) {finish(InvitationDelivery.ContextChanged);return}
            if(phase!=0) return
            if(!live()) {finish(InvitationDelivery.ContextChanged);return}
            if(action=="deviceShares") {sendRead();return}
            phase=1;sent=true
            try {if(socket?.send(request.toString())!=true) {sent=false;fail()}} catch(_:Exception) {fail()}
            return
        }
        if(phase==2 && a=="deviceShares") {observe(p);return}
        if(phase!=1) return
        val answer=AgentInvitationReplies.parse(action,target,nonce,p) ?: return
        when(answer) {
            is AgentInvitationOutcome.ShareCreated->{
                val expiry=number(p,"expire") ?: 0L
                val minutes=number(request,"expire") ?: 0L
                val now=System.currentTimeMillis()
                if(number(p,"p")!=number(request,"p") || text(p,"guestname")!=text(request,"guestname") || number(p,"consent")!=number(request,"consent") || (bool(request,"viewOnly")==true && bool(p,"viewOnly")!=true) || expiry<=now || minutes>Int.MAX_VALUE || expiry>now+minutes*60_000L+5000L) {fail();return}
                accepted=answer;ack=p.deepCopy();sendRead()
            }
            AgentInvitationOutcome.ShareRemoved->{accepted=answer;sendRead()}
            AgentInvitationOutcome.Malformed,AgentInvitationOutcome.Unknown->fail()
            else->finish(InvitationDelivery.Reply(answer))
        }
    }
    private val listener=object:WebSocketListener() {
        override fun onOpen(webSocket:WebSocket,response:Response) {synchronized(this@AgentInvitationChannel) {
            if(done || !live()) {webSocket.cancel();if(!done) finish(InvitationDelivery.ContextChanged);return}
            socket=webSocket
            if(!webSocket.send("{\"action\":\"userinfo\"}")) fail()
        }}
        override fun onMessage(webSocket:WebSocket,text:String) {synchronized(this@AgentInvitationChannel) {
            if(done) return
            if(!live()) {finish(InvitationDelivery.ContextChanged);return}
            if(text.length>2_000_000) {fail();return}
            val p=runCatching {JsonParser.parseString(text).takeIf {it.isJsonObject}?.asJsonObject}.getOrNull()
            if(p==null) {fail();return}
            receive(p)
        }}
        override fun onMessage(webSocket:WebSocket,bytes:ByteString) {synchronized(this@AgentInvitationChannel) {if(!done) fail()}}
        override fun onClosing(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@AgentInvitationChannel) {if(!done) fail()}}
        override fun onClosed(webSocket:WebSocket,code:Int,reason:String) {synchronized(this@AgentInvitationChannel) {if(!done) fail()}}
        override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {synchronized(this@AgentInvitationChannel) {if(!done) fail()}}
    }
    companion object {
        fun fromSource(source:MeshCentralClient,result:(InvitationDelivery)->Unit):AgentInvitationChannel? {
            val actor=source.userInfo.value?.id ?: return null
            val binding=InvitationBinding(source.serverUrl,actor,source.authCookie,source.ignoreSsl)
            if(actor.isBlank() || !binding.matches(source)) return null
            return AgentInvitationChannel(binding,OkHttpHelper.createClient(binding.ignoreSsl),{binding.matches(source)},result)
        }
    }
}
