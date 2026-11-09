package iecxiv.meshfabric.ui.management

import org.json.JSONArray
import org.json.JSONObject

internal fun profileGroupMeshInitialGrantRights(value:Long)=value>0L && value and 0x00fffff8L==value
internal fun profileGroupMeshGrantAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","meshid","userids","meshadmin","_grantActor","_grantServer","_grantGroupBefore","_grantDeviceBefore","_grantConfirmed"))
    require(p.optString("action")=="addmeshuser" && p.opt("_grantConfirmed")==true)
    require(s.canWrite && s.userGroupPhase=="ready" && s.user!=null && profileUserGroupEditRole(userAdvancedMask(s.user.opt("siteadmin"))))
    require(p.getString("_grantActor")==s.id && server.isNotBlank() && p.getString("_grantServer")==server)
    val ids=p.getJSONArray("userids");require(ids.length()==1 && ids.opt(0) is String)
    val gid=ids.getString(0);val mid=p.getString("meshid")
    require(gid.length<=128 && profileCreatedUserGroupId(s.id,gid) && profileGroupMeshId(s.id,mid))
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    require(p.getString("_grantGroupBefore")==profileUserGroupDeleteSnapshot(group) && p.getString("_grantDeviceBefore")==profileUserGroupDeleteSnapshot(mesh))
    require(!group.getJSONObject("links").has(mid) && !mesh.getJSONObject("links").has(gid))
    val authority=mesh.getJSONObject("links").getJSONObject(s.id).get("rights")
    require(authority is Number && userAdvancedMask(authority)==0xffffffffL)
    val raw=p.get("meshadmin");require(raw is Number)
    profileGroupMeshInitialGrantRights(userAdvancedMask(raw))
}.getOrDefault(false)
internal fun profileGroupMeshGrantWire(p:JSONObject)=JSONObject().put("action","addmeshuser").put("meshid",p.getString("meshid")).put("userids",JSONArray().put(p.getJSONArray("userids").getString(0))).put("meshadmin",userAdvancedMask(p.get("meshadmin")))
internal fun profileGroupMeshGrantCount(value:Any?,expected:Int)=value is Number && value.toDouble()==expected.toDouble()
internal fun profileGroupMeshGrantAck(p:JSONObject,request:String)=request.isNotBlank() && p.optString("action")=="addmeshuser" && p.optString("responseid")==request && !p.has("error") && !p.has("err") && profileGroupMeshGrantCount(p.opt("success"),1) && profileGroupMeshGrantCount(p.opt("failed"),0)
internal fun profileGroupMeshGrantObserved(group:JSONObject?,mesh:JSONObject?,gid:String,mid:String,rights:Long,ack:Boolean):Boolean=runCatching {
    require(ack && profileGroupMeshInitialGrantRights(rights) && group!=null && mesh!=null && group.optString("_id")==gid && mesh.optString("_id")==mid)
    val a=group.getJSONObject("links").getJSONObject(mid).get("rights")
    val b=mesh.getJSONObject("links").getJSONObject(gid).get("rights")
    a is Number && b is Number && userAdvancedMask(a)==rights && userAdvancedMask(b)==rights
}.getOrDefault(false)
