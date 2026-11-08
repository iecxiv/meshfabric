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
val listedUserGroups:List<JSONObject>?=null,val userGroupPhase:String="idle"
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
private val details=mutableSetOf<String>()
private fun valid()=!source.isUserLogout && source.connectionState.value && binding==Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
private fun send(action:String,fields:JSONObject=JSONObject()):Boolean=socket?.send(JSONObject(fields.toString()).put("action",action).toString())==true
@Synchronized fun broadcastServerLabel():String=if(!valid()) "" else runCatching { profileBroadcastServerLabel(OkHttpHelper.toWebSocketUrl(binding.first,"/control.ashx")) }.getOrDefault("")
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
reads.clear(); details.clear(); pendingId=""; pendingAction=""; pendingTarget=""; pendingNode=""; expected=JSONObject(); historyId=""; directoryId=""
state.value=ProfileState(notice=if(uncertain) "unknown" else reason)
}
@Synchronized fun close() { closed=true; stop(); scope.cancel() }
@Synchronized fun refresh() { if(state.value.ready && !state.value.busy && valid()) { send("userinfo"); loadLists() } }
private fun loadLists() {
reads.clear(); reads.add("meshes")
if(ProfilePermissions.manageUsers(state.value.site)) reads.add("users")
state.update { it.copy(loading=true,readError=false,users=if("users" in reads) it.users else emptyList()) }
if(reads.toList().any { !send(it) }) { stop(); return }
val epoch=generation
readTimer?.cancel(); readTimer=scope.launch { delay(20_000); synchronized(this@ProfileSession) {
if(epoch==generation && reads.isNotEmpty()) { reads.clear(); state.update { it.copy(loading=false,readError=true,notice="readFailed") } }
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
if(!state.value.ready || !valid() || (state.value.busy && (!verify || pendingAction!="editusergroup"))) return
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
private fun receive(p:JSONObject) {
val action=p.optString("action")
if(action=="usergroups" && p.optString("tag").startsWith("profile-user-groups:")) {
if(directoryId.isBlank() || p.optString("tag")!=directoryId || state.value.userGroupPhase!="loading") return
directoryTimer?.cancel();directoryId=""
val raw=if(p.has("ugroups") && p.isNull("ugroups")) emptyList<JSONObject>() else records(p.opt("ugroups"))
val list=raw?.takeIf {all->all.all {it.optString("_id").startsWith("ugrp/") && it.optString("_id").split('/').size==3}}?.filter {profileUserGroupDomainAllowed(state.value.id,it.optString("_id"))}
state.update {it.copy(listedUserGroups=list,userGroupPhase=if(list==null) "failed" else "ready")}
if(pendingAction=="editusergroup" && list?.singleOrNull {it.optString("_id")==pendingTarget}?.let {profileUserGroupEditObserved(it,expected)}==true) finish("userGroupObserved",false)
return
}
when(action) {
"ping"->{ send("pong"); return }
"close","stopped"->{ stop(); return }
"serverinfo"->{ state.update { it.copy(server=JSONObject((p.optJSONObject("serverinfo") ?: p).toString())) }; return }
"userinfo"->{
val u=p.optJSONObject("userinfo") ?: return
if(u.optString("_id").isBlank()) return
if(state.value.id.isNotBlank() && state.value.id!=u.optString("_id")) { stop(); return }
val first=!state.value.ready
directoryTimer?.cancel();directoryId=""
state.update { it.copy(ready=true,user=JSONObject(u.toString()),listedUserGroups=null,userGroupPhase="idle",notice=if(first) "ready" else it.notice) }
if(first) { timer?.cancel(); loadLists() }
if(pendingAction=="editusergroup") {if(!profileUserGroupEditRole(state.value.site)) {stop();return};requestUserGroupDirectory(true)}
if(!ProfilePermissions.manageUsers(state.value.site)) {
if(pendingAction in setOf("adduser","edituser","deleteuser","changeuserpass","adddeviceuser","userbroadcast")) { stop(); return }
reads.remove("users"); details.clear(); clearHistory()
state.update { it.copy(users=emptyList(),nodes=null,userGroups=null,sessions=null,detailsBusy=false,loading=reads.isNotEmpty()) }
}
}
"users","meshes"->{
if(action !in reads) return
val list=records(p.opt(action)); reads.remove(action)
state.update { if(list==null) it.copy(loading=reads.isNotEmpty(),readError=true,notice="readFailed") else if(action=="users") it.copy(users=list,loading=reads.isNotEmpty()) else it.copy(groups=list,loading=reads.isNotEmpty()) }
if(reads.isEmpty()) readTimer?.cancel()
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
val ok=p.optString("result").equals("ok",true) && !p.has("error") && !p.has("err")
val rejected=p.has("error") || p.has("err") || (p.has("result") && !ok)
if(rejected) finish("rejected",false)
else if(ok && action=="editusergroup") requestUserGroupDirectory(true)
else if(ok && action=="userbroadcast") finish("broadcastAccepted",false)
else if(ok && action in setOf("edituser","adddeviceuser","changeuserpass")) { if(action=="edituser") loadLists() else if(action=="adddeviceuser") loadDetails() }
else if(ok) finish("confirmed")
}
}
@Synchronized fun submit(p:JSONObject):Boolean {
val s=state.value
if(!s.canWrite || !valid()) return false
val action=p.optString("action")
val id=p.optString("id").ifBlank { p.optString("userid").ifBlank { p.optString("meshid").ifBlank {p.optString("ugrpid")} } }
val target=s.users.firstOrNull { it.optString("_id")==id }
val group=s.groups.firstOrNull { it.optString("_id")==id }
val node=s.nodes?.firstOrNull { it.optString("_id")==p.optString("nodeid") }
val allowed=when(action) {
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
val payload=if(action=="editusergroup") profileUserGroupEditWire(p) else JSONObject().put("action",action)
if(action!="editusergroup") keys.filter(p::has).forEach { payload.put(it,p.get(it)) }
if(action=="edituser") payload.put("id",id).put("quota",preservedQuota ?: JSONObject.NULL)
pendingId=UUID.randomUUID().toString(); pendingAction=action; pendingTarget=if(action=="adddeviceuser") p.getJSONArray("userids").getString(0) else id; pendingNode=p.optString("nodeid")
expected=JSONObject()
if(action=="edituser") listOf("email","realname","siteadmin","flags","removeRights","consent","groups","quota","phone","emailVerified").filter(payload::has).forEach { expected.put(it,payload.get(it)) }
if(action=="adddeviceuser") expected.put("rights",p.getLong("rights"))
if(action=="editusergroup") listOf("name","desc").filter(payload::has).forEach {expected.put(it,payload.getString(it))}
payload.put("responseid",pendingId)
if(socket?.send(payload.toString())!=true) { stop("unknown"); return false }
state.update { it.copy(busy=true,notice="sent") }
val epoch=generation
timer?.cancel(); timer=scope.launch { delay(20_000); synchronized(this@ProfileSession) { if(epoch==generation && pendingId.isNotBlank()) stop("unknown") } }
if(action=="editusergroup") requestUserGroupDirectory(true)
return true
}
}
