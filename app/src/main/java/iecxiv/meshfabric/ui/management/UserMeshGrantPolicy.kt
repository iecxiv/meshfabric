package iecxiv.meshfabric.ui.management
import org.json.JSONArray
import org.json.JSONObject

internal fun profileUserMeshInitialGrantRights(value:Long)=profileGroupMeshInitialGrantRights(value)
internal fun profileUserMeshGrantChannel(action:String,direct:Boolean)=action=="addmeshuser" && direct
internal fun profileUserMeshGrantPacket(actor:String,server:String,user:JSONObject,mesh:JSONObject,rights:Long,confirmed:Boolean=false)=JSONObject().put("action","addmeshuser").put("meshid",mesh.getString("_id")).put("userids",JSONArray().put(user.getString("_id"))).put("meshadmin",rights).put("_userGrantActor",actor).put("_userGrantServer",server).put("_userGrantUserBefore",profileUserGroupDeleteSnapshot(user)).put("_userGrantMeshBefore",profileUserGroupDeleteSnapshot(mesh)).put("_userGrantConfirmed",confirmed)
internal fun profileUserMeshGrantAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","meshid","userids","meshadmin","_userGrantActor","_userGrantServer","_userGrantUserBefore","_userGrantMeshBefore","_userGrantConfirmed"))
    require(p.optString("action")=="addmeshuser" && p.opt("_userGrantConfirmed")==true && s.canWrite && !s.detailsBusy)
    require(s.user!=null && ProfilePermissions.manageUsers(userAdvancedMask(s.user.opt("siteadmin"))))
    require(p.getString("_userGrantActor")==s.id && server.isNotBlank() && p.getString("_userGrantServer")==server)
    val ids=p.getJSONArray("userids");require(ids.length()==1 && ids.opt(0) is String)
    val uid=ids.getString(0);val mid=p.getString("meshid")
    require(uid.length<=128 && profileUserMeshRevokeSubject(s.id,uid) && profileGroupMeshId(s.id,mid))
    val user=requireNotNull(s.users.singleOrNull {it.optString("_id")==uid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    require(canAdministerUser(s,user))
    require(p.getString("_userGrantUserBefore")==profileUserGroupDeleteSnapshot(user) && p.getString("_userGrantMeshBefore")==profileUserGroupDeleteSnapshot(mesh))
    require(!user.getJSONObject("links").has(mid) && !mesh.getJSONObject("links").has(uid))
    val authority=mesh.getJSONObject("links").getJSONObject(s.id).get("rights")
    require(authority is Number && userAdvancedMask(authority)==0xffffffffL)
    val rights=p.get("meshadmin");require(rights is Number)
    profileUserMeshInitialGrantRights(userAdvancedMask(rights))
}.getOrDefault(false)
internal fun profileUserMeshGrantWire(p:JSONObject)=JSONObject().put("action","addmeshuser").put("meshid",p.getString("meshid")).put("userids",JSONArray().put(p.getJSONArray("userids").getString(0))).put("meshadmin",userAdvancedMask(p.get("meshadmin")))
internal fun profileUserMeshGrantAck(p:JSONObject,request:String)=profileGroupMeshGrantAck(p,request)
internal fun profileUserMeshGrantReadbackReady(s:ProfileState,uid:String,mid:String,rights:Long,ack:Boolean,userRead:Boolean,meshRead:Boolean):Boolean {
    if(!ack || !userRead || !meshRead || !s.ready || s.loading || s.readError || !profileUserMeshRevokeSubject(s.id,uid) || !profileGroupMeshId(s.id,mid)) return false
    return profileGroupMeshGrantObserved(s.users.singleOrNull {it.optString("_id")==uid},s.groups.singleOrNull {it.optString("_id")==mid},uid,mid,rights,true)
}
