package iecxiv.meshfabric.ui.management

import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class ProfileState(
val ready:Boolean=false,val loading:Boolean=false,val busy:Boolean=false,
val user:JSONObject?=null,val users:List<JSONObject> = emptyList(),val groups:List<JSONObject> = emptyList(),
val notice:String="disconnected",val readError:Boolean=false,val server:JSONObject?=null,
val nodes:List<JSONObject>?=null,val userGroups:List<JSONObject>?=null,val sessions:JSONObject?=null,
val detailsBusy:Boolean=false,val historyUser:String="",val history:JSONArray?=null,val historyBusy:Boolean=false,val historyFailed:Boolean=false,
val listedUserGroups:List<JSONObject>?=null,val userGroupPhase:String="idle",val createdUserGroup:String="",val deletedUserGroup:String="",val removedMember:String="",val removedMemberGroup:String="",val addedMember:String="",val addedMemberGroup:String="",val revokedGroup:String="",val revokedDeviceGroup:String="",val grantedGroup:String="",val grantedDeviceGroup:String="",val revokedDirectUser:String="",val revokedDirectMesh:String="",val grantedDirectUser:String="",val grantedDirectMesh:String="",val editedDirectUser:String="",val editedDirectMesh:String="",val editedGroup:String="",val editedDeviceGroup:String="",val advancedEditedDeviceGroup:String=""
) {
val site:Long get()=user?.optLong("siteadmin",0L) ?: 0L
val id:String get()=user?.optString("_id").orEmpty()
val canWrite:Boolean get()=ready && !loading && !busy && !readError
val emailIdentity:Boolean get()=(server?.optLong("features",0L) ?: 0L) and 0x200000L != 0L
val canCreateUser:Boolean get()=ProfilePermissions.manageUsers(site) && (server?.optLong("features",0L) ?: 0L) and 0x80004L == 0L && server?.opt("domainauth")!=true && server?.opt("domainauth") !is String
fun rights(group:JSONObject):Long=group.optJSONObject("links")?.optJSONObject(id)?.optLong("rights",0L) ?: 0L
}

internal class ProfileSession(private val source:MeshCentralClient) {
val state=MutableStateFlow(ProfileState())
private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
private var socket:WebSocket?=null
private var generation=0L
private var binding=Triple("","",false)
private var closed=false
private var pendingId=""
private var pendingAction=""
private var pendingTarget=""
private var pendingNode=""
private var expected=JSONObject()
private var timer:Job?=null
private var readTimer:Job?=null
private var detailsTimer:Job?=null
private var historyTimer:Job?=null
private var historyId=""
private var directoryId=""
private var directoryTimer:Job?=null
private val reads=mutableSetOf<String>()
private val listTags=mutableMapOf<String,String>()
private val details=mutableSetOf<String>()
private fun directMeshRevocation()=profileUserMeshRevokeChannel(pendingAction,expected.optBoolean("directMesh"))
private fun directMeshGrant()=profileUserMeshGrantChannel(pendingAction,expected.optBoolean("directGrant"))
private fun directMeshEdit()=pendingAction=="addmeshuser" && expected.optBoolean("directEdit")
private fun groupMeshEdit()=pendingAction=="addmeshuser" && expected.optBoolean("groupEdit")
private fun advancedOperation()=pendingAction=="editmesh" && expected.optBoolean("advancedOperation")
private fun advancedAuthority():Boolean=runCatching {
val s=state.value;val p=expected.getJSONObject("advancedRequest")
require(valid() && s.ready && s.id==p.getString("_advancedActor") && broadcastServerLabel()==p.getString("_advancedServer"))
require(deviceGroupAdvancedContext(s)==p.getString("_advancedContext"))
val mesh=requireNotNull(s.groups.singleOrNull {it.optString("_id")==p.getString("meshid")})
require(deviceGroupAdvancedValue(mesh,"mtype")==JSONObject(p.getString("_advancedBefore")).getLong("mtype"))
val raw=mesh.optJSONObject("links")?.optJSONObject(s.id)?.opt("rights")
val rights=if(raw is Number) userAdvancedMask(raw) else 0L
ProfilePermissions.editGroup(userAdvancedMask(s.user?.opt("siteadmin")),rights)
}.getOrDefault(false)
private fun observeAdvancedSettings() {
if(!advancedOperation()) return
if(!advancedAuthority()) {stop();return}
val p=expected.getJSONObject("advancedRequest");val ack=expected.optBoolean("advancedAck");val read=expected.optBoolean("advancedMeshRead")
val snapshot=state.value.copy(groups=listOfNotNull(expected.optJSONObject("advancedMeshRecord")))
if(!deviceGroupAdvancedReadbackReady(snapshot,p,ack,read) || !deviceGroupAdvancedReadbackReady(state.value,p,ack,read)) return
state.update {it.copy(advancedEditedDeviceGroup=p.getString("meshid"))}
finish("deviceGroupAdvancedObserved",false);source.requestServerData()
}
private fun directMeshOperation()=directMeshRevocation() || directMeshGrant() || directMeshEdit()
private fun valid()=!source.isUserLogout && source.connectionState.value && binding==Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
private fun send(action:String,fields:JSONObject=JSONObject()):Boolean=socket?.send(JSONObject(fields.toString()).put("action",action).toString())==true
@Synchronized fun broadcastServerLabel():String=if(!valid()) "" else runCatching { profileBroadcastServerLabel(OkHttpHelper.toWebSocketUrl(binding.first,"/control.ashx")) }.getOrDefault("")
@Synchronized fun resetUserMfa(before:JSONObject,actor:String,site:Long,server:String,actorBefore:JSONObject?,serverBefore:String,confirmed:Boolean):Boolean {
val s=state.value
if(!confirmed || !valid() || !userMfaResetContext(s,before,actor,site,server,broadcastServerLabel(),true,actorBefore,serverBefore)) return false
val packet=userMfaResetPacket(before)
pendingId=UUID.randomUUID().toString();pendingAction="mfareset";pendingTarget=before.getString("_id");pendingNode="";expected=JSONObject()
state.update {it.copy(busy=true,notice="sending")}
val epoch=generation
timer?.cancel()
if(!send("changeuserpass",packet.put("responseid",pendingId))) {stop();return false}
timer=scope.launch {delay(20_000);synchronized(this@ProfileSession) {if(epoch==generation && pendingAction=="mfareset") finish("unknown",false)}}
return true
}
@Synchronized fun start() {
if(closed || socket!=null || source.isUserLogout || !source.connectionState.value || source.authCookie.isBlank()) return
binding=Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
val epoch=++generation
state.value=ProfileState(notice="connecting")
runCatching {
socket=OkHttpHelper.createClient(binding.third).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(binding.first,"/control.ashx?moreargs=1")).header("Cookie",binding.second).build(),object:WebSocketListener() {
override fun onOpen(webSocket:WebSocket,response:Response) { synchronized(this@ProfileSession) {
if(epoch!=generation || !valid()) { webSocket.cancel(); return }
webSocket.send(JSONObject().put("action","urlargs").put("args",JSONObject().put("auth",source.extractRawToken(binding.second))).toString())
send("userinfo")
} }
override fun onMessage(webSocket:WebSocket,text:String) { synchronized(this@ProfileSession) {
if(epoch!=generation) return
if(!valid() || text.length>2_000_000) { stop(); return }
val packet=runCatching { JSONObject(text) }.getOrNull() ?: return
receive(packet)
} }
override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,"") }
override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { failed(epoch) }
override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { failed(epoch) }
})
timer=scope.launch { delay(20_000); synchronized(this@ProfileSession) { if(epoch==generation && !state.value.ready) stop("timeout") } }
}.onFailure { stop() }
}
@Synchronized private fun failed(epoch:Long) { if(epoch==generation) stop() }
@Synchronized fun stop(reason:String="disconnected") {
val uncertain=pendingId.isNotBlank()
generation++; socket?.cancel(); socket=null
listOf(timer,readTimer,detailsTimer,historyTimer,directoryTimer).forEach { it?.cancel() }
reads.clear(); listTags.clear(); details.clear(); pendingId=""; pendingAction=""; pendingTarget=""; pendingNode=""; expected=JSONObject(); historyId=""; directoryId=""
state.value=ProfileState(notice=if(uncertain) "unknown" else reason)
}
@Synchronized fun close() { closed=true; stop(); scope.cancel() }
@Synchronized fun refresh() { if(state.value.ready && !state.value.busy && valid()) { send("userinfo"); loadLists() } }
private fun finishAdvancedUnknown() {
readTimer?.cancel();reads.clear();listTags.clear()
finish("unknown",false)
state.update {it.copy(loading=false,readError=true)}
}
private fun expireListReads(epoch:Long) {
if(epoch!=generation || reads.isEmpty()) return
if(advancedOperation()) {finishAdvancedUnknown();return}
reads.clear();listTags.clear()
state.update {it.copy(loading=false,readError=true,notice="readFailed")}
}
private fun loadLists() {
if(advancedOperation() && expected.optBoolean("advancedAck")) {expected.put("advancedMeshRead",false);expected.remove("advancedMeshRecord")}
if(directMeshOperation() && expected.optBoolean("directAck")) expected.put("directUserRead",false).put("directMeshRead",false)
if((pendingAction in setOf("removemeshuser","addmeshuser") && !directMeshOperation()) && expected.optBoolean("meshAck")) expected.put("meshDeviceRead",false)
reads.clear(); listTags.clear(); reads.add("meshes")
if(ProfilePermissions.manageUsers(state.value.site)) reads.add("users")
state.update { it.copy(loading=true,readError=false,users=if("users" in reads) it.users else emptyList()) }
for(action in reads.toList()) {
val tag="profile-list:"+UUID.randomUUID().toString()
listTags[action]=tag
if(!send(action,JSONObject().put("tag",tag).put("responseid",tag))) {stop();return}
}
val epoch=generation
readTimer?.cancel(); readTimer=scope.launch { delay(20_000); synchronized(this@ProfileSession) {
expireListReads(epoch)
} }
}
@Synchronized fun loadDetails() { loadDetailReads(false) }
@Synchronized fun loadGroupDetails() { loadDetailReads(true) }
private fun loadDetailReads(groupView:Boolean) {
if(!state.value.ready || !valid() || (!groupView && !ProfilePermissions.manageUsers(state.value.site)) || (groupView && state.value.busy)) return
if(groupView) { loadLists(); if(!state.value.ready || !valid()) return }
details.clear(); details.add("nodes")
if(!groupView) details.addAll(listOf("usergroups","wssessioncount")) else if(ProfilePermissions.normalized(state.value.site) and 256L!=0L) details.add("usergroups")
state.update { it.copy(nodes=null,userGroups=null,sessions=null,detailsBusy=true) }
if(details.toList().any { !send(it) }) { stop(); return }
val epoch=generation
detailsTimer?.cancel(); detailsTimer=scope.launch { delay(20_000); synchronized(this@ProfileSession) {
if(epoch==generation) { details.clear(); state.update { it.copy(detailsBusy=false) } }
} }
}
@Synchronized fun loadUserGroupDirectory() {requestUserGroupDirectory()}
private fun requestUserGroupDirectory(verify:Boolean=false) {
if(!state.value.ready || !valid() || (state.value.busy && (!verify || (pendingAction!="editusergroup" && !(pendingAction in setOf("removeuserfromusergroup","addusertousergroup") && expected.optBoolean("memberAck")) && !((pendingAction in setOf("removemeshuser","addmeshuser") && !directMeshOperation()) && expected.optBoolean("meshAck")))))) return
if((pendingAction in setOf("removemeshuser","addmeshuser") && !directMeshOperation()) && expected.optBoolean("meshAck")) expected.put("meshGroupRead",false)
directoryTimer?.cancel(); directoryId="profile-user-groups:"+UUID.randomUUID().toString()
val request=directoryId; val epoch=generation
state.update {it.copy(listedUserGroups=null,userGroupPhase="loading")}
if(!send("usergroups",JSONObject().put("tag",request))) { directoryId=""; state.update {it.copy(userGroupPhase="failed")};return }
directoryTimer=scope.launch {delay(20_000);synchronized(this@ProfileSession) {if(epoch==generation && directoryId==request && state.value.userGroupPhase=="loading") {directoryId="";state.update {it.copy(listedUserGroups=null,userGroupPhase="failed")}}}}
}
@Synchronized fun clearHistory() { historyTimer?.cancel(); historyId=""; state.update { it.copy(historyUser="",history=null,historyBusy=false,historyFailed=false) } }
@Synchronized fun previousLogins(id:String):Boolean {
val s=state.value
val target=s.users.firstOrNull { it.optString("_id")==id } ?: return false
if(!s.ready || !valid() || !canAdministerUser(s,target)) return false
clearHistory(); historyId=UUID.randomUUID().toString()
state.update { it.copy(historyUser=id,historyBusy=true) }
if(!send("previousLogins",JSONObject().put("userid",id).put("responseid",historyId))) { clearHistory(); return false }
val epoch=generation
historyTimer=scope.launch { delay(20_000); synchronized(this@ProfileSession) {
if(epoch==generation && state.value.historyBusy) state.update { it.copy(historyBusy=false,historyFailed=true) }
} }
return true
}
private fun records(value:Any?):List<JSONObject>? {
val result=mutableListOf<JSONObject>()
when(value) {
is JSONArray->for(i in 0 until value.length()) result.add(value.optJSONObject(i)?.let { JSONObject(it.toString()) } ?: return null)
is JSONObject->for(key in value.keys()) result.add(value.optJSONObject(key)?.let { JSONObject(it.toString()).apply { if(optString("_id").isBlank()) put("_id",key) } } ?: return null)
else->return null
}
if(result.any { it.optString("_id").isBlank() } || result.map { it.optString("_id") }.distinct().size!=result.size) return null
return result.sortedBy { it.optString("name").lowercase() }
}
private fun matches(account:JSONObject)=expected.length()>0 && expected.keys().asSequence().all { key->if(key in setOf("phone","emailVerified")) userContactFieldMatches(key,account,expected) else userEditFieldMatches(key,account,expected) }
private fun finish(notice:String,reload:Boolean=true) {
timer?.cancel(); pendingId=""; pendingAction=""; pendingTarget=""; pendingNode=""; expected=JSONObject()
state.update { it.copy(busy=false,notice=notice) }
if(reload) { loadLists(); source.requestServerData() }
}
private fun observeNode(node:JSONObject) {
if(pendingAction=="adddeviceuser" && node.optString("_id")==pendingNode && directUserRights(node.optJSONObject("links"),pendingTarget)==expected.optLong("rights",-1)) finish("observed")
}
private fun observeUserMeshRevocation() {
if(!directMeshRevocation()) return
val uid=expected.optString("directUser");val mid=expected.optString("directTarget")
if(!profileUserMeshRevokeReadbackReady(state.value,uid,mid,expected.optBoolean("directAck"),expected.optBoolean("directUserRead"),expected.optBoolean("directMeshRead"))) return
state.update {it.copy(revokedDirectUser=uid,revokedDirectMesh=mid)}
finish("userMeshRevokedObserved",false);source.requestServerData()
}
private fun observeUserMeshGrant() {
if(!directMeshGrant()) return
val uid=expected.optString("directUser");val mid=expected.optString("directTarget");val rights=expected.optLong("directRights",-1)
val ack=expected.optBoolean("directAck");val ur=expected.optBoolean("directUserRead");val mr=expected.optBoolean("directMeshRead")
val snapshots=state.value.copy(users=listOfNotNull(expected.optJSONObject("grantUserRecord")),groups=listOfNotNull(expected.optJSONObject("grantMeshRecord")))
if(!profileUserMeshGrantReadbackReady(snapshots,uid,mid,rights,ack,ur,mr) || !profileUserMeshGrantReadbackReady(state.value,uid,mid,rights,ack,ur,mr)) return
state.update {it.copy(grantedDirectUser=uid,grantedDirectMesh=mid)}
finish("userMeshGrantedObserved",false);source.requestServerData()
}
private fun observeUserMeshEdit() {
if(!directMeshEdit()) return
val uid=expected.optString("directUser");val mid=expected.optString("directTarget");val rights=expected.optLong("directRights",-1)
val ack=expected.optBoolean("directAck");val ur=expected.optBoolean("directUserRead");val mr=expected.optBoolean("directMeshRead")
val snapshots=state.value.copy(users=listOfNotNull(expected.optJSONObject("grantUserRecord")),groups=listOfNotNull(expected.optJSONObject("grantMeshRecord")))
if(!profileUserMeshEditReadbackReady(snapshots,uid,mid,rights,ack,ur,mr) || !profileUserMeshEditReadbackReady(state.value,uid,mid,rights,ack,ur,mr)) return
state.update {it.copy(editedDirectUser=uid,editedDirectMesh=mid)}
finish("userMeshEditedObserved",false);source.requestServerData()
}
private fun observeGroupMeshEdit() {
if(!groupMeshEdit()) return
val gid=expected.optString("meshSubject");val mid=expected.optString("meshTarget");val rights=expected.optLong("meshRights",-1)
val ack=expected.optBoolean("meshAck");val gr=expected.optBoolean("meshGroupRead");val mr=expected.optBoolean("meshDeviceRead")
val snapshots=state.value.copy(listedUserGroups=listOfNotNull(expected.optJSONObject("editGroupRecord")),groups=listOfNotNull(expected.optJSONObject("editDeviceRecord")))
if(!profileGroupMeshEditReadbackReady(snapshots,gid,mid,rights,ack,gr,mr) || !profileGroupMeshEditReadbackReady(state.value,gid,mid,rights,ack,gr,mr)) return
state.update {it.copy(editedGroup=gid,editedDeviceGroup=mid)}
finish("groupMeshEditedObserved",false);source.requestServerData()
}
private fun observeGroupMeshRevocation() {
if(groupMeshEdit()) {observeGroupMeshEdit();return}
if(directMeshOperation()) return
val granting=pendingAction=="addmeshuser"
if(pendingAction !in setOf("removemeshuser","addmeshuser")) return
val subject=expected.optString("meshSubject");val device=expected.optString("meshTarget")
val verified=if(granting) profileGroupMeshGrantReadbackReady(state.value,subject,device,expected.optLong("meshRights"),expected.optBoolean("meshAck"),expected.optBoolean("meshGroupRead"),expected.optBoolean("meshDeviceRead")) else profileGroupMeshReadbackReady(state.value,subject,device,expected.optBoolean("meshAck"),expected.optBoolean("meshGroupRead"),expected.optBoolean("meshDeviceRead"))
if(!verified) return
state.update {if(granting) it.copy(grantedGroup=subject,grantedDeviceGroup=device) else it.copy(revokedGroup=subject,revokedDeviceGroup=device)}
finish(if(granting) "groupMeshGrantedObserved" else "groupMeshRevokedObserved",false);source.requestServerData()
}
private fun receive(p:JSONObject) {
val action=p.optString("action")
if(action=="usergroups" && p.optString("tag").startsWith("profile-user-groups:")) {
if(directoryId.isBlank() || p.optString("tag")!=directoryId || state.value.userGroupPhase!="loading") return
directoryTimer?.cancel();directoryId=""
val raw=if(p.has("ugroups") && p.isNull("ugroups")) emptyList<JSONObject>() else records(p.opt("ugroups"))
val list=raw?.takeIf {all->all.all {it.optString("_id").startsWith("ugrp/") && it.optString("_id").split('/').size==3}}?.filter {profileUserGroupDomainAllowed(state.value.id,it.optString("_id"))}
state.update {it.copy(listedUserGroups=list,userGroupPhase=if(list==null) "failed" else "ready")}
if(groupMeshEdit() && expected.optBoolean("meshAck")) {val record=list?.singleOrNull {it.optString("_id")==expected.optString("meshSubject")};expected.put("editGroupRecord",record?.let {JSONObject(it.toString())})}
if((pendingAction in setOf("removemeshuser","addmeshuser") && !directMeshOperation()) && expected.optBoolean("meshAck")) {expected.put("meshGroupRead",list!=null && profileUserGroupMemberReadValid(p));observeGroupMeshRevocation()}
if(pendingAction=="editusergroup" && list?.singleOrNull {it.optString("_id")==pendingTarget}?.let {profileUserGroupEditObserved(it,expected)}==true) finish("userGroupObserved",false)
if(pendingAction=="addusertousergroup" && profileUserGroupMemberReadValid(p) && list?.singleOrNull {it.optString("_id")==expected.optString("memberGroup")}?.let {profileUserGroupMemberAddObserved(it,pendingTarget,expected.optBoolean("memberAck"))}==true) {val member=pendingTarget;val memberGroup=expected.optString("memberGroup");state.update {it.copy(addedMember=member,addedMemberGroup=memberGroup)};finish("userGroupMemberAddedObserved",false);loadLists();source.requestServerData()}
if(pendingAction=="removeuserfromusergroup" && profileUserGroupMemberReadValid(p) && list?.singleOrNull {it.optString("_id")==expected.optString("memberGroup")}?.let {profileUserGroupMemberRemovalObserved(it,pendingTarget,expected.optBoolean("memberAck"))}==true) {val member=pendingTarget;val memberGroup=expected.optString("memberGroup");state.update {it.copy(removedMember=member,removedMemberGroup=memberGroup)};finish("userGroupMemberObserved",false);loadLists();source.requestServerData()}
return
}
when(action) {
"ping"->{ send("pong"); return }
"close","stopped"->{ stop(); return }
"serverinfo"->{ state.update { it.copy(server=JSONObject((p.optJSONObject("serverinfo") ?: p).toString())) }; if(advancedOperation() && !advancedAuthority()) stop(); return }
"userinfo"->{
val u=p.optJSONObject("userinfo") ?: return
if(u.optString("_id").isBlank()) return
if(state.value.id.isNotBlank() && state.value.id!=u.optString("_id")) { stop(); return }
val first=!state.value.ready
directoryTimer?.cancel();directoryId=""
state.update { it.copy(ready=true,user=JSONObject(u.toString()),listedUserGroups=null,userGroupPhase="idle",notice=if(first) "ready" else it.notice) }
if(first) { timer?.cancel(); loadLists() }
if(advancedOperation()) {if(!advancedAuthority()) {stop();return};if(expected.optBoolean("advancedAck")) loadLists()}
if(directMeshOperation()) {if(!ProfilePermissions.manageUsers(state.value.site)) {stop();return};if(expected.optBoolean("directAck")) loadLists()}
if(!directMeshOperation() && pendingAction in setOf("editusergroup","createusergroup","deleteusergroup","removeuserfromusergroup","addusertousergroup","removemeshuser","addmeshuser")) {if(!profileUserGroupEditRole(state.value.site)) {stop();return};if(pendingAction=="editusergroup" || (pendingAction in setOf("removeuserfromusergroup","addusertousergroup") && expected.optBoolean("memberAck"))) requestUserGroupDirectory(true)}
if((pendingAction in setOf("removemeshuser","addmeshuser") && !directMeshOperation()) && expected.optBoolean("meshAck")) {loadLists();requestUserGroupDirectory(true)}
if(pendingAction=="addusertousergroup" && !ProfilePermissions.manageUsers(state.value.site)) {stop();return}
if(!ProfilePermissions.manageUsers(state.value.site)) {
if(pendingAction in setOf("adduser","edituser","deleteuser","changeuserpass","adddeviceuser","userbroadcast")) { stop(); return }
reads.remove("users"); details.clear(); clearHistory()
state.update { it.copy(users=emptyList(),nodes=null,userGroups=null,sessions=null,detailsBusy=false,loading=reads.isNotEmpty()) }
}
}
"users","meshes"->{
if(action !in reads || !profileListReadMatches(action,listTags[action],p.opt("tag"),p.opt("responseid"),p.has(action))) return
val replyError=p.has("error") || p.has("err") || (p.has("result") && !p.optString("result").equals("ok",true))
val list=if(replyError) null else records(p.opt(action)); reads.remove(action);listTags.remove(action)
if(directMeshOperation() && expected.optBoolean("directAck")) expected.put(if(action=="users") "directUserRead" else "directMeshRead",list!=null && profileUserGroupMemberReadValid(p))
if(action=="meshes" && (pendingAction in setOf("removemeshuser","addmeshuser") && !directMeshOperation()) && expected.optBoolean("meshAck")) expected.put("meshDeviceRead",list!=null && profileUserGroupMemberReadValid(p))
if(action=="meshes" && groupMeshEdit() && expected.optBoolean("meshAck")) {val record=list?.singleOrNull {it.optString("_id")==expected.optString("meshTarget")};expected.put("editDeviceRecord",record?.let {JSONObject(it.toString())})}
if(action=="meshes" && advancedOperation() && expected.optBoolean("advancedAck")) {
expected.put("advancedMeshRead",list!=null && profileUserGroupMemberReadValid(p))
val record=list?.singleOrNull {it.optString("_id")==pendingTarget}
expected.put("advancedMeshRecord",record?.let {JSONObject(it.toString())})
}
if((directMeshGrant() || directMeshEdit()) && expected.optBoolean("directAck")) {
val id=expected.optString(if(action=="users") "directUser" else "directTarget")
val record=list?.singleOrNull {it.optString("_id")==id}
expected.put(if(action=="users") "grantUserRecord" else "grantMeshRecord",record?.let {JSONObject(it.toString())})
}
state.update { if(list==null) it.copy(loading=reads.isNotEmpty(),readError=true,notice="readFailed") else if(action=="users") it.copy(users=list,loading=reads.isNotEmpty()) else it.copy(groups=list,loading=reads.isNotEmpty()) }
if(reads.isEmpty()) readTimer?.cancel()
if(advancedOperation()) observeAdvancedSettings()
if(directMeshRevocation()) observeUserMeshRevocation()
if(directMeshGrant()) observeUserMeshGrant()
if(directMeshEdit()) observeUserMeshEdit()
if(action=="meshes" || groupMeshEdit()) observeGroupMeshRevocation()
if(action=="users" && pendingAction=="edituser") list?.firstOrNull { it.optString("_id")==pendingTarget }?.let { if(matches(it)) finish("observed",false) }
}
"nodes","usergroups","wssessioncount"->{
if(action !in details) return
details.remove(action)
when(action) {
"nodes"->{
val raw=p.optJSONObject("nodes")
val nodes=if(raw==null) null else runCatching {
val all=mutableListOf<JSONObject>()
for(mesh in raw.keys()) { val a=raw.getJSONArray(mesh); for(i in 0 until a.length()) all.add(JSONObject(a.getJSONObject(i).toString()).put("meshid",mesh)) }
require(all.all { it.optString("_id").startsWith("node/") }); require(all.map { it.optString("_id") }.distinct().size==all.size); all.toList()
}.getOrNull()
state.update { it.copy(nodes=nodes) }; nodes?.forEach(::observeNode)
}
"usergroups"->state.update { it.copy(userGroups=records(p.opt("ugroups"))) }
else->{ val counts=p.optJSONObject("wssessions")?.takeIf { o->o.keys().asSequence().all { key->(o.opt(key) as? Number)?.let { it.toDouble()>=0 && it.toDouble()<=Int.MAX_VALUE && it.toDouble()%1.0==0.0 }==true } }; state.update { it.copy(sessions=counts?.let { JSONObject(it.toString()) }) } }
}
state.update { it.copy(detailsBusy=details.isNotEmpty()) }; if(details.isEmpty()) detailsTimer?.cancel()
}
"previousLogins"->{
if(!state.value.historyBusy || p.optString("userid")!=state.value.historyUser || (p.has("responseid") && p.optString("responseid")!=historyId)) return
val a=p.optJSONArray("events")?.takeIf { it.length()<=2000 && (0 until it.length()).all { i->it.optJSONObject(i)!=null } }
historyTimer?.cancel(); state.update { it.copy(history=a,historyBusy=false,historyFailed=a==null) }
}
"event"->{
val e=p.optJSONObject("event") ?: return
if(pendingAction in setOf("removeuserfromusergroup","addusertousergroup") && expected.optBoolean("memberAck") && e.optString("action")=="usergroupchange" && e.optString("ugrpid")==expected.optString("memberGroup") && state.value.userGroupPhase!="loading") requestUserGroupDirectory(true)
if(pendingAction=="editusergroup" && e.optString("action")=="usergroupchange" && e.optString("ugrpid")==pendingTarget && state.value.userGroupPhase!="loading") requestUserGroupDirectory(true)
val account=e.optJSONObject("account")
if(e.optString("action") in setOf("accountchange","accountcreate") && account!=null && ProfilePermissions.manageUsers(state.value.site)) {
val id=account.optString("_id")
if(id.isNotBlank() && userDomain(id)==userDomain(state.value.id)) state.update { it.copy(users=(it.users.filterNot { u->u.optString("_id")==id }+JSONObject(account.toString())).sortedBy { u->u.optString("name").lowercase() }) }
if(id==state.value.id) send("userinfo")
if(id==pendingTarget && ((pendingAction=="edituser" && matches(account)) || (pendingAction=="changeuserpass" && e.optInt("msgid")==75))) finish("observed")
}
if(e.optString("action")=="changenode") e.optJSONObject("node")?.let(::observeNode)
if(e.optString("action")=="wssessioncount" && e.opt("count") is Number && e.optLong("count") in 0..Int.MAX_VALUE.toLong()) state.update { s->s.copy(sessions=s.sessions?.let { JSONObject(it.toString()).put(e.optString("userid"),e.optInt("count")) }) }
}
}
if(pendingId.isNotBlank() && p.optString("responseid")==pendingId && action==pendingAction) {
if(advancedOperation()) {
if(!advancedAuthority()) {stop();return}
if(deviceGroupAdvancedAck(p,pendingId)) {expected.put("advancedAck",true);loadLists()}
else if(p.has("error") || p.has("err") || (p.has("result") && !p.optString("result").equals("ok",true))) finish("rejected",false)
return
}

if(groupMeshEdit()) {
if(profileGroupMeshEditAck(p,pendingId)) {expected.put("meshAck",true).put("meshGroupRead",false).put("meshDeviceRead",false);loadLists();requestUserGroupDirectory(true)}
else if(profileGroupMeshGrantReplyRejected(p)) finish("rejected",false)
return
}

if(directMeshEdit()) {
if(profileUserMeshEditAck(p,pendingId)) {expected.put("directAck",true).put("directUserRead",false).put("directMeshRead",false);loadLists()}
else if(profileGroupMeshGrantReplyRejected(p)) finish("rejected",false)
return
}

if(directMeshGrant()) {
if(profileUserMeshGrantAck(p,pendingId)) {expected.put("directAck",true).put("directUserRead",false).put("directMeshRead",false);loadLists()}
else if(profileGroupMeshGrantReplyRejected(p)) finish("rejected",false)
return
}
if(directMeshRevocation()) {
if(profileUserMeshRevokeAck(p,pendingId)) {expected.put("directAck",true).put("directUserRead",false).put("directMeshRead",false);loadLists()}
else if(p.has("error") || p.has("err") || (p.has("result") && p.optString("result")!="ok")) finish("rejected",false)
return
}
if(action=="addmeshuser") {
if(profileGroupMeshGrantAck(p,pendingId)) {expected.put("meshAck",true).put("meshGroupRead",false).put("meshDeviceRead",false);loadLists();requestUserGroupDirectory(true)}
else if(profileGroupMeshGrantReplyRejected(p)) finish("rejected",false)
return
}
val ok=p.optString("result").equals("ok",true) && !p.has("error") && !p.has("err")
val rejected=p.has("error") || p.has("err") || (p.has("result") && !ok)
if(rejected) finish("rejected",false)
else if(ok && action=="removemeshuser") {if(profileGroupMeshRevokeAck(p,pendingId)) {expected.put("meshAck",true).put("meshGroupRead",false).put("meshDeviceRead",false);loadLists();requestUserGroupDirectory(true)}}
else if(ok && action=="addusertousergroup") {if(profileUserGroupMemberAddAck(p)) {expected.put("memberAck",true);requestUserGroupDirectory(true)}}
else if(ok && action=="removeuserfromusergroup") {expected.put("memberAck",true);requestUserGroupDirectory(true)}
else if(ok && action=="deleteusergroup") {
if(profileUserGroupDeleteAck(pendingTarget,p.opt("ugrpid"))) {val removed=pendingTarget;state.update {it.copy(deletedUserGroup=removed)};finish("userGroupDeleted",false);loadUserGroupDirectory();loadLists();source.requestServerData()}
}
else if(ok && action=="createusergroup") {
val created=p.opt("ugrpid") as? String
if(created!=null && profileCreatedUserGroupId(state.value.id,created)) {state.update {it.copy(createdUserGroup=created)};finish("userGroupCreated",false);loadUserGroupDirectory()}
}
else if(ok && action=="editusergroup") requestUserGroupDirectory(true)
else if(ok && action=="userbroadcast") finish("broadcastAccepted",false)
else if(ok && action in setOf("edituser","adddeviceuser","changeuserpass")) { if(action=="edituser") loadLists() else if(action=="adddeviceuser") loadDetails() }
else if(ok) finish("confirmed")
}
}
@Synchronized fun submitDeviceGroupAdvanced(input:JSONObject):Boolean {
val p=runCatching {JSONObject(input.toString())}.getOrNull() ?: return false
val s=state.value
if(!valid() || !deviceGroupAdvancedAllowed(s,p,broadcastServerLabel())) return false
val payload=deviceGroupAdvancedWire(p)
pendingId=UUID.randomUUID().toString();pendingAction="editmesh";pendingTarget=p.getString("meshid");pendingNode=""
expected=JSONObject().put("advancedOperation",true).put("advancedRequest",p).put("advancedAck",false).put("advancedMeshRead",false)
val request=pendingId;val epoch=generation
state.update {it.copy(busy=true,notice="pending",advancedEditedDeviceGroup="")}
timer?.cancel();timer=scope.launch {delay(20_000);synchronized(this@ProfileSession) {if(epoch==generation && pendingId==request) finishAdvancedUnknown()}}
if(!send("editmesh",payload.put("responseid",request))) {stop();return false}
return true
}
@Synchronized fun submitUserMeshGrant(p:JSONObject):Boolean {
val s=state.value
if(!valid() || !profileUserMeshGrantAllowed(s,p,broadcastServerLabel())) return false
val payload=profileUserMeshGrantWire(p)
val uid=p.getJSONArray("userids").getString(0)
pendingId=UUID.randomUUID().toString();pendingAction="addmeshuser";pendingTarget=uid;pendingNode=""
expected=JSONObject().put("directGrant",true).put("directUser",uid).put("directTarget",p.getString("meshid")).put("directRights",userAdvancedMask(p.get("meshadmin"))).put("directAck",false).put("directUserRead",false).put("directMeshRead",false)
payload.put("responseid",pendingId)
if(socket?.send(payload.toString())!=true) {stop("unknown");return false}
state.update {it.copy(busy=true,notice="sent",grantedDirectUser="",grantedDirectMesh="")}
val epoch=generation
timer?.cancel();timer=scope.launch {delay(20_000);synchronized(this@ProfileSession) {if(epoch==generation && pendingId.isNotBlank()) stop("unknown")}}
return true
}
@Synchronized fun submitUserMeshEdit(p:JSONObject):Boolean {
val s=state.value
if(!valid() || !profileUserMeshEditAllowed(s,p,broadcastServerLabel())) return false
val payload=profileUserMeshEditWire(p)
val uid=p.getJSONArray("userids").getString(0)
pendingId=UUID.randomUUID().toString();pendingAction="addmeshuser";pendingTarget=uid;pendingNode=""
expected=JSONObject().put("directEdit",true).put("directUser",uid).put("directTarget",p.getString("meshid")).put("directRights",userAdvancedMask(p.get("meshadmin"))).put("directAck",false).put("directUserRead",false).put("directMeshRead",false)
payload.put("responseid",pendingId)
if(socket?.send(payload.toString())!=true) {stop("unknown");return false}
state.update {it.copy(busy=true,notice="sent",editedDirectUser="",editedDirectMesh="")}
val epoch=generation
timer?.cancel();timer=scope.launch {delay(20_000);synchronized(this@ProfileSession) {if(epoch==generation && pendingId.isNotBlank()) stop("unknown")}}
return true
}
@Synchronized fun submitGroupMeshEdit(p:JSONObject):Boolean {
val s=state.value
if(!valid() || !profileGroupMeshEditAllowed(s,p,broadcastServerLabel())) return false
val payload=profileGroupMeshEditWire(p)
val uid=p.getJSONArray("userids").getString(0)
pendingId=UUID.randomUUID().toString();pendingAction="addmeshuser";pendingTarget=uid;pendingNode=""
expected=JSONObject().put("groupEdit",true).put("meshSubject",uid).put("meshTarget",p.getString("meshid")).put("meshRights",userAdvancedMask(p.get("meshadmin"))).put("meshAck",false).put("meshSubjectRead",false).put("meshDeviceRead",false)
payload.put("responseid",pendingId)
if(socket?.send(payload.toString())!=true) {stop("unknown");return false}
state.update {it.copy(busy=true,notice="sent",editedGroup="",editedDeviceGroup="")}
val epoch=generation
timer?.cancel();timer=scope.launch {delay(20_000);synchronized(this@ProfileSession) {if(epoch==generation && pendingId.isNotBlank()) stop("unknown")}}
return true
}
@Synchronized fun submitUserMeshRevoke(p:JSONObject):Boolean {
val s=state.value
if(!valid() || !profileUserMeshRevokeAllowed(s,p,broadcastServerLabel())) return false
val payload=profileUserMeshRevokeWire(p)
pendingId=UUID.randomUUID().toString();pendingAction="removemeshuser";pendingTarget=p.getString("userid");pendingNode=""
expected=JSONObject().put("directMesh",true).put("directUser",p.getString("userid")).put("directTarget",p.getString("meshid")).put("directAck",false).put("directUserRead",false).put("directMeshRead",false)
payload.put("responseid",pendingId)
if(socket?.send(payload.toString())!=true) {stop("unknown");return false}
state.update {it.copy(busy=true,notice="sent",revokedDirectUser="",revokedDirectMesh="")}
val epoch=generation
timer?.cancel();timer=scope.launch {delay(20_000);synchronized(this@ProfileSession) {if(epoch==generation && pendingId.isNotBlank()) stop("unknown")}}
return true
}
@Synchronized fun submit(p:JSONObject):Boolean {
if(p.keys().asSequence().any {it.startsWith("_advanced")} || (p.optString("action")=="editmesh" && DEVICE_GROUP_ADVANCED_FIELDS.any { p.has(it) })) return false
val s=state.value
if(!s.canWrite || !valid()) return false
val action=p.optString("action")
val id=p.optString("id").ifBlank { p.optString("userid").ifBlank { p.optString("meshid").ifBlank {p.optString("ugrpid")} } }
val target=s.users.firstOrNull { it.optString("_id")==id }
val group=s.groups.firstOrNull { it.optString("_id")==id }
val node=s.nodes?.firstOrNull { it.optString("_id")==p.optString("nodeid") }
val allowed=when(action) {
"addmeshuser"->profileGroupMeshGrantAllowed(s,p,broadcastServerLabel())
"removemeshuser"->profileGroupMeshRevokeAllowed(s,p,broadcastServerLabel())
"addusertousergroup"->profileUserGroupMemberAddAllowed(s,p,broadcastServerLabel())
"removeuserfromusergroup"->profileUserGroupMemberRemoveAllowed(s,p,broadcastServerLabel())
"deleteusergroup"->profileUserGroupDeleteAllowed(s,p,broadcastServerLabel())
"createusergroup"->profileUserGroupCreateAllowed(s,p,broadcastServerLabel())
"editusergroup"->profileUserGroupEditAllowed(s,p)
"userbroadcast"->profileBroadcastAllowed(s,p,broadcastServerLabel())
"adduser"->s.canCreateUser && validUserCreation(p.optString("username"),p.optString("email"),p.optString("pass"),p.optString("pass")) && p.opt("removeEvents") is Boolean && p.opt("resetNextLogin") is Boolean
"edituser"->if(p.has("_contactBefore")) userContactAllowed(s,target,p) else if(p.has("_advancedBefore")) userAdvancedAllowed(s,target,p) else (target!=null && canAdministerUser(s,target) && (if(p.has("siteadmin")) canLockUser(s,target) && p.optLong("_previousSite",-1)==ProfilePermissions.normalized(target.optLong("siteadmin",0)) && p.optLong("siteadmin",-1)==userLockMask(target.optLong("siteadmin",0),!userIsLocked(target.optLong("siteadmin",0))) else (p.has("email") || p.has("realname")) && (!p.has("email") || (!s.emailIdentity && (p.optString("email").isEmpty() || validUserEmail(p.optString("email"))) && p.optString("_beforeEmail")==target.optString("email"))) && (!p.has("realname") || (p.optString("realname").length<=256 && p.optString("_beforeRealname")==target.optString("realname"))))) && p.keys().asSequence().all { it in setOf("action","id","email","realname","siteadmin","_previousSite","_beforeEmail","_beforeRealname") }
"deleteuser"->target!=null && canAdministerUser(s,target) && id!=s.id
"changeuserpass"->target!=null && canAdministerUser(s,target) && id!=s.id && p.optString("pass").isNotEmpty() && p.optString("pass").length<=256 && p.opt("resetNextLogin") is Boolean && p.opt("removeMultiFactor")==false
"adddeviceuser"->p.optJSONArray("userids")?.let { a->a.length()==1 && s.users.any { it.optString("_id")==a.optString(0) && canAdministerUser(s,it) } }==true && node!=null && canGrantDevice(s,node) && p.optLong("rights",-1) in 1..0xffffffffL && p.optLong("rights") and 7L == 0L
"createmesh"->ProfilePermissions.createGroup(s.site) && p.optString("meshname").isNotBlank()
"editmesh"->group!=null && ProfilePermissions.editGroup(s.site,s.rights(group))
"deletemesh"->group!=null && ProfilePermissions.deleteGroup(s.site,s.rights(group))
else->false
}
if(!allowed) return false
val preservedQuota=if(action=="edituser") runCatching {
val current=requireNotNull(target)
if(p.has("_advancedBefore")) userQuotaForEdit(if(p.isNull("quota")) null else p.opt("quota")) else userQuotaForEdit(if(current.isNull("quota")) null else current.opt("quota"))
}.getOrElse { return false } else null
val keys=when(action) {
"userbroadcast"->listOf("msg")
"adduser"->listOf("username","email","pass","removeEvents","resetNextLogin")
"edituser"->listOf("email","realname","siteadmin","flags","removeRights","consent","groups","quota","phone","emailVerified")
"deleteuser"->listOf("userid","username")
"changeuserpass"->listOf("userid","pass","resetNextLogin","removeMultiFactor")
"adddeviceuser"->listOf("nodeid","nodename","userids","rights")
"createmesh"->listOf("meshname","meshtype","desc")
"editmesh"->listOf("meshid","meshname","desc")
else->listOf("meshid")
}
val payload=when(action) {"addmeshuser"->profileGroupMeshGrantWire(p);"removemeshuser"->profileGroupMeshRevokeWire(p);"addusertousergroup"->profileUserGroupMemberAddWire(p);"removeuserfromusergroup"->profileUserGroupMemberRemoveWire(p);"deleteusergroup"->profileUserGroupDeleteWire(p);"editusergroup"->profileUserGroupEditWire(p);"createusergroup"->profileUserGroupCreateWire(p);else->JSONObject().put("action",action)}
if(action !in setOf("editusergroup","createusergroup","deleteusergroup","removeuserfromusergroup","addusertousergroup","removemeshuser","addmeshuser")) keys.filter(p::has).forEach { payload.put(it,p.get(it)) }
if(action=="edituser") payload.put("id",id).put("quota",preservedQuota ?: JSONObject.NULL)
pendingId=UUID.randomUUID().toString(); pendingAction=action; pendingTarget=if(action=="addusertousergroup") p.getString("_addUser") else if(action=="adddeviceuser") p.getJSONArray("userids").getString(0) else id; pendingNode=p.optString("nodeid")
expected=JSONObject()
if(action=="addmeshuser") expected.put("meshSubject",p.getJSONArray("userids").getString(0)).put("meshTarget",p.getString("meshid")).put("meshRights",userAdvancedMask(p.get("meshadmin"))).put("meshAck",false).put("meshGroupRead",false).put("meshDeviceRead",false)
if(action=="removemeshuser") expected.put("meshSubject",p.getString("userid")).put("meshTarget",p.getString("meshid")).put("meshAck",false).put("meshGroupRead",false).put("meshDeviceRead",false)
if(action in setOf("removeuserfromusergroup","addusertousergroup")) expected.put("memberGroup",p.getString("ugrpid")).put("memberAck",false)
if(action=="edituser") listOf("email","realname","siteadmin","flags","removeRights","consent","groups","quota","phone","emailVerified").filter(payload::has).forEach { expected.put(it,payload.get(it)) }
if(action=="adddeviceuser") expected.put("rights",p.getLong("rights"))
if(action=="editusergroup") listOf("name","desc").filter(payload::has).forEach {expected.put(it,payload.getString(it))}
payload.put("responseid",pendingId)
if(socket?.send(payload.toString())!=true) { stop("unknown"); return false }
state.update { it.copy(busy=true,notice="sent",grantedGroup=if(action=="addmeshuser") "" else it.grantedGroup,grantedDeviceGroup=if(action=="addmeshuser") "" else it.grantedDeviceGroup,createdUserGroup=if(action=="createusergroup") "" else it.createdUserGroup,deletedUserGroup=if(action=="deleteusergroup") "" else it.deletedUserGroup,removedMember=if(action=="removeuserfromusergroup") "" else it.removedMember,removedMemberGroup=if(action=="removeuserfromusergroup") "" else it.removedMemberGroup,addedMember=if(action=="addusertousergroup") "" else it.addedMember,addedMemberGroup=if(action=="addusertousergroup") "" else it.addedMemberGroup,revokedGroup=if(action=="removemeshuser") "" else it.revokedGroup,revokedDeviceGroup=if(action=="removemeshuser") "" else it.revokedDeviceGroup) }
val epoch=generation
timer?.cancel(); timer=scope.launch { delay(20_000); synchronized(this@ProfileSession) { if(epoch==generation && pendingId.isNotBlank()) stop("unknown") } }
if(action=="editusergroup") requestUserGroupDirectory(true)
return true
}
}
