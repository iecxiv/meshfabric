package iecxiv.meshfabric.ui.management

import org.json.JSONObject

internal fun profileGroupMeshId(actor:String,id:String)=actor.startsWith("user/") && actor.split('/').size==3 && id.startsWith("mesh/") && id.split('/').size==3 && id.split('/')[2].isNotBlank() && id.length in 8..134 && userDomain(actor)==userDomain(id) && !id.contains('\u0000')
internal fun profileGroupMeshRevokeAuthority(direct:Long,subject:Long):Boolean {
    val actor=ProfilePermissions.normalized(direct);val grant=ProfilePermissions.normalized(subject)
    return actor and 2L!=0L && (grant!=0xffffffffL || actor==0xffffffffL)
}
internal fun profileGroupMeshRevokeAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","meshid","userid","_meshActor","_meshServer","_meshGroupBefore","_meshDeviceBefore","_meshConfirmed"))
    require(p.optString("action")=="removemeshuser" && p.opt("_meshConfirmed")==true)
    require(s.canWrite && s.userGroupPhase=="ready" && s.user!=null && profileUserGroupEditRole(userAdvancedMask(s.user.opt("siteadmin"))))
    require(p.getString("_meshActor")==s.id && server.isNotBlank() && p.getString("_meshServer")==server)
    val mid=p.getString("meshid");val gid=p.getString("userid")
    require(profileGroupMeshId(s.id,mid) && profileCreatedUserGroupId(s.id,gid))
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    require(p.getString("_meshGroupBefore")==profileUserGroupDeleteSnapshot(group) && p.getString("_meshDeviceBefore")==profileUserGroupDeleteSnapshot(mesh))
    val outgoing=group.getJSONObject("links").getJSONObject(mid)
    val incoming=mesh.getJSONObject("links").getJSONObject(gid)
    val actor=mesh.getJSONObject("links").getJSONObject(s.id)
    require(outgoing.opt("rights") is Number && incoming.opt("rights") is Number && actor.opt("rights") is Number)
    val grant=userAdvancedMask(outgoing.get("rights"))
    require(grant==userAdvancedMask(incoming.get("rights")))
    profileGroupMeshRevokeAuthority(userAdvancedMask(actor.get("rights")),grant)
}.getOrDefault(false)
internal fun profileGroupMeshRevokeWire(p:JSONObject)=JSONObject().put("action","removemeshuser").put("meshid",p.getString("meshid")).put("userid",p.getString("userid"))
internal fun profileGroupMeshRevokeAck(p:JSONObject,request:String)=request.isNotBlank() && p.optString("action")=="removemeshuser" && p.optString("responseid")==request && p.optString("result")=="ok" && !p.has("error") && !p.has("err")
internal fun profileGroupMeshRevokeObserved(group:JSONObject?,mesh:JSONObject?,gid:String,mid:String,ack:Boolean):Boolean=runCatching {
    require(ack && group!=null && mesh!=null && group.optString("_id")==gid && mesh.optString("_id")==mid)
    !group.getJSONObject("links").has(mid) && !mesh.getJSONObject("links").has(gid)
}.getOrDefault(false)
