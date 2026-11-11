package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.awaitCancellation
import org.json.JSONObject

internal data class ProfileForm(val users:Boolean,val operation:String,val id:String="",val name:String="",val email:String="",val description:String="")

@Composable internal fun ProfileScreen(source:MeshCentralClient,modifier:Modifier=Modifier,header:@Composable (@Composable ()->Unit)->Unit={}) {
val agentActor by source.userInfo.collectAsState()
val agentIdentity=remember(source,source.serverUrl,source.authCookie,source.ignoreSsl,agentActor?.id) {Any()}
AgentGroupDialogsHost(agentIdentity,render={target,close->AgentInviteLinkDialog(source,target.mesh,false,enrollment=target.enrollment,onDismiss=close)}) {
val es=LocalConfiguration.current.locales[0].language=="es"
fun t(s:String,e:String)=if(es) s else e
val lifecycle=LocalLifecycleOwner.current.lifecycle
val session=remember(source) { ProfileSession(source) }
val liveState by session.state.collectAsState()
val state=rememberProfileDialogPresentation(liveState,agentIdentity,agentActor?.id.orEmpty())
val displayNames=rememberProfileDisplayNames(state,session.broadcastServerLabel())
val connected by source.connectionState.collectAsState()
val profileDeviceGroups by source.groupedDevices.collectAsState()
var section by rememberSaveable { mutableStateOf("account") }
val tab=profileSectionKeys.indexOf(section).coerceAtLeast(0)
var form by remember { mutableStateOf<ProfileForm?>(null) }
var groupDetails by remember { mutableStateOf<String?>(null) }
var userGroupDetails by remember {mutableStateOf<String?>(null)}
var deviceDetails by remember {mutableStateOf<String?>(null)}
var deviceAssignment by remember {mutableStateOf<Pair<String,String>?>(null)}
var origins by remember {mutableStateOf<List<ProfileOrigin>>(emptyList())}
var groupDetailTab by remember {mutableIntStateOf(0)}
var groupSearch by rememberSaveable { mutableStateOf("") }
var broadcast by remember { mutableStateOf(false) }
var ownAccountRequest by remember(source,state.id,source.serverUrl,source.authCookie,connected) {mutableStateOf<ProfileOwnAccountRequest?>(null)}
val canOpenProfileDevice:(String)->Boolean={nodeId->profileReceivedDevice(state,profileFlattenReceivedDevices(source.groupedDevices.value),nodeId,session.broadcastServerLabel().isNotBlank())!=null}
val openProfileDevice:(String)->Unit={nodeId->if(canOpenProfileDevice(nodeId)) deviceDetails=nodeId}
val closeProfileDialog:()->Unit={
val restored=profilePopOrigin(origins,state.users.filter {canAdministerUser(state,it)}.map {it.optString("_id")}.toSet(),state.groups.map {it.optString("_id")}.toSet(),state.ready)
origins=restored.second;form=null;groupDetails=null;userGroupDetails=null;deviceDetails=null;deviceAssignment=null
restored.first?.let {destination->if(destination.user!=null) form=destination.user else {groupDetails=destination.groupId;groupDetailTab=destination.groupTab}}
}

DisposableEffect(session) { onDispose { session.close() } }
LaunchedEffect(session,lifecycle,connected) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
if(connected && !source.isUserLogout) { session.start(); try { awaitCancellation() } finally { session.stop() } } else session.stop()
} }
LaunchedEffect(agentIdentity) { form=null; groupDetails=null; userGroupDetails=null; deviceDetails=null; deviceAssignment=null; origins=emptyList();groupDetailTab=0; broadcast=false;ownAccountRequest=null }
Column(modifier) {
ProfileRefreshHeader(tab,state,header) {performProfileHeaderRefresh(tab,session)}
if(!state.ready && state.id.isNotBlank()) Text(t("Datos anteriores; acciones bloqueadas hasta actualizar la conexión y los permisos.","Previous data; actions blocked until connection and permissions are refreshed."),Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall)
ManagementSectionTabs(listOf(t("Mi cuenta","My account"),t("Grupos de dispositivos","Device groups"),t("Usuarios","Users"),t("Grupos","Groups")),tab) { ownAccountRequest=null;section=profileSectionKeys.getOrNull(it) ?: "account"; form=null; groupDetails=null; userGroupDetails=null; deviceDetails=null; deviceAssignment=null; origins=emptyList();groupDetailTab=0; broadcast=false }
if(tab!=0) ProfileManagementConnectionStatus(state,Modifier.padding(horizontal=16.dp),session::start)
if(tab==0) NativeAccountPane(source,state.id,Modifier.weight(1f).fillMaxWidth(),pendingAction=ownAccountRequest,onActionConsumed={ownAccountRequest=null}) else if(section=="userGroups") UserGroupsDirectoryPane(session,state,Modifier.weight(1f).fillMaxWidth(),onOpenUser={id->state.users.firstOrNull {it.optString("_id")==id && canAdministerUser(state,it)}?.let {u->form=ProfileForm(true,"edit",id,u.optString("name"),u.optString("email"))}},onOpenDeviceGroup={id->if(state.ready && state.groups.any {it.optString("_id")==id}) {groupDetails=id;origins=emptyList();groupDetailTab=0;form=null}}) else {
val message=when(state.notice) {
"ready"->""
"userMeshEditedObserved"->t("Derechos directos editados y máscara exacta observada en ambos enlaces. Usuario: ","Direct rights edited and exact mask observed in both links. User: ")+displayNames.label(state.editedDirectUser,es)+t("; grupo de dispositivos: ","; device group: ")+displayNames.label(state.editedDirectMesh,es)
"userMeshGrantedObserved"->t("Derechos solicitados observados en ambos enlaces directos. Usuario: ","Requested rights observed in both direct links. User: ")+displayNames.label(state.grantedDirectUser,es)+t("; grupo de dispositivos: ","; device group: ")+displayNames.label(state.grantedDirectMesh,es)
"userMeshRevokedObserved"->t("Ambos enlaces directos observados ausentes. Usuario: ","Both direct links observed absent. User: ")+displayNames.label(state.revokedDirectUser,es)+t("; grupo de dispositivos: ","; device group: ")+displayNames.label(state.revokedDirectMesh,es)
"groupMeshRevokedObserved"->t("Ambos enlaces observados ausentes. Grupo de usuarios: ","Both links observed absent. User group: ")+displayNames.label(state.revokedGroup,es)+t("; grupo de dispositivos: ","; device group: ")+displayNames.label(state.revokedDeviceGroup,es)
"broadcastAccepted"->t("El servidor aceptó la difusión. No confirma recepción ni lectura.","The server accepted the broadcast. Receipt and reading are not confirmed.")
"confirmed"->t("Solicitud confirmada por el servidor; actualizando.","Server acknowledged the request; refreshing.")
"observed"->t("Se observó el cambio solicitado en el servidor.","The requested change was observed on the server.")
"sent"->t("Enviada; ejecución sin confirmar.","Sent; execution unconfirmed.")
"unknown"->t("Resultado sin confirmar. Reconecta y comprueba antes de reintentar. Sin reenvíos automáticos.","Outcome unconfirmed. Reconnect and check before retrying. No automatic resends.")
"rejected"->t("El servidor rechazó la solicitud.","The server rejected the request.")
"readFailed"->t("Listado incompleto. Modificaciones bloqueadas hasta actualizar.","Incomplete list. Changes blocked until refresh.")
else->t("Sin conexión o cargando.","Offline or loading.")
}
val users=section=="users"
val permitted=!users || ProfilePermissions.manageUsers(state.site)
val rows=if(users) state.users else state.groups.filter { deviceGroupMatches(groupSearch,it.optString("name"),it.optString("desc")) }
LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=managementConnectionContentPadding,verticalArrangement=Arrangement.spacedBy(10.dp)) {
item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall,color=if(state.notice in setOf("rejected","readFailed","unknown")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
if(state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
ServerGeneralActions {
if(users) TextButton(enabled=state.canWrite && profileBroadcastRoleAllowed(state.site),onClick={form=null;groupDetails=null;origins=emptyList();groupDetailTab=0;broadcast=true}) {Text("Broadcast")}
TextButton(enabled=state.canWrite && permitted && (if(users) state.canCreateUser else ProfilePermissions.createGroup(state.site)),onClick={ form=ProfileForm(users,"create") }) { Text(if(users) t("Crear usuario","Create user") else t("Crear grupo","Create group")) }
}
Text(if(users) t("Usuarios","Users") else t("Grupos de dispositivos","Device groups"),style=MaterialTheme.typography.titleMedium)
if(!users) OutlinedTextField(groupSearch,{groupSearch=it.take(256)},label={Text(t("Buscar grupo","Search groups"))},singleLine=true,modifier=Modifier.fillMaxWidth())
} }
if(!permitted) item { ManagementCard { Text(t("Sin permiso para administrar usuarios.","No permission to manage users.")) } } else {
if(rows.isEmpty() && !state.loading) item { ManagementCard { Text(if(state.ready && !state.readError) t("No hay elementos.","No items.") else t("Listado no disponible.","List unavailable.")) } }
items(rows,key={ it.optString("_id") }) { row->
val id=row.optString("_id")
val name=row.optString("name").ifBlank { if(users) t("nombre no disponible","name unavailable") else t("Grupo sin nombre","Unnamed group") }
ManagementCard {
if(users) ProfileUserListIdentity(row,state.users) else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {Icon(Icons.Default.Devices,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary);Text(name,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
val detail=if(users) "" else row.optString("desc")
if(detail.isNotBlank()) Text(detail,style=MaterialTheme.typography.bodyMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
if(users && userIsLocked(row.optLong("siteadmin",0))) Text(t("Bloqueado","Locked"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium)
HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
if(users && !profileOwnAccount(state.id,id)) TextButton(enabled=state.canWrite && canLockUser(state,row),onClick={ form=ProfileForm(true,if(userIsLocked(row.optLong("siteadmin",0))) "unlock" else "lock",id,name) }) { Text(if(userIsLocked(row.optLong("siteadmin",0))) t("Desbloquear","Unlock") else t("Bloquear","Lock")) }
if(!users) TextButton(enabled=state.ready && !state.busy,onClick={groupDetails=id;origins=emptyList();groupDetailTab=0;form=null}) {Text(t("Abrir","Open"))}
TextButton(enabled=state.canWrite && (if(users) canAdministerUser(state,row) else ProfilePermissions.editGroup(state.site,state.rights(row))),onClick={ form=ProfileForm(users,"edit",id,name,row.optString("email"),row.optString("desc")) }) { Text(t("Editar","Edit")) }
if(!users || !profileOwnAccount(state.id,id)) TextButton(enabled=state.canWrite && (if(users) canAdministerUser(state,row) && id!=state.id else ProfilePermissions.deleteGroup(state.site,state.rights(row))),onClick={ form=ProfileForm(users,"delete",id,name) },colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Text(t("Eliminar","Delete")) }
}
}
}
}
}
}
}
if(broadcast && form==null && groupDetails==null) ProfileBroadcastDialog(session,state) {broadcast=false}
groupDetails?.takeIf { form==null && userGroupDetails==null }?.let { id->key(id) { DeviceGroupDetailsDialog(session,state,id,origins.lastOrNull()?.user!=null,onDismiss=closeProfileDialog,initialTab=groupDetailTab,onTabChanged={groupDetailTab=it},onReviewUserGroupAssignment={gid->profileAccessAssignmentPair(state,gid,id)?.let {pair->deviceAssignment=pair;session.refresh();session.loadUserGroupDirectory()}},onOpenUserGroup={gid->if(profileReceivedUserGroup(state,gid)!=null) {userGroupDetails=gid;session.loadUserGroupDirectory()}},onOpenUser={uid->profileAccessUserTarget(state,uid)?.let {u->origins=profilePushOrigin(origins,ProfileOrigin(groupId=id,groupTab=groupDetailTab));groupDetails=null;form=ProfileForm(true,"edit",uid,u.optString("name"),u.optString("email"))}},onOpenDevice=openProfileDevice,canOpenDevice=canOpenProfileDevice,onEdit={state.groups.firstOrNull {it.optString("_id")==id}?.let {g->form=ProfileForm(false,"edit",id,g.optString("name"),description=g.optString("desc"))}},onDelete={state.groups.firstOrNull {it.optString("_id")==id}?.let {g->form=ProfileForm(false,"delete",id,g.optString("name"))}}) } }
form?.let { selected->
if(selected.users) UserAdministrationDialog(session,state,selected,onOpenGroup={id->if(state.ready && state.groups.any {it.optString("_id")==id}) {origins=profilePushOrigin(origins,ProfileOrigin(user=selected));groupDetailTab=0;groupDetails=id;form=null}},onOpenDevice=openProfileDevice,canOpenDevice=canOpenProfileDevice,onOpenUserGroup={gid->state.users.singleOrNull {it.optString("_id")==selected.id}?.let {u->if(gid in profileUserMembershipGroupIds(state,u).orEmpty() && profileReceivedUserGroup(state,gid)!=null) {userGroupDetails=gid;session.loadUserGroupDirectory()}}},canOpenUserGroup={gid->profileReceivedUserGroup(state,gid)!=null},onManageOwnAccount={action->if(connected && !source.isUserLogout && state.ready && profileOwnAccount(state.id,selected.id) && action in setOf("password","mfa")) {form=null;groupDetails=null;userGroupDetails=null;deviceDetails=null;deviceAssignment=null;origins=emptyList();groupDetailTab=0;broadcast=false;section="account";ownAccountRequest=ProfileOwnAccountRequest(state.id,source.serverUrl,action)}},onDismiss=closeProfileDialog) else key(selected) {
var name by remember { mutableStateOf(selected.name) }
var description by remember { mutableStateOf(selected.description) }
var confirmation by remember { mutableStateOf("") }
var error by remember { mutableStateOf("") }
val creating=selected.operation=="create"
val deleting=selected.operation=="delete"
val target=state.groups.firstOrNull { it.optString("_id")==selected.id }
val allowed=state.canWrite && when { creating->ProfilePermissions.createGroup(state.site); target==null->false; deleting->ProfilePermissions.deleteGroup(state.site,state.rights(target)); else->ProfilePermissions.editGroup(state.site,state.rights(target)) }
val valid=if(deleting) confirmation==selected.id else name.isNotBlank() && name.length<=256
ManagementDialog(onDismissRequest={ form=null },modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false),title={ Text(if(deleting) t("Confirmar eliminación","Confirm deletion") else if(creating) t("Crear grupo","Create group") else t("Editar grupo","Edit group")) },text={ Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
if(!creating) { Text(selected.name); if(deleting) Text(selected.id) else ProfileDisclosure(t("Identificador","Identifier")) {Text(selected.id)} }
if(deleting) {
Text(t("Acción irreversible desde la app. Puede afectar al acceso a sus dispositivos.","Cannot be undone from the app. May affect access to its devices."))
OutlinedTextField(confirmation,{ if(it.length<=1024) confirmation=it },label={ Text(t("Escribe el ID completo","Type the complete ID")) },singleLine=true)
} else {
OutlinedTextField(name,{ if(it.length<=256) name=it },label={ Text(t("Nombre","Name")) },singleLine=true)
if(creating) Text(t("Grupo de dispositivos con agente.","Agent-based device group."))
OutlinedTextField(description,{ if(it.length<=1024) description=it },label={ Text(t("Descripción","Description")) },maxLines=3)
}
if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
} },confirmButton={ TextButton(enabled=allowed && valid,onClick={
val p=JSONObject().put("action",when { deleting->"deletemesh"; creating->"createmesh"; else->"editmesh" })
if(!creating) p.put("meshid",selected.id)
if(!deleting) { p.put("meshname",name.trim()).put("desc",description); if(creating) p.put("meshtype",2) }
if(session.submit(p)) form=null else error=t("No se ha enviado. Comprueba conexión y permisos.","Not sent. Check connection and permissions.")
}) { Text(if(deleting) t("Eliminar","Delete") else t("Enviar","Send")) } },dismissButton={ TextButton(onClick={ form=null }) { Text(t("Cancelar","Cancel")) } })
} }
userGroupDetails?.let {gid->ReceivedUserGroupDialog(session,state,gid,dismissLabel=if(form?.users==true) t("Volver al usuario","Back to user") else t("Volver a Accesos","Back to Access")) {userGroupDetails=null}}
deviceAssignment?.let {(gid,mid)->GroupMeshAssignmentReview(session,state,gid,mid) {deviceAssignment=null}}
deviceDetails?.let {id->ProfileDeviceDetailsOverlay(source,session,state,profileFlattenReceivedDevices(profileDeviceGroups),id) {deviceDetails=null}}
}
}
