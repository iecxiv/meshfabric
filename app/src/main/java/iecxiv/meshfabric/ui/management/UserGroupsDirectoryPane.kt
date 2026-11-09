package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal fun profileUserGroupDomainAllowed(actor:String,group:String)=actor.startsWith("user/") && actor.split('/').size==3 && group.startsWith("ugrp/") && group.split('/').size==3 && userDomain(actor)==userDomain(group)
@Composable internal fun UserGroupsDirectoryPane(session:ProfileSession,s:ProfileState,modifier:Modifier=Modifier,onOpenUser:(String)->Unit,onOpenDeviceGroup:(String)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val displayNames=rememberProfileDisplayNames(s,session.broadcastServerLabel())
    var query by rememberSaveable(s.id) { mutableStateOf("") }
var editing by remember(s.id) { mutableStateOf<JSONObject?>(null) }
var creating by remember(s.id) {mutableStateOf(false)}
var deleting by remember(s.id) {mutableStateOf<JSONObject?>(null)}
var removing by remember(s.id) {mutableStateOf<Pair<JSONObject,String>?>(null)}
var adding by remember(s.id) {mutableStateOf<JSONObject?>(null)}
var grantingMesh by remember(s.id) {mutableStateOf<String?>(null)}
var reviewingMesh by remember(s.id) {mutableStateOf<Pair<String,String>?>(null)}
var editingMesh by remember(s.id) {mutableStateOf<Pair<String,String>?>(null)}
LaunchedEffect(s.ready) {if(!s.ready) {editing=null;creating=false;deleting=null;removing=null;adding=null;reviewingMesh=null;grantingMesh=null;editingMesh=null}}
    LaunchedEffect(s.ready,s.id,s.site,s.userGroupPhase) { if(s.ready && s.userGroupPhase=="idle") session.loadUserGroupDirectory() }
    val rows=s.listedUserGroups.orEmpty().filter { deviceGroupMatches(query,it.optString("name"),it.optString("desc")) }
val refreshAction=userGroupsRefreshAction(s.ready,s.busy,s.loading,s.userGroupPhase,s.notice)
val feedback=userGroupsNoticeText(s.notice,es)
    LazyColumn(modifier,contentPadding=managementConnectionContentPadding,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(feedback.isNotBlank()) Text(feedback,style=MaterialTheme.typography.bodySmall,color=if(userGroupsNoticeIsError(s.notice)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
ServerGeneralActions {
Column(Modifier.testTag("user-groups-server-actions")) {
TextButton(enabled=refreshAction!=UserGroupsRefreshAction.NONE,onClick={when(refreshAction) {UserGroupsRefreshAction.RECONNECT->session.start();UserGroupsRefreshAction.REFRESH->{session.refresh();session.loadUserGroupDirectory()};UserGroupsRefreshAction.NONE->Unit}}) {Text(if(s.ready) t("Actualizar","Refresh") else t("Reconectar","Reconnect"))}
TextButton(enabled=s.canWrite && s.userGroupPhase=="ready" && profileUserGroupEditRole(s.site),onClick={editing=null;deleting=null;removing=null;creating=true}) {Text(t("Crear grupo de usuarios","Create user group"))}
}
}
if(s.notice=="userGroupCreated" && s.createdUserGroup.isNotBlank()) Text(t("El servidor confirmó la creación: ","The server confirmed creation: ")+displayNames.label(s.createdUserGroup,es),style=MaterialTheme.typography.bodySmall)
            if(s.notice=="userGroupDeleted" && s.deletedUserGroup.isNotBlank()) Text(t("El grupo ","Group ")+displayNames.label(s.deletedUserGroup,es)+t(" ha sido eliminado. Borrado confirmado por el servidor."," has been deleted. Deletion confirmed by the server."),style=MaterialTheme.typography.bodySmall)
if(s.notice=="userGroupMemberObserved") Text(t("Enlace ausente en la consulta del grupo. Usuario: ","Link absent in the group read. User: ")+displayNames.label(s.removedMember,es)+t("; grupo: ","; group: ")+displayNames.label(s.removedMemberGroup,es),style=MaterialTheme.typography.bodySmall)
if(s.notice=="userGroupMemberAddedObserved") Text(t("Enlace de alta observado en el grupo. Usuario: ","Added link observed in the group. User: ")+displayNames.label(s.addedMember,es)+t("; grupo: ","; group: ")+displayNames.label(s.addedMemberGroup,es),style=MaterialTheme.typography.bodySmall)
if(s.notice=="groupMeshRevokedObserved") Text(t("Ambos enlaces observados ausentes. Grupo de usuarios: ","Both links observed absent. User group: ")+displayNames.label(s.revokedGroup,es)+t("; grupo de dispositivos: ","; device group: ")+displayNames.label(s.revokedDeviceGroup,es),style=MaterialTheme.typography.bodySmall)
if(s.notice=="groupMeshGrantedObserved") Text(t("Derechos solicitados observados en ambos enlaces. Grupo de usuarios: ","Requested rights observed in both links. User group: ")+displayNames.label(s.grantedGroup,es)+t("; grupo de dispositivos: ","; device group: ")+displayNames.label(s.grantedDeviceGroup,es),style=MaterialTheme.typography.bodySmall)
if(s.notice=="groupMeshEditedObserved") Text(t("Máscara exacta editada y observada en ambos enlaces. Grupo de usuarios: ","Edited exact mask observed in both links. User group: ")+displayNames.label(s.editedGroup,es)+t("; grupo de dispositivos: ","; device group: ")+displayNames.label(s.editedDeviceGroup,es),style=MaterialTheme.typography.bodySmall)
OutlinedTextField(query,{query=it.take(256)},label={Text(t("Buscar grupo","Search groups"))},singleLine=true,modifier=Modifier.fillMaxWidth())
            if(s.busy || s.loading || s.userGroupPhase=="loading" || s.notice=="connecting") LinearProgressIndicator(Modifier.fillMaxWidth())
if(s.notice=="userGroupObserved") Text(t("Estado del grupo verificado en una consulta al servidor.","Group state verified by a server read."),style=MaterialTheme.typography.bodySmall)
            if(s.userGroupPhase=="failed") Text(t("No se ha podido consultar el listado. Puedes volver a actualizar.","The list could not be retrieved. Refresh to try again."),color=MaterialTheme.colorScheme.error)
            if(s.ready && ProfilePermissions.normalized(s.site) and 256L==0L) Text(t("Vista limitada: el servidor proporciona solo nombres.","Limited view: the server provides names only."),style=MaterialTheme.typography.bodySmall)
            if(s.userGroupPhase=="ready" && rows.isEmpty()) Text(if(query.isBlank()) t("No se han recibido grupos del dominio actual.","No groups were received for the current domain.") else t("Sin coincidencias.","No matches."))
            }
        }
        if(rows.isNotEmpty()) item {Text(t("Grupos","Groups"),style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("user-groups-list-heading"))}
        items(rows,key={it.optString("_id")}) { group->
            ManagementCard {
                Text(group.optString("name").ifBlank { t("Grupo sin nombre","Unnamed group") },style=MaterialTheme.typography.titleSmall)
                if(group.optString("desc").isNotBlank()) Text(group.optString("desc"),style=MaterialTheme.typography.bodySmall)
                TextButton(enabled=s.canWrite && s.userGroupPhase=="ready" && profileUserGroupEditRole(s.site) && runCatching {profileUserGroupBefore(group)}.isSuccess,onClick={editing=JSONObject(group.toString())}) {Text(t("Editar","Edit"))}
TextButton(enabled=s.canWrite && s.userGroupPhase=="ready" && profileUserGroupEditRole(s.site) && profileUserGroupDeleteShape(s.id,group),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error),onClick={editing=null;creating=false;deleting=JSONObject(group.toString());session.loadUserGroupDirectory()}) {Text(t("Eliminar","Delete"))}
if(profileUserGroupEditRole(s.site) && !profileUserGroupDeleteShape(s.id,group)) Text(t("Borrado no disponible: datos incompletos, enlaces no compatibles o restricciones de grupo externo.","Deletion unavailable: incomplete data, unsupported links or external-group restrictions."),style=MaterialTheme.typography.bodySmall)
TextButton(enabled=s.canWrite && s.userGroupPhase=="ready" && ProfilePermissions.manageUsers(s.site) && profileUserGroupEditRole(s.site) && group.isNull("membershipType") && group.opt("links") is JSONObject,onClick={val copy=runCatching {JSONObject(group.toString()).also {profileUserGroupDeleteSnapshot(it)}}.getOrNull();if(copy!=null) {editing=null;creating=false;deleting=null;removing=null;adding=copy;session.refresh()}}) {Text(t("Incorporar usuario","Add user"))}
TextButton(enabled=s.canWrite && s.userGroupPhase=="ready" && profileUserGroupEditRole(s.site),onClick={editing=null;creating=false;deleting=null;removing=null;adding=null;reviewingMesh=null;grantingMesh=group.optString("_id");session.refresh();session.loadUserGroupDirectory()}) {Text(t("Asignar grupo de dispositivos","Assign device group"))}
ProfileDisclosure(t("Miembros y accesos recibidos","Received members and access")) {
                    val links=group.optJSONObject("links")
                    if(links==null) Text(t("El servidor no ha informado detalles de accesos para esta ficha.","The server has not provided access details for this entry."),style=MaterialTheme.typography.bodySmall) else {
                        val ids=links.keys().asSequence().sorted().toList()
val memberGroupUsable=remember(group) {runCatching {profileUserGroupDeleteSnapshot(group)}.isSuccess}
var linkQuery by remember(group.optString("_id")) {mutableStateOf("")}
OutlinedTextField(linkQuery,{linkQuery=it.take(256)},label={Text(t("Buscar enlace recibido","Search received links"))},singleLine=true,modifier=Modifier.fillMaxWidth())
val matchingIds=ids.filter {linkQuery.isBlank() || it.contains(linkQuery,true) || profileReceivedLinkPresentation(s,group,it,es).label.contains(linkQuery,true) || links.optJSONObject(it)?.optString("name")?.contains(linkQuery,true)==true}
                        if(ids.isEmpty()) Text(t("Sin asignaciones recibidas.","No assignments received."))
                        for(id in matchingIds.take(100)) {
val presentation=profileReceivedLinkPresentation(s,group,id,es)
if(profileUserGroupMemberId(s.id,id)) TextButton(enabled=presentation.usable && s.canWrite && s.userGroupPhase=="ready" && profileUserGroupEditRole(s.site) && memberGroupUsable && group.isNull("membershipType") && links.opt(id) is JSONObject,onClick={editing=null;creating=false;deleting=null;removing=JSONObject(group.toString()) to id;session.loadUserGroupDirectory()}) {Text(t("Retirar del grupo: ","Remove from group: ")+presentation.label)}
                            val user=s.users.firstOrNull {it.optString("_id")==id}
                            val mesh=s.groups.firstOrNull {it.optString("_id")==id}
if(profileGroupMeshId(s.id,id)) TextButton(enabled=presentation.usable && s.canWrite && s.userGroupPhase=="ready",onClick={editing=null;creating=false;deleting=null;removing=null;adding=null;reviewingMesh=group.optString("_id") to id;session.refresh();session.loadUserGroupDirectory()}) {Text(t("Revisar asignación: ","Review assignment: ")+presentation.label)}
if(profileGroupMeshId(s.id,id)) TextButton(enabled=presentation.usable && s.canWrite && !s.detailsBusy && s.userGroupPhase=="ready" && profileUserGroupEditRole(s.site),onClick={editing=null;creating=false;deleting=null;removing=null;adding=null;reviewingMesh=null;grantingMesh=null;editingMesh=group.optString("_id") to id}) {Text(t("Editar derechos de asignación: ","Edit assignment rights: ")+presentation.label)}
                            when {
                                user!=null && canAdministerUser(s,user)->TextButton(enabled=presentation.usable && s.canWrite,onClick={onOpenUser(id)}) {Text(presentation.label)}
                                mesh!=null->TextButton(enabled=presentation.usable && s.ready && !s.busy,onClick={onOpenDeviceGroup(id)}) {Text(presentation.label)}
                                else->Text(presentation.label,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        if(matchingIds.size>100) Text(t("Se muestran los primeros 100 enlaces coincidentes; busca para acotar.","Showing the first 100 matching links; search to narrow them down."),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
grantingMesh?.let {GroupMeshGrantDialog(session,s,it) {grantingMesh=null}}
editing?.let {UserGroupEditDialog(session,s,it) {editing=null}}
if(creating && editing==null) UserGroupCreateDialog(session,s) {creating=false}
deleting?.let {UserGroupDeleteDialog(session,s,it) {deleting=null}}
removing?.let {(group,id)->UserGroupMemberRemoveDialog(session,s,group,id) {removing=null}}
adding?.let {UserGroupMemberAddDialog(session,s,it) {adding=null}}
reviewingMesh?.let {(gid,mid)->GroupMeshAssignmentReview(session,s,gid,mid) {reviewingMesh=null}}
editingMesh?.let {(gid,mid)->key(gid,mid) {GroupMeshEditDialog(session,s,gid,mid) {editingMesh=null}}}
}
