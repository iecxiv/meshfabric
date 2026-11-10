package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal sealed class AgentInvitationOutcome {
    data class GroupLink(val url:String,val hours:Int):AgentInvitationOutcome()
    data class Rejected(val reason:String):AgentInvitationOutcome()
    object EmailAccepted:AgentInvitationOutcome()
    object Unknown:AgentInvitationOutcome()
    object Malformed:AgentInvitationOutcome()
}

internal class AgentInvitationExchange(val action:String,val target:String,val responseId:String) {
    init {require(action in setOf("createInviteLink","inviteAgent") && target.startsWith("mesh/") && responseId.isNotBlank())}
    private var finished=false
    @Synchronized fun receive(reply:JsonObject):AgentInvitationOutcome? {
        if(finished) return null
        val outcome=AgentInvitationReplies.parse(action,target,responseId,reply) ?: return null
        finished=true
        return outcome
    }
    @Synchronized fun expire():AgentInvitationOutcome? {
        if(finished) return null
        finished=true
        return AgentInvitationOutcome.Unknown
    }
}

internal object AgentInvitationReplies {
    private fun text(o:JsonObject,key:String):String? = o.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun whole(o:JsonObject,key:String):Int? {
        val e=o.get(key) ?: return null
        if(!e.isJsonPrimitive || !e.asJsonPrimitive.isNumber) return null
        val d=e.asDouble
        return if(d>=0.0 && d<=99999.0 && d%1.0==0.0) d.toInt() else null
    }
    private fun safeUrl(value:String,leaf:String):String? {
        if(value.length>4096 || value.any { it.isISOControl() || it.isWhitespace() }) return null
        val url=value.toHttpUrlOrNull() ?: return null
        if(url.scheme!="https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if(!url.encodedPath.endsWith("/"+leaf) || url.queryParameter("c").isNullOrBlank()) return null
        return value
    }
    fun parse(expected:String,target:String,responseId:String,reply:JsonObject):AgentInvitationOutcome? {
        if(expected !in setOf("createInviteLink","inviteAgent")) return AgentInvitationOutcome.Malformed
        if(responseId.isBlank() || text(reply,"responseid")!=responseId) return null
        val action=text(reply,"action") ?: return null
        if(action!=expected) return null
        val result=text(reply,"result")
        if(reply.has("result") && result==null) return AgentInvitationOutcome.Malformed
        if(result!=null && !result.equals("ok",true)) return AgentInvitationOutcome.Rejected(result.take(200))
        return when(expected) {
            "createInviteLink"->{
                val hours=whole(reply,"expire")
                val url=text(reply,"url")?.let { safeUrl(it,"agentinvite") }
                if(text(reply,"meshid")==target && hours!=null && url!=null) AgentInvitationOutcome.GroupLink(url,hours) else AgentInvitationOutcome.Malformed
            }
            "inviteAgent"->if(result!=null) AgentInvitationOutcome.EmailAccepted else AgentInvitationOutcome.Malformed
            else->AgentInvitationOutcome.Malformed
        }
    }
}
