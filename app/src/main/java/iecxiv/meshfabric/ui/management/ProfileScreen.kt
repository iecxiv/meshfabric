package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.awaitCancellation
import org.json.JSONObject

internal data class ProfileForm(val users:Boolean,val operation:String,val id:String="",val name:String="",val email:String="",val description:String="")

@Composable internal fun ProfileDestination(source:MeshCentralClient,onDismiss:()->Unit,onNavigate:(Int)->Unit) {
val es=LocalConfiguration.current.locales[0].language=="es"
fun t(s:String,e:String)=if(es) s else e
val lifecycle=LocalLifecycleOwner.current.lifecycle
val session=remember(source) { ProfileSession(source) }
val state by session.state.collectAsState()
val connected by source.connectionState.collectAsState()
var tab by rememberSaveable { mutableIntStateOf(0) }
var form by remember { mutableStateOf<ProfileForm?>(null) }
DisposableEffect(session) { onDispose { session.close() } }
LaunchedEffect(session,lifecycle,connected) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
if(connected && !source.isUserLogout) { session.start(); try { awaitCancellation() } finally { session.stop() } } else session.stop()
} }
LaunchedEffect(state.ready) { if(!state.ready) form=null }
Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,securePolicy=SecureFlagPolicy.SecureOn)) {
Surface(Modifier.fillMaxSize()) {
Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={ ProfileNavigationBar(3,onNavigate) }) { padding->
Column(Modifier.fillMaxSize().padding(padding)) {
Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
Text(t("Perfil","Profile"),Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.titleLarge)
if(tab!=0) TextButton(enabled=!state.loading && !state.busy,onClick={ if(state.ready) session.refresh() else session.start() }) { Text(t("Actualizar","Refresh")) }
}
ScrollableTabRow(selectedTabIndex=tab,edgePadding=0.dp) {
listOf(t("Mi usuario","My user"),t("Usuarios","Users"),t("Grupos de dispositivos","Device groups")).forEachIndexed { index,label->Tab(selected=tab==index,onClick={ tab=index },text={ Text(label) }) }
}
if(tab==0) NativeAccountPane(source,state.id,Modifier.weight(1f).fillMaxWidth()) else {
if(state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
val message=when(state.notice) {
"ready"->""
"confirmed"->t("Solicitud confirmada por el servidor; actualizando.","Server acknowledged the request; refreshing.")
"sent"->t("Enviada; ejecución sin confirmar.","Sent; execution unconfirmed.")
"unknown"->t("Resultado sin confirmar. Actualiza antes de reintentar. Sin reenvíos automáticos.","Outcome unconfirmed. Refresh before retrying. No automatic resends.")
"rejected"->t("El servidor rechazó la solicitud.","The server rejected the request.")
"readFailed"->t("Listado incompleto. Modificaciones bloqueadas hasta actualizar.","Incomplete list. Changes blocked until refresh.")
else->t("Sin conexión o cargando.","Offline or loading.")
}
if(message.isNotBlank()) Text(message,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
val users=tab==1
val permitted=!users || ProfilePermissions.manageUsers(state.site)
val rows=if(users) state.users else state.groups
Button(enabled=state.canWrite && permitted && (users || ProfilePermissions.createGroup(state.site)),onClick={ form=ProfileForm(users,"create") },modifier=Modifier.padding(12.dp)) { Text(if(users) t("Crear usuario","Create user") else t("Crear grupo","Create group")) }
if(!permitted) Text(t("Sin permiso para administrar usuarios.","No permission to manage users."),Modifier.padding(16.dp)) else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
if(rows.isEmpty() && !state.loading) item { Text(if(state.ready && !state.readError) t("No hay elementos.","No items.") else t("Listado no disponible.","List unavailable.")) }
items(rows,key={ it.optString("_id") }) { row->
val id=row.optString("_id")
val name=row.optString("name").ifBlank { id }
Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
Text(name,style=MaterialTheme.typography.titleSmall); Text(id,style=MaterialTheme.typography.bodySmall)
Text(row.optString(if(users) "email" else "desc"),style=MaterialTheme.typography.bodySmall)
Row {
TextButton(enabled=state.canWrite && (if(users) ProfilePermissions.manageUsers(state.site) else ProfilePermissions.editGroup(state.site,state.rights(row))),onClick={ form=ProfileForm(users,"edit",id,name,row.optString("email"),row.optString("desc")) }) { Text(t("Editar","Edit")) }
TextButton(enabled=state.canWrite && (if(users) ProfilePermissions.manageUsers(state.site) && id!=state.id else ProfilePermissions.deleteGroup(state.site,state.rights(row))),onClick={ form=ProfileForm(users,"delete",id,name) }) { Text(t("Eliminar","Delete"),color=MaterialTheme.colorScheme.error) }
}
} }
}
}
}
}
}
}
form?.let { selected->key(selected) {
var name by remember { mutableStateOf(selected.name) }
var email by remember { mutableStateOf(selected.email) }
var description by remember { mutableStateOf(selected.description) }
var password by remember { mutableStateOf("") }
var confirmation by remember { mutableStateOf("") }
var error by remember { mutableStateOf("") }
val creating=selected.operation=="create"
val deleting=selected.operation=="delete"
val target=(if(selected.users) state.users else state.groups).firstOrNull { it.optString("_id")==selected.id }
val allowed=state.canWrite && when {
selected.users->ProfilePermissions.manageUsers(state.site) && (creating || target!=null) && (!deleting || selected.id!=state.id)
creating->ProfilePermissions.createGroup(state.site)
target==null->false
deleting->ProfilePermissions.deleteGroup(state.site,state.rights(target))
else->ProfilePermissions.editGroup(state.site,state.rights(target))
}
val valid=if(deleting) confirmation==selected.id else if(selected.users && !creating) email.length<=320 else name.isNotBlank() && name.length<=256 && (!selected.users || (!name.contains('/') && password.isNotEmpty() && password.length<=256 && email.length<=320))
AlertDialog(onDismissRequest={ form=null },title={ Text(if(deleting) t("Confirmar eliminación","Confirm deletion") else if(creating) t("Crear","Create") else t("Editar","Edit")) },text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
if(!creating) { Text(selected.name); Text(selected.id) }
if(deleting) {
Text(t("Acción irreversible desde la app. Si es un grupo, puede afectar al acceso a sus dispositivos.","Cannot be undone from the app. Deleting a group can affect access to its devices."))
OutlinedTextField(confirmation,{ if(it.length<=1024) confirmation=it },label={ Text(t("Escribe el ID completo","Type the complete ID")) },singleLine=true)
} else {
if(!selected.users || creating) OutlinedTextField(name,{ if(it.length<=256) name=it },label={ Text(t("Nombre","Name")) },singleLine=true)
if(selected.users) {
OutlinedTextField(email,{ if(it.length<=320) email=it },label={ Text(t("Correo electrónico","Email")) },singleLine=true)
if(creating) OutlinedTextField(password,{ if(it.length<=256) password=it },label={ Text(t("Contraseña","Password")) },singleLine=true,visualTransformation=PasswordVisualTransformation())
} else {
if(creating) Text(t("Grupo de dispositivos con agente.","Agent-based device group."))
OutlinedTextField(description,{ if(it.length<=1024) description=it },label={ Text(t("Descripción","Description")) },maxLines=3)
}
}
if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
} },confirmButton={ TextButton(enabled=allowed && valid,onClick={
val action=if(selected.users) when { deleting->"deleteuser"; creating->"adduser"; else->"edituser" } else when { deleting->"deletemesh"; creating->"createmesh"; else->"editmesh" }
val p=JSONObject().put("action",action)
if(!creating) p.put(if(selected.users) "userid" else "meshid",selected.id)
if(!deleting) {
if(selected.users) { p.put("email",email); if(creating) p.put("username",name.trim()).put("pass",password) }
else { p.put("meshname",name.trim()).put("desc",description); if(creating) p.put("meshtype",2) }
}
if(session.submit(p)) { password=""; form=null } else error=t("No se ha enviado. Comprueba conexión y permisos.","Not sent. Check connection and permissions.")
}) { Text(if(deleting) t("Eliminar","Delete") else t("Enviar","Send")) } },dismissButton={ TextButton(onClick={ password=""; form=null }) { Text(t("Cancelar","Cancel")) } })
} }
}
}
