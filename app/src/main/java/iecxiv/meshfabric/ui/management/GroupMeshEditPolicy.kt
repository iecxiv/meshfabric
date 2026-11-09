package iecxiv.meshfabric.ui.management
import org.json.JSONArray
import org.json.JSONObject

internal fun profileGroupMeshEditedRights(before:Long,selected:Long)=profileUserMeshEditedRights(before,selected)
internal fun profileGroupMeshEditPacket(actor:String,server:String,subject:JSONObject,mesh:JSONObject,rights:Long,confirmed:Boolean=false)=JSONObject().put("action","addmeshuser").put("meshid",mesh.getString("_id")).put("userids",JSONArray().put(subject.getString("_id"))).put("meshadmin",rights).put("_groupEditActor",actor).put("_groupEditServer",server).put("_groupEditUserBefore",profileUserGroupDeleteSnapshot(subject)).put("_groupEditMeshBefore",profileUserGroupDeleteSnapshot(mesh)).put("_groupEditConfirmed",confirmed)
internal fun profileGroupMeshEditAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","meshid","userids","meshadmin","_groupEditActor","_groupEditServer","_groupEditUserBefore","_groupEditMeshBefore","_groupEditConfirmed"))
    require(s.canWrite && !s.detailsBusy && s.userGroupPhase=="ready" && p.optString("action")=="addmeshuser" && p.opt("_groupEditConfirmed")==true)
    require(s.user!=null && profileUserGroupEditRole(userAdvancedMask(s.user.opt("siteadmin"))))
    require(p.getString("_groupEditActor")==s.id && server.isNotBlank() && p.getString("_groupEditServer")==server)
    val ids=p.getJSONArray("userids");require(ids.length()==1 && ids.opt(0) is String)
    val gid=ids.getString(0);val mid=p.getString("meshid")
    require(gid.length<=128 && profileCreatedUserGroupId(s.id,gid) && profileGroupMeshId(s.id,mid))
    val subject=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    require(p.getString("_groupEditUserBefore")==profileUserGroupDeleteSnapshot(subject) && p.getString("_groupEditMeshBefore")==profileUserGroupDeleteSnapshot(mesh))
    val a=subject.getJSONObject("links").getJSONObject(mid).get("rights")
    val b=mesh.getJSONObject("links").getJSONObject(gid).get("rights")
    val authority=mesh.getJSONObject("links").getJSONObject(s.id).get("rights")
    val desired=p.get("meshadmin")
    require(a is Number && b is Number && authority is Number && desired is Number)
    val before=userAdvancedMask(a);val next=userAdvancedMask(desired)
    require(before==userAdvancedMask(b) && userAdvancedMask(authority)==0xffffffffL && before!=next)
    profileGroupMeshEditedRights(before,next and USER_MESH_EDITABLE_MASK)==next
}.getOrDefault(false)
internal fun profileGroupMeshEditWire(p:JSONObject)=JSONObject().put("action","addmeshuser").put("meshid",p.getString("meshid")).put("userids",JSONArray().put(p.getJSONArray("userids").getString(0))).put("meshadmin",userAdvancedMask(p.get("meshadmin")))
internal fun profileGroupMeshEditAck(p:JSONObject,request:String)=profileGroupMeshGrantAck(p,request)
internal fun profileGroupMeshEditReadbackReady(s:ProfileState,gid:String,mid:String,rights:Long,ack:Boolean,userRead:Boolean,meshRead:Boolean):Boolean=runCatching {
    require(ack && userRead && meshRead && s.ready && !s.loading && !s.readError && s.userGroupPhase=="ready" && rights in 1L..0xfffffffeL)
    require(profileCreatedUserGroupId(s.id,gid) && profileGroupMeshId(s.id,mid))
    val subject=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    val a=subject.getJSONObject("links").getJSONObject(mid).get("rights")
    val b=mesh.getJSONObject("links").getJSONObject(gid).get("rights")
    a is Number && b is Number && userAdvancedMask(a)==rights && userAdvancedMask(b)==rights
}.getOrDefault(false)
