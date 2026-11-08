package iecxiv.meshfabric.ui.management

import org.json.JSONArray
import org.json.JSONObject

internal fun profileUserGroupDeleteTyped(expected:String,typed:String)=expected.isNotBlank() && expected==typed
private fun groupDeleteSorted(value:Any?,depth:Int):Any {
    require(depth<=32)
    return when(value) {
        null,JSONObject.NULL->JSONObject.NULL
        is JSONObject->JSONObject().apply {for(key in value.keys().asSequence().sorted()) put(key,groupDeleteSorted(value.get(key),depth+1))}
        is JSONArray->JSONArray().apply {for(i in 0 until value.length()) put(groupDeleteSorted(value.get(i),depth+1))}
        is String,is Boolean,is Number->requireNotNull(value)
        else->error("Unknown JSON value")
    }
}
internal fun profileUserGroupDeleteSnapshot(group:JSONObject)=groupDeleteSorted(group,0).toString()
internal fun profileUserGroupDeleteShape(actor:String,group:JSONObject):Boolean=runCatching {
    require(profileCreatedUserGroupId(actor,group.getString("_id")) && group.opt("name") is String && group.getString("name").isNotBlank())
    val links=group.getJSONObject("links")
    val ids=links.keys().asSequence().toList()
    require(ids.all {id->id.split('/').size==3 && id.split('/')[2].isNotBlank() && userDomain(id)==userDomain(actor) && (id.startsWith("user/") || id.startsWith("mesh/")) && links.opt(id) is JSONObject})
    require(group.isNull("membershipType") || ids.none {it.startsWith("user/")})
    profileUserGroupDeleteSnapshot(group)
    true
}.getOrDefault(false)
internal fun profileUserGroupDeleteContext(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(s.canWrite && s.userGroupPhase=="ready" && s.user!=null && profileUserGroupEditRole(userAdvancedMask(s.user.opt("siteadmin"))))
    require(p.getString("_deleteActor")==s.id && server.isNotBlank() && p.getString("_deleteServer")==server)
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==p.getString("ugrpid")})
    require(profileUserGroupDeleteShape(s.id,group) && p.getString("_deleteBefore")==profileUserGroupDeleteSnapshot(group))
    true
}.getOrDefault(false)
internal fun profileUserGroupDeletePacket(actor:String,server:String,group:JSONObject,typed:String,confirmed:Boolean=false)=JSONObject().put("action","deleteusergroup").put("ugrpid",group.getString("_id")).put("_deleteActor",actor).put("_deleteServer",server).put("_deleteBefore",profileUserGroupDeleteSnapshot(group)).put("_deleteTyped",typed).put("_deleteConfirmed",confirmed)
internal fun profileUserGroupDeleteAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","ugrpid","_deleteActor","_deleteServer","_deleteBefore","_deleteTyped","_deleteConfirmed"))
    require(p.optString("action")=="deleteusergroup" && p.opt("_deleteConfirmed")==true && profileUserGroupDeleteContext(s,p,server))
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==p.getString("ugrpid")})
    p.opt("_deleteTyped") is String && profileUserGroupDeleteTyped(group.getString("name"),p.getString("_deleteTyped"))
}.getOrDefault(false)
internal fun profileUserGroupDeleteWire(p:JSONObject)=JSONObject().put("action","deleteusergroup").put("ugrpid",p.getString("ugrpid"))
internal fun profileUserGroupDeleteAck(target:String,returned:Any?)=target.isNotBlank() && returned is String && returned==target
