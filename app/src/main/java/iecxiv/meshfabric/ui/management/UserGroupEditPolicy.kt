package iecxiv.meshfabric.ui.management

import org.json.JSONObject

internal fun profileUserGroupEditRole(site:Long)=ProfilePermissions.normalized(site) and 256L!=0L
internal fun profileUserGroupNameValid(value:String)=value.isNotBlank() && value.length<=64 && !value.contains('\u0000')
internal fun profileUserGroupDescValid(value:String)=value.length<=1024 && !value.contains('\u0000')
internal fun profileUserGroupDesc(group:JSONObject):String=if(group.isNull("desc")) "" else group.getString("desc")
internal fun profileUserGroupBefore(group:JSONObject):JSONObject {
    require(group.opt("name") is String && (group.isNull("desc") || group.opt("desc") is String))
    return JSONObject().put("name",group.getString("name")).put("desc",if(group.isNull("desc")) JSONObject.NULL else group.getString("desc")).put("external",!group.isNull("membershipType"))
}
internal fun profileUserGroupEditContext(s:ProfileState,actor:String,id:String,before:JSONObject):Boolean=runCatching {
    require(s.canWrite && s.userGroupPhase=="ready" && s.user!=null && profileUserGroupEditRole(userAdvancedMask(s.user.opt("siteadmin"))))
    require(actor==s.id && profileUserGroupDomainAllowed(actor,id) && id.length<=1024 && id.split('/')[2].isNotBlank())
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==id})
    val current=profileUserGroupBefore(group)
    require(before.keys().asSequence().toSet()==setOf("name","desc","external"))
    require(listOf("name","desc","external").all {before.opt(it)==current.opt(it)})
    true
}.getOrDefault(false)
internal fun profileUserGroupEditPacket(actor:String,group:JSONObject,name:String,desc:String,confirmed:Boolean=false):JSONObject {
    val p=JSONObject().put("action","editusergroup").put("ugrpid",group.getString("_id")).put("_groupActor",actor).put("_groupBefore",profileUserGroupBefore(group)).put("_groupConfirmed",confirmed)
    if(name!=group.getString("name")) p.put("name",name)
    if(desc!=profileUserGroupDesc(group)) p.put("desc",desc)
    return p
}
internal fun profileUserGroupEditAllowed(s:ProfileState,p:JSONObject):Boolean=runCatching {
    require(p.optString("action")=="editusergroup" && p.opt("_groupConfirmed")==true)
    require(p.keys().asSequence().toSet().let {it.containsAll(setOf("action","ugrpid","_groupActor","_groupBefore","_groupConfirmed")) && it.all {key->key in setOf("action","ugrpid","_groupActor","_groupBefore","_groupConfirmed","name","desc")}})
    require(profileUserGroupEditContext(s,p.getString("_groupActor"),p.getString("ugrpid"),p.getJSONObject("_groupBefore")))
    val before=p.getJSONObject("_groupBefore")
    require(p.has("name") || p.has("desc"))
    if(p.has("name")) require(p.opt("name") is String && profileUserGroupNameValid(p.getString("name")) && !before.getBoolean("external") && p.getString("name")!=before.getString("name"))
    if(p.has("desc")) require(p.opt("desc") is String && profileUserGroupDescValid(p.getString("desc")) && p.getString("desc")!=(if(before.isNull("desc")) "" else before.getString("desc")))
    true
}.getOrDefault(false)
internal fun profileUserGroupEditWire(p:JSONObject):JSONObject=JSONObject().put("action","editusergroup").put("ugrpid",p.getString("ugrpid")).apply {listOf("name","desc").filter(p::has).forEach {put(it,p.getString(it))}}
internal fun profileUserGroupEditObserved(group:JSONObject,expected:JSONObject):Boolean=runCatching {expected.length()>0 && expected.keys().asSequence().all {key->key in setOf("name","desc") && group.opt(key) is String && group.getString(key)==expected.getString(key)}}.getOrDefault(false)
