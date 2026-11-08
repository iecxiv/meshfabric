package iecxiv.meshfabric.ui.management

import org.json.JSONObject

internal fun profileUserGroupAddToken(id:String):String? {
    val parts=id.split('/')
    if(parts.size!=3 || parts[0]!="user") return null
    val token=parts[2]
    return token.takeIf {it.length in 1..128 && it.all {c->c.code in 33..126 && c!='/' && c !in 'A'..'Z'}}
}
internal fun profileUserGroupMemberAddAllowed(s:ProfileState,p:JSONObject,server:String):Boolean=runCatching {
    require(p.keys().asSequence().toSet()==setOf("action","ugrpid","usernames","_addActor","_addServer","_addGroupBefore","_addUser","_addUserBefore","_addConfirmed"))
    require(p.optString("action")=="addusertousergroup" && p.opt("_addConfirmed")==true)
    require(s.canWrite && s.userGroupPhase=="ready" && s.user!=null && profileUserGroupEditRole(userAdvancedMask(s.user.opt("siteadmin"))) && ProfilePermissions.manageUsers(s.site))
    require(p.getString("_addActor")==s.id && server.isNotBlank() && p.getString("_addServer")==server)
    val gid=p.getString("ugrpid");val uid=p.getString("_addUser")
    require(profileCreatedUserGroupId(s.id,gid) && profileUserGroupMemberId(s.id,uid))
    val group=requireNotNull(s.listedUserGroups?.singleOrNull {it.optString("_id")==gid})
    val account=requireNotNull(s.users.singleOrNull {it.optString("_id")==uid})
    require(group.isNull("membershipType") && p.getString("_addGroupBefore")==profileUserGroupDeleteSnapshot(group) && p.getString("_addUserBefore")==profileUserGroupDeleteSnapshot(account))
    require(!group.getJSONObject("links").has(uid) && !account.getJSONObject("links").has(gid))
    val names=p.getJSONArray("usernames");val token=requireNotNull(profileUserGroupAddToken(uid))
    require(names.length()==1 && names.opt(0) is String && names.getString(0)==token)
    true
}.getOrDefault(false)
internal fun profileUserGroupMemberAddWire(p:JSONObject)=JSONObject().put("action","addusertousergroup").put("ugrpid",p.getString("ugrpid")).put("usernames",org.json.JSONArray().put(p.getJSONArray("usernames").getString(0)))
internal fun profileUserGroupMemberAddAck(p:JSONObject):Boolean=runCatching {p.optString("result")=="ok" && !p.has("error") && !p.has("err") && userAdvancedMask(p.opt("added"))==1L && p.has("added") && p.has("failed") && userAdvancedMask(p.opt("failed"))==0L}.getOrDefault(false)
internal fun profileUserGroupMemberAddObserved(group:JSONObject,uid:String,ack:Boolean):Boolean=runCatching {
    require(ack)
    val link=group.getJSONObject("links").getJSONObject(uid)
    require(!link.has("userid") || (link.opt("userid") is String && link.getString("userid")==uid))
    link.has("rights") && userAdvancedMask(link.opt("rights"))==1L
}.getOrDefault(false)
