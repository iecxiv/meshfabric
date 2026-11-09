package iecxiv.meshfabric.ui.management

import org.json.JSONObject

internal fun profileUserGroupMemberId(actor:String,id:String)=actor.startsWith("user/") && actor.split('/').size==3 && id.startsWith("user/") && id.split('/').size==3 && id.split('/')[2].isNotBlank() && id.length<=256 && !id.contains('\u0000') && userDomain(actor)==userDomain(id)
internal fun profileUserGroupMemberRemoveContext(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(s.canWrite && s.userGroupPhase=="ready" && s.user!=null && profileUserGroupEditRole(userAdvancedMask(s.user.opt("siteadmin"))))
    require(p.getString("_memberActor")==s.id && server.isNotBlank() && p.getString("_memberServer")==server)
    require(profileUserGroupMemberId(s.id,p.getString("userid")) && profileCreatedUserGroupId(s.id,p.getString("ugrpid")))
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==p.getString("ugrpid")})
    require(group.isNull("membershipType") && p.getString("_memberBefore")==profileUserGroupDeleteSnapshot(group))
    val link=group.getJSONObject("links").getJSONObject(p.getString("userid"))
    require(!link.has("userid") || (link.opt("userid") is String && link.getString("userid")==p.getString("userid")))
    true
}.getOrDefault(false)
internal fun profileUserGroupMemberRemovePacket(actor:String,server:String,group:JSONObject,id:String,confirmed:Boolean=false)=JSONObject().put("action","removeuserfromusergroup").put("ugrpid",group.getString("_id")).put("userid",id).put("_memberActor",actor).put("_memberServer",server).put("_memberBefore",profileUserGroupDeleteSnapshot(group)).put("_memberConfirmed",confirmed)
internal fun profileUserGroupMemberRemoveAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","ugrpid","userid","_memberActor","_memberServer","_memberBefore","_memberConfirmed"))
    p.optString("action")=="removeuserfromusergroup" && p.opt("_memberConfirmed")==true && profileUserGroupMemberRemoveContext(s,p,server)
}.getOrDefault(false)
internal fun profileUserGroupMemberRemoveWire(p:JSONObject)=JSONObject().put("action","removeuserfromusergroup").put("ugrpid",p.getString("ugrpid")).put("userid",p.getString("userid"))
internal fun profileUserGroupMemberRemovalObserved(group:JSONObject,id:String,ack:Boolean)=ack && id.isNotBlank() && group.optJSONObject("links")?.let {!it.has(id)}==true
internal fun profileUserGroupMemberReadValid(p:JSONObject)=!p.has("error") && !p.has("err") && (!p.has("result") || p.optString("result").equals("ok",true))
