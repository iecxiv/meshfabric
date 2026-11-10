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
    object NotSent:InvitationDelivery()
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
    val diagnosticCode:String get()=if(phase==0) "INVITE_WAITING_USERINFO" else "INVITE_WAITING_LINK_REPLY"
    private var socket:WebSocket?=null
    private var request=JsonObject()
    private var action=""
    private var target=""
    private var nonce=""
    private fun text(o:JsonObject,k:String)=o.get(k)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString
    private fun live()=runCatching {current()}.getOrDefault(false)
    @Synchronized fun start(packet:JsonObject):Boolean {
        if(used || done || !live()) return false
        val a=text(packet,"action") ?: return false
        if(a!="createInviteLink" || packet.has("nodeid")) return false
        val t=text(packet,"meshid") ?: return false
        if(runCatching {AgentInvitationContract.installationTarget(binding.actor,t)}.isFailure) return false
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
        request=JsonObject()
        result(value)
    }
    private fun fail()=finish(if(sent) InvitationDelivery.Unconfirmed else InvitationDelivery.NotSent)
    @Synchronized fun close() {if(!done) fail()}
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
            phase=1;sent=true
            try {if(socket?.send(request.toString())!=true) {sent=false;fail()}} catch(_:Exception) {fail()}
            return
        }
        if(phase!=1) return
        val answer=AgentInvitationReplies.parse(action,target,nonce,p) ?: return
        if(answer==AgentInvitationOutcome.Malformed || answer==AgentInvitationOutcome.Unknown) fail() else finish(InvitationDelivery.Reply(answer))
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
