package iecxiv.meshfabric.ui.management
import org.json.JSONArray
import org.json.JSONObject

internal const val USER_MESH_EDITABLE_MASK=0x00fffff8L
internal fun profileUserMeshEditedRights(before:Long,selected:Long):Long? {
    if(before !in 0L..0xfffffffeL || selected<0L || selected and USER_MESH_EDITABLE_MASK!=selected) return null
    val result=(before and USER_MESH_EDITABLE_MASK.inv() and 0xffffffffL) or selected
    return result.takeIf {it in 1L..0xfffffffeL}
}
internal fun profileUserMeshEditPacket(actor:String,server:String,user:JSONObject,mesh:JSONObject,rights:Long,confirmed:Boolean=false)=JSONObject().put("action","addmeshuser").put("meshid",mesh.getString("_id")).put("userids",JSONArray().put(user.getString("_id"))).put("meshadmin",rights).put("_userEditActor",actor).put("_userEditServer",server).put("_userEditUserBefore",profileUserGroupDeleteSnapshot(user)).put("_userEditMeshBefore",profileUserGroupDeleteSnapshot(mesh)).put("_userEditConfirmed",confirmed)
internal fun profileUserMeshEditAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","meshid","userids","meshadmin","_userEditActor","_userEditServer","_userEditUserBefore","_userEditMeshBefore","_userEditConfirmed"))
    require(s.canWrite && !s.detailsBusy && p.optString("action")=="addmeshuser" && p.opt("_userEditConfirmed")==true)
    require(s.user!=null && ProfilePermissions.manageUsers(userAdvancedMask(s.user.opt("siteadmin"))))
    require(p.getString("_userEditActor")==s.id && server.isNotBlank() && p.getString("_userEditServer")==server)
    val ids=p.getJSONArray("userids");require(ids.length()==1 && ids.opt(0) is String)
    val uid=ids.getString(0);val mid=p.getString("meshid")
    require(uid.length<=128 && profileUserMeshRevokeSubject(s.id,uid) && profileGroupMeshId(s.id,mid))
    val user=requireNotNull(s.users.singleOrNull {it.optString("_id")==uid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    require(canAdministerUser(s,user))
    require(p.getString("_userEditUserBefore")==profileUserGroupDeleteSnapshot(user) && p.getString("_userEditMeshBefore")==profileUserGroupDeleteSnapshot(mesh))
    val a=user.getJSONObject("links").getJSONObject(mid).get("rights")
    val b=mesh.getJSONObject("links").getJSONObject(uid).get("rights")
    val authority=mesh.getJSONObject("links").getJSONObject(s.id).get("rights")
    val desired=p.get("meshadmin")
    require(a is Number && b is Number && authority is Number && desired is Number)
    val before=userAdvancedMask(a);val next=userAdvancedMask(desired)
    require(before==userAdvancedMask(b) && userAdvancedMask(authority)==0xffffffffL && before!=next)
    profileUserMeshEditedRights(before,next and USER_MESH_EDITABLE_MASK)==next
}.getOrDefault(false)
internal fun profileUserMeshEditWire(p:JSONObject)=JSONObject().put("action","addmeshuser").put("meshid",p.getString("meshid")).put("userids",JSONArray().put(p.getJSONArray("userids").getString(0))).put("meshadmin",userAdvancedMask(p.get("meshadmin")))
internal fun profileUserMeshEditAck(p:JSONObject,request:String)=profileUserMeshGrantAck(p,request)
internal fun profileUserMeshEditReadbackReady(s:ProfileState,uid:String,mid:String,rights:Long,ack:Boolean,userRead:Boolean,meshRead:Boolean):Boolean=runCatching {
    require(ack && userRead && meshRead && s.ready && !s.loading && !s.readError && rights in 1L..0xfffffffeL)
    require(profileUserMeshRevokeSubject(s.id,uid) && profileGroupMeshId(s.id,mid))
    val user=requireNotNull(s.users.singleOrNull {it.optString("_id")==uid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    val a=user.getJSONObject("links").getJSONObject(mid).get("rights")
    val b=mesh.getJSONObject("links").getJSONObject(uid).get("rights")
    a is Number && b is Number && userAdvancedMask(a)==rights && userAdvancedMask(b)==rights
}.getOrDefault(false)
