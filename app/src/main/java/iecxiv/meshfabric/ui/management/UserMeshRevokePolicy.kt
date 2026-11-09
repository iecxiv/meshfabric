package iecxiv.meshfabric.ui.management
import org.json.JSONObject

internal fun profileUserMeshRevokeSubject(actor:String,uid:String):Boolean {
    val a=actor.split('/');val u=uid.split('/')
    return a.size==3 && a[0]=="user" && a[2].isNotBlank() && u.size==3 && u[0]=="user" && u[2].isNotBlank() && u[1]==a[1] && uid.length<=1024 && uid!=actor && !uid.contains('\u0000')
}
internal fun profileUserMeshRevokePacket(actor:String,server:String,user:JSONObject,mesh:JSONObject,confirmed:Boolean=false)=JSONObject().put("action","removemeshuser").put("userid",user.getString("_id")).put("meshid",mesh.getString("_id")).put("_directActor",actor).put("_directServer",server).put("_directUserBefore",profileUserGroupDeleteSnapshot(user)).put("_directMeshBefore",profileUserGroupDeleteSnapshot(mesh)).put("_directConfirmed",confirmed)
internal fun profileUserMeshRevokeAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","userid","meshid","_directActor","_directServer","_directUserBefore","_directMeshBefore","_directConfirmed"))
    require(p.optString("action")=="removemeshuser" && p.opt("_directConfirmed")==true && s.canWrite && !s.detailsBusy)
    require(p.getString("_directActor")==s.id && server.isNotBlank() && p.getString("_directServer")==server)
    val uid=p.getString("userid");val mid=p.getString("meshid")
    require(profileUserMeshRevokeSubject(s.id,uid) && profileGroupMeshId(s.id,mid))
    val user=requireNotNull(s.users.singleOrNull {it.optString("_id")==uid})
    val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==mid})
    require(canAdministerUser(s,user))
    require(p.getString("_directUserBefore")==profileUserGroupDeleteSnapshot(user) && p.getString("_directMeshBefore")==profileUserGroupDeleteSnapshot(mesh))
    val a=user.getJSONObject("links").getJSONObject(mid).get("rights")
    val b=mesh.getJSONObject("links").getJSONObject(uid).get("rights")
    val authority=mesh.getJSONObject("links").getJSONObject(s.id).get("rights")
    require(a is Number && b is Number && authority is Number)
    val rights=userAdvancedMask(a)
    require(rights==userAdvancedMask(b))
    profileGroupMeshRevokeAuthority(userAdvancedMask(authority),rights)
}.getOrDefault(false)
internal fun profileUserMeshRevokeWire(p:JSONObject)=JSONObject().put("action","removemeshuser").put("userid",p.getString("userid")).put("meshid",p.getString("meshid"))
internal fun profileUserMeshRevokeAck(p:JSONObject,request:String)=profileGroupMeshRevokeAck(p,request)
internal fun profileUserMeshRevokeReadbackReady(s:ProfileState,uid:String,mid:String,ack:Boolean,userRead:Boolean,meshRead:Boolean):Boolean {
    if(!ack || !userRead || !meshRead || !s.ready || s.readError || !profileUserMeshRevokeSubject(s.id,uid) || !profileGroupMeshId(s.id,mid)) return false
    return profileGroupMeshRevokeObserved(s.users.singleOrNull {it.optString("_id")==uid},s.groups.singleOrNull {it.optString("_id")==mid},uid,mid,true)
}

internal fun profileUserMeshRevokeChannel(action:String,direct:Boolean)=action=="removemeshuser" && direct
