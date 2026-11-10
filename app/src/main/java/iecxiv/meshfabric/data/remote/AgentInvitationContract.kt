package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object AgentInvitationContract {
    private const val FULL = 0xffffffffL
    private val binaryIds = setOf(6,5,10005,25,26,28,30,32,36,37,40,41,45,16,29)
    fun normalized(rights:Long)=rights and FULL
    fun groupAllowed(site:Long,rights:Long,type:Int,lanOnly:Boolean,invitation:Boolean):Boolean {
        val s=normalized(site)
        return type==2 && (!invitation || !lanOnly) && (s==FULL || (s and 4096L==0L && normalized(rights) and 4L!=0L))
    }
    fun sharingAllowed(rights:Long,protocols:Int,enabled:Boolean=true):Boolean {
        if(!enabled || protocols !in 1..7) return false
        val r=normalized(rights)
        if(r==FULL) return true
        if(r and 8L==0L || r and 524288L==0L || r and 4096L!=0L) return false
        return !(protocols and 1!=0 && r and 512L!=0L) && !(protocols and 2!=0 && r and 65536L!=0L) && !(protocols and 4!=0 && r and 1024L!=0L)
    }
    private fun id(value:String,kind:String):String {
        val parts=value.split('/')
        require(parts.size==3 && parts[0]==kind && parts[2].isNotEmpty() && value.length<=if(kind=="node") 128 else 134)
        require(parts[1].matches(Regex("[A-Za-z0-9_.-]*")) && parts[2].matches(Regex("[A-Za-z0-9_@+${'$'}=-]+")))
        return parts[2]
    }
    private fun packet(action:String,target:String,kind:String,responseId:String):JsonObject {
        id(target,kind); require(responseId.isNotBlank() && responseId.length<=128)
        return JsonObject().apply {addProperty("action",action);addProperty(if(kind=="mesh") "meshid" else "nodeid",target);addProperty("responseid",responseId)}
    }
    fun groupLink(mesh:String,hours:Int,flags:Int,agents:Int,responseId:String):JsonObject {
        require(hours in 0..99999 && flags in setOf(0,1,2) && agents in setOf(0,1,2,4,8,16))
        return packet("createInviteLink",mesh,"mesh",responseId).apply {addProperty("expire",hours);addProperty("flags",flags);addProperty("agents",agents)}
    }
    fun groupEmail(mesh:String,email:String,name:String,message:String,os:Int,flags:Int,hours:Int,responseId:String):JsonObject {
        require(email.length in 4..1024 && email.count {it=='@'}==1 && email.none {it.isWhitespace() || it.isISOControl()})
        require(email.substringBefore('@').isNotEmpty() && email.substringAfter('@').isNotEmpty())
        require(name.length<=64 && message.length<=1024 && os in 0..5 && flags in setOf(0,1,2) && hours in 0..99999)
        return packet("inviteAgent",mesh,"mesh",responseId).apply {addProperty("email",email);addProperty("name",name);addProperty("msg",message);addProperty("os",os.toString());addProperty("flags",flags.toString());addProperty("expire",hours)}
    }
    fun temporaryShare(node:String,guest:String,minutes:Int,protocols:Int,rights:Long,maxMinutes:Int?,prompt:Boolean,viewOnly:Boolean,responseId:String):JsonObject {
        require(guest.isNotBlank() && guest.length<=128 && guest.none {it.isISOControl()})
        require(minutes>0 && (maxMinutes==null || maxMinutes<=0 || minutes<=maxMinutes))
        require(sharingAllowed(rights,protocols))
        var consent=0
        if(protocols and 1!=0) consent=consent or if(prompt) 18 else 2
        if(protocols and 2!=0) consent=consent or if(prompt) 73 else 65
        if(protocols and 4!=0) consent=consent or if(prompt) 36 else 4
        val forcedViewOnly=normalized(rights)!=FULL && normalized(rights) and 256L!=0L
        return packet("createDeviceShareLink",node,"node",responseId).apply {addProperty("guestname",guest.trim());addProperty("expire",minutes);addProperty("p",protocols);addProperty("consent",consent);addProperty("viewOnly",viewOnly || forcedViewOnly)}
    }
    fun listShares(node:String,responseId:String)=packet("deviceShares",node,"node",responseId)
    fun revoke(node:String,publicId:String,responseId:String):JsonObject {
        require(publicId.length in 1..128 && publicId.none {it.isISOControl()})
        return packet("removeDeviceShare",node,"node",responseId).apply {addProperty("publicid",publicId)}
    }
    fun download(base:String,mesh:String,platform:String,architecture:Int,mode:Int=0):String {
        val mid=id(mesh,"mesh");require(mode in 0..4)
        val url=(base.trimEnd('/')+"/").toHttpUrl().newBuilder()
        when(platform) {
            "windows"->{require(architecture in setOf(3,4,43) && mode in 0..2);url.addPathSegment("meshagents").addQueryParameter("id",architecture.toString()).addQueryParameter("meshid",mid).addQueryParameter("installflags",mode.toString())}
            "macos"->{require(architecture in setOf(16,29,10005));url.addPathSegment("meshosxagent").addQueryParameter("id",architecture.toString()).addQueryParameter("meshid",mid)}
            "binary"->{require(architecture in binaryIds && mode in 0..2);url.addPathSegment("meshagents").addQueryParameter("id",mid).addQueryParameter("meshinstall",architecture.toString()).addQueryParameter("installflags",mode.toString())}
            "assistant"->url.addPathSegment("meshagents").addQueryParameter("id","10006").addQueryParameter("meshid",mid).addQueryParameter("ac",mode.toString())
            "android"->url.addPathSegment("meshagents").addQueryParameter("id","14")
            else->error("Unsupported platform")
        }
        return url.build().toString()
    }
    fun mobileConfiguration(agentUrl:String,certificateHash:String,mesh:String):String {
        val mid=id(mesh,"mesh")
        require(agentUrl.startsWith("mc://") && agentUrl.length<=2048 && agentUrl.none {it==',' || it.isWhitespace() || it.isISOControl()})
        require(certificateHash.matches(Regex("[A-Za-z0-9@${'$'}+/=_-]+")) && certificateHash.length in 32..256)
        return "$agentUrl,$certificateHash,$mid"
    }
    fun isCorrelatedFailure(expectedAction:String,responseId:String,reply:JsonObject):Boolean {
        val action=reply.get("action")?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString ?: return false
        val rid=reply.get("responseid")?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString ?: return false
        val result=reply.get("result")?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString ?: return false
        return rid==responseId && responseId.isNotBlank() && (action==expectedAction || (expectedAction in setOf("createDeviceShareLink","removeDeviceShare") && action=="deviceShares")) && !result.equals("ok",true)
    }
}
