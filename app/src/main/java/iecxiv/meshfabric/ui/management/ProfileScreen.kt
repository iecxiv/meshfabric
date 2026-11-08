package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
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

@Composable internal fun ProfileScreen(source:MeshCentralClient,modifier:Modifier=Modifier) {
val es=LocalConfiguration.current.locales[0].language=="es"
fun t(s:String,e:String)=if(es) s else e
val lifecycle=LocalLifecycleOwner.current.lifecycle
val session=remember(source) { ProfileSession(source) }
val state by session.state.collectAsState()
val connected by source.connectionState.collectAsState()
var section by rememberSaveable { mutableStateOf("account") }
val tab=profileSectionKeys.indexOf(section).coerceAtLeast(0)
var form by remember { mutableStateOf<ProfileForm?>(null) }
DisposableEffect(session) { onDispose { session.close() } }
LaunchedEffect(session,lifecycle,connected) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
if(connected && !source.isUserLogout) { session.start(); try { awaitCancellation() } finally { session.stop() } } else session.stop()
} }
LaunchedEffect(state.ready) { if(!state.ready) form=null }
Column(modifier) {
ManagementSectionTabs(listOf(t("Mi cuenta","My account"),t("Grupos de dispositivos","Device groups"),t("Usuarios","Users")),tab) { section=profileSectionKeys.getOrNull(it) ?: "account"; form=null }
if(tab==0) NativeAccountPane(source,state.id,Modifier.weight(1f).fillMaxWidth()) else {
val message=when(state.notice) {
"ready"->""
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
val rows=if(users) state.users else state.groups
LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
item { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall,color=if(state.notice in setOf("rejected","readFailed","unknown")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
if(state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
ServerGeneralActions {
TextButton(enabled=!state.loading && !state.busy,onClick={ if(state.ready) session.refresh() else session.start() }) { Text(t("Actualizar","Refresh")) }
TextButton(enabled=state.canWrite && permitted && (if(users) state.canCreateUser else ProfilePermissions.createGroup(state.site)),onClick={ form=ProfileForm(users,"create") }) { Text(if(users) t("Crear usuario","Create user") else t("Crear grupo","Create group")) }
}
Text(if(users) t("Usuarios","Users") else t("Grupos de dispositivos","Device groups"),style=MaterialTheme.typography.titleMedium)
} }
if(!permitted) item { ManagementCard { Text(t("Sin permiso para administrar usuarios.","No permission to manage users.")) } } else {
if(rows.isEmpty() && !state.loading) item { ManagementCard { Text(if(state.ready && !state.readError) t("No hay elementos.","No items.") else t("Listado no disponible.","List unavailable.")) } }
items(rows,key={ it.optString("_id") }) { row->
val id=row.optString("_id")
val name=row.optString("name").ifBlank { if(users) id else t("Grupo sin nombre","Unnamed group") }
ManagementCard {
Text(name,style=MaterialTheme.typography.titleSmall)
if(users) Text(id,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
val detail=row.optString(if(users) "email" else "desc")
if(detail.isNotBlank()) Text(detail,style=MaterialTheme.typography.bodyMedium)
if(users && userIsLocked(row.optLong("siteadmin",0))) Text(t("Bloqueado","Locked"),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium)
HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
if(users) TextButton(enabled=state.canWrite && canLockUser(state,row),onClick={ form=ProfileForm(true,if(userIsLocked(row.optLong("siteadmin",0))) "unlock" else "lock",id,name) }) { Text(if(userIsLocked(row.optLong("siteadmin",0))) t("Desbloquear","Unlock") else t("Bloquear","Lock")) }
TextButton(enabled=state.canWrite && (if(users) canAdministerUser(state,row) else ProfilePermissions.editGroup(state.site,state.rights(row))),onClick={ form=ProfileForm(users,"edit",id,name,row.optString("email"),row.optString("desc")) }) { Text(t("Editar","Edit")) }
TextButton(enabled=state.canWrite && (if(users) canAdministerUser(state,row) && id!=state.id else ProfilePermissions.deleteGroup(state.site,state.rights(row))),onClick={ form=ProfileForm(users,"delete",id,name) },colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Text(t("Eliminar","Delete")) }
}
}
}
}
}
}
}
form?.let { selected->
if(selected.users) UserAdministrationDialog(session,state,selected) { form=null } else key(selected) {
var name by remember { mutableStateOf(selected.name) }
var description by remember { mutableStateOf(selected.description) }
var confirmation by remember { mutableStateOf("") }
var error by remember { mutableStateOf("") }
val creating=selected.operation=="create"
val deleting=selected.operation=="delete"
val target=state.groups.firstOrNull { it.optString("_id")==selected.id }
val allowed=state.canWrite && when { creating->ProfilePermissions.createGroup(state.site); target==null->false; deleting->ProfilePermissions.deleteGroup(state.site,state.rights(target)); else->ProfilePermissions.editGroup(state.site,state.rights(target)) }
val valid=if(deleting) confirmation==selected.id else name.isNotBlank() && name.length<=256
ManagementDialog(onDismissRequest={ form=null },properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),title={ Text(if(deleting) t("Confirmar eliminación","Confirm deletion") else if(creating) t("Crear grupo","Create group") else t("Editar grupo","Edit group")) },text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
if(!creating) { Text(selected.name); Text(selected.id) }
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
}
