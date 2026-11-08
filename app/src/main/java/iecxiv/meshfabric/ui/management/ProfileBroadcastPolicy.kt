package iecxiv.meshfabric.ui.management

import org.json.JSONArray
import org.json.JSONObject

internal fun profileBroadcastMessageValid(value:String)=value.isNotBlank() && value.length<=512 && !value.contains('\u0000')
internal fun profileBroadcastRoleAllowed(site:Long)=ProfilePermissions.manageUsers(site)
internal fun profileBroadcastServerLabel(raw:String):String=runCatching {
    val uri=java.net.URI(raw)
    val scheme=when(uri.scheme) { "wss"->"https";"ws"->"http";"http","https"->uri.scheme;else->error("Unsupported scheme") }
    require(!uri.host.isNullOrBlank())
    java.net.URI(scheme,null,uri.host,uri.port,uri.path.orEmpty().removeSuffix("control.ashx"),null,null).toString()
}.getOrDefault("")
internal fun profileBroadcastScope(actor:JSONObject,server:String):JSONObject {
    val id=actor.optString("_id")
    require(id.startsWith("user/") && id.split('/').size==3 && id.split('/')[2].isNotBlank() && server.isNotBlank())
    return JSONObject().put("actor",id).put("server",server).put("realms",JSONArray(userRealmValues(actor.opt("groups"))))
}
internal fun profileBroadcastScopeAllowed(s:ProfileState,scope:JSONObject,server:String):Boolean=runCatching {
    require(s.canWrite && s.user!=null && profileBroadcastRoleAllowed(userAdvancedMask(s.user.opt("siteadmin"))))
    require(server.isNotBlank() && scope.keys().asSequence().toSet()==setOf("actor","server","realms"))
    require(scope.opt("actor") is String && scope.getString("actor")==s.id && scope.opt("server") is String && scope.getString("server")==server)
    require(scope.opt("realms") is JSONArray)
    require(userRealmValues(scope.opt("realms"))==userRealmValues(s.user.opt("groups")))
    profileBroadcastScope(s.user,server)
    true
}.getOrDefault(false)
internal fun profileBroadcastPacket(scope:JSONObject,message:String,confirmed:Boolean=false)=JSONObject().put("action","userbroadcast").put("msg",message).put("_broadcastScope",JSONObject(scope.toString())).put("_broadcastConfirmed",confirmed)
internal fun profileBroadcastAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","msg","_broadcastScope","_broadcastConfirmed"))
    require(p.optString("action")=="userbroadcast" && p.opt("msg") is String && profileBroadcastMessageValid(p.getString("msg")) && p.opt("_broadcastConfirmed")==true)
    profileBroadcastScopeAllowed(s,p.getJSONObject("_broadcastScope"),server)
}.getOrDefault(false)
