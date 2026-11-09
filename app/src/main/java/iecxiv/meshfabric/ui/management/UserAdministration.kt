package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun userDomain(id:String)=id.split('/').getOrNull(1) ?: "\u0000"
internal fun userIsLocked(site:Long)=ProfilePermissions.normalized(site)!=0xffffffffL && ProfilePermissions.normalized(site) and 32L != 0L
internal fun userLockMask(site:Long,locked:Boolean):Long? {
val value=ProfilePermissions.normalized(site)
if(value==0xffffffffL) return null
return if(locked) value or 32L else value and 32L.inv() and 0xffffffffL
}
internal fun validUserEmail(value:String)=value.length<=320 && value.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
internal fun validUserCreation(name:String,email:String,password:String,repeated:String)=name.isNotBlank() && name.length<=256 && name.none { it=='/' || it.isISOControl() || it.isWhitespace() } && validUserEmail(email) && password.isNotEmpty() && password.length<=256 && password==repeated
internal fun canAdministerUser(s:ProfileState,u:JSONObject)=ProfilePermissions.manageUsers(s.site) && u.optString("_id").startsWith("user/") && userDomain(s.id)==userDomain(u.optString("_id")) && (ProfilePermissions.normalized(s.site)==0xffffffffL || ProfilePermissions.normalized(u.optLong("siteadmin",0))!=0xffffffffL)
internal fun canLockUser(s:ProfileState,u:JSONObject)=canAdministerUser(s,u) && u.optString("_id")!=s.id && userLockMask(u.optLong("siteadmin",0),true)!=null
internal fun directUserRights(links:JSONObject?,id:String)=ProfilePermissions.normalized((links?.optJSONObject(id)?.opt("rights") as? Number)?.toLong() ?: 0L)
private fun userAccess(s:ProfileState,u:JSONObject,id:String,mesh:String=""):Long {
val uid=u.optString("_id")
if(userDomain(uid)!=userDomain(id)) return 0
if(ProfilePermissions.normalized(u.optLong("siteadmin",0))==0xffffffffL) return 0xffffffffL
val target=if(id.startsWith("mesh/")) s.groups.firstOrNull { it.optString("_id")==id } else s.nodes?.firstOrNull { it.optString("_id")==id }
var rights=directUserRights(u.optJSONObject("links"),id) or directUserRights(target?.optJSONObject("links"),uid)
for(group in s.userGroups.orEmpty()) {
val gid=group.optString("_id")
if(u.optJSONObject("links")?.has(gid)==true || group.optJSONObject("links")?.has(uid)==true) rights=rights or directUserRights(group.optJSONObject("links"),id) or directUserRights(target?.optJSONObject("links"),gid)
}
if(mesh.isNotBlank()) rights=rights or userAccess(s,u,mesh)
return ProfilePermissions.normalized(rights)
}
internal fun canGrantDevice(s:ProfileState,node:JSONObject)=s.user?.let { userAccess(s,it,node.optString("_id"),node.optString("meshid")) and 2L != 0L }==true
private fun userDate(value:Any?):String=runCatching {
val instant=if(value is Number) { val n=value.toLong(); require(n>0); Instant.ofEpochMilli(if(n<100000000000L) Math.multiplyExact(n,1000L) else n) } else Instant.parse(value.toString())
DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(instant)
}.getOrDefault("")
private fun userRights(value:Long,es:Boolean,mesh:Boolean=false):String {
val mask=ProfilePermissions.normalized(value)
if(mask==0xffffffffL) return if(mesh) { if(es) "Todos los permisos" else "All permissions" } else { if(es) "Administrador total" else "Full administrator" }
if(mask==0L) return if(mesh) { if(es) "Ninguno" else "None" } else { if(es) "Usuario" else "User" }
if(mesh) return deviceGroupRightsLabel(mask,es)
val labels=if(es) listOf("Copia de seguridad","Administrar usuarios","Restaurar servidor","Acceso a archivos","Actualizar servidor","Bloqueado","Sin nuevos grupos","Sin MeshCmd","Grupos de usuarios","Grabaciones","Configuración bloqueada","Todos los eventos","Sin nuevos dispositivos") else listOf("Server backup","Manage users","Server restore","File access","Server update","Locked","No new groups","No MeshCmd","User groups","Recordings","Locked settings","All events","No new devices")
return labels.mapIndexedNotNull { i,label->label.takeIf { mask and (1L shl i)!=0L } }.joinToString(", ").ifBlank { if(es) "Permisos adicionales" else "Additional rights" }
}

@Composable internal fun UserAdministrationDialog(session:ProfileSession,s:ProfileState,form:ProfileForm,onOpenGroup:(String)->Unit={},onOpenDevice:((String)->Unit)?=null,canOpenDevice:(String)->Boolean={false},onOpenUserGroup:((String)->Unit)?=null,canOpenUserGroup:(String)->Boolean={false},onDismiss:()->Unit) {
key(form) {
val es=LocalConfiguration.current.locales[0].language=="es"
fun t(a:String,b:String)=if(es) a else b
val creating=form.operation=="create"
val deleting=form.operation=="delete"
val locking=form.operation in setOf("lock","unlock")
val editing=!creating && !deleting && !locking
val user=s.users.firstOrNull { it.optString("_id")==form.id }
val original=remember { user?.let { JSONObject(it.toString()) } }
var name by remember { mutableStateOf("") }
var email by remember { mutableStateOf(original?.optString("email").orEmpty()) }
var realname by remember { mutableStateOf(original?.optString("realname").orEmpty()) }
var password by remember { mutableStateOf("") }
var repeated by remember { mutableStateOf("") }
var removeEvents by remember { mutableStateOf(false) }
var reset by remember { mutableStateOf(false) }
var approved by remember { mutableStateOf(false) }
var typedId by remember { mutableStateOf("") }
var mode by remember { mutableStateOf("profile") }
var advanced by remember { mutableStateOf(false) }
var directMesh by remember {mutableStateOf<String?>(null)}
var directGrant by remember {mutableStateOf(false)}
var directEdit by remember {mutableStateOf<String?>(null)}
var contact by remember { mutableStateOf(false) }
var contactField by remember { mutableStateOf("phone") }
var nodeId by remember { mutableStateOf("") }
var search by remember { mutableStateOf("") }
var deviceRights by remember { mutableLongStateOf(264L) }
var feedback by remember { mutableStateOf("") }
LaunchedEffect(form.id) { if(!creating && !deleting && !locking) session.loadDetails() }
DisposableEffect(session,form.id) { onDispose { session.clearHistory() } }
fun submit(p:JSONObject) {
if(session.submit(p)) { password=""; repeated=""; onDismiss() } else feedback=t("No se ha enviado. Comprueba permisos, conexión y datos actuales; si cambiaron, cierra y vuelve a abrir la ficha.","Not sent. Check permissions, connection and current data; if changed, close and reopen the details.")
}
@Composable fun field(label:String,value:String,change:(String)->Unit,secret:Boolean=false,enabled:Boolean=true) {
OutlinedTextField(value,{ if(it.length<=if(secret) 256 else 320) change(it) },label={ Text(label) },singleLine=true,enabled=enabled,visualTransformation=if(secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,modifier=Modifier.fillMaxWidth())
}
@Composable fun option(label:String,value:Boolean,change:(Boolean)->Unit) { Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(value,change); Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium) } }
val allowed=s.canWrite && (if(creating) s.canCreateUser else user!=null && canAdministerUser(s,user))
val contactReady=allowed && email==original?.optString("email").orEmpty() && realname==original?.optString("realname").orEmpty()
val selectedNode=s.nodes?.firstOrNull { it.optString("_id")==nodeId }
val newName=if(s.emailIdentity) email.trim() else name.trim()
val valid=when {
creating->validUserCreation(newName,email.trim(),password,repeated)
deleting->typedId==form.id && form.id!=s.id
locking->user!=null && canLockUser(s,user) && approved && userIsLocked(user.optLong("siteadmin",0))==(form.operation=="unlock")
mode=="password"->approved && form.id!=s.id && password.isNotEmpty() && password.length<=256 && password==repeated
mode=="device"->approved && selectedNode!=null && canGrantDevice(s,selectedNode) && deviceRights>0 && deviceRights and 7L == 0L
mode=="history"->false
else->original!=null && (email.isEmpty() || validUserEmail(email)) && realname.length<=256 && ((!s.emailIdentity && email!=original.optString("email")) || realname!=original.optString("realname"))
}
if(directEdit!=null) { key(form.id,directEdit) { UserMeshEditDialog(session,s,form.id,requireNotNull(directEdit)) {directEdit=null} } } else if(directGrant) { UserMeshGrantDialog(session,s,form.id) {directGrant=false} } else if(directMesh!=null) { key(form.id,directMesh) { UserMeshAssignmentReview(session,s,form.id,requireNotNull(directMesh)) {directMesh=null} } } else if(contact && user!=null) { UserContactEditor(session,s,user,onDismiss={contact=false},onSaved=onDismiss,initialField=contactField) } else if(advanced && user!=null) { UserAdvancedEditor(session,s,user,onDismiss={advanced=false},onSaved=onDismiss) } else {
ManagementDialog(onDismissRequest=onDismiss,modifier=if(editing) Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f) else Modifier,properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=!editing),title={ Text(when { creating->t("Crear usuario","Create user"); deleting->t("Eliminar usuario","Delete user"); locking->if(form.operation=="lock") t("Bloquear usuario","Lock user") else t("Desbloquear usuario","Unlock user"); mode=="password"->t("Cambiar contraseña","Change password"); mode=="device"->t("Agregar dispositivo","Add device"); mode=="history"->t("Inicios de sesión anteriores","Previous logins"); else->t("Editar usuario","Edit user") }) },text={
Column(Modifier.fillMaxWidth().heightIn(max=if(editing) (LocalConfiguration.current.screenHeightDp*0.68f).dp else 440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
if(!creating) { Text(user?.optString("name").orEmpty().ifBlank { form.name },style=MaterialTheme.typography.titleSmall); if(deleting || locking) Text(form.id,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) else ProfileDisclosure(t("Identificador","Identifier")) { Text(form.id,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
when {
creating->{
if(!s.emailIdentity) field(t("Nombre de usuario","Username"),name,{ name=it })
field(t("Correo electrónico","Email"),email,{ email=it })
field(t("Contraseña","Password"),password,{ password=it },true)
field(t("Repetir contraseña","Repeat password"),repeated,{ repeated=it },true)
if(repeated.isNotEmpty() && password!=repeated) Text(t("Las contraseñas no coinciden.","Passwords do not match."),color=MaterialTheme.colorScheme.error)
option(t("Remover todos los eventos anteriores para este ID de usuario.","Remove all previous events for this user ID."),removeEvents,{ removeEvents=it })
if(removeEvents) Text(t("Eliminará el historial anterior asociado al ID si existe. Esta operación no se puede deshacer desde la app.","Deletes previous history associated with the ID if present. This cannot be undone from the app."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
option(t("Forzar restablecimiento de contraseña en el próximo inicio de sesión.","Force password reset at next sign-in."),reset,{ reset=it })
}
deleting->{ Text(t("Eliminar esta cuenta es irreversible desde la app.","Deleting this account cannot be undone from the app.")); field(t("Escribe el ID completo","Type the complete ID"),typedId,{ typedId=it }) }
locking->{ Text(t("Se cambiará únicamente el estado de bloqueo, conservando los demás permisos. No se bloquea tu propia cuenta ni un administrador total desde esta acción.","Only the lock state changes; other rights are preserved. This action cannot lock your own account or a full administrator.")); option(t("Confirmo el cambio de estado de esta cuenta.","I confirm changing this account's lock state."),approved,{ approved=it }) }
user==null->Text(t("Usuario no disponible.","User unavailable."))
mode=="password"->{
field(t("Nueva contraseña","New password"),password,{ password=it },true)
field(t("Repetir contraseña","Repeat password"),repeated,{ repeated=it },true)
option(t("Forzar restablecimiento en el próximo inicio de sesión","Force reset at next sign-in"),reset,{ reset=it })
option(t("Confirmo cambiar la contraseña de esta cuenta.","I confirm changing this account's password."),approved,{ approved=it })
Text(t("Se conservan sus métodos de autenticación multifactor. Si el servidor no confirma el cambio, el resultado quedará sin confirmar.","Existing multifactor methods are preserved. Without a server confirmation the outcome remains unconfirmed."),style=MaterialTheme.typography.bodySmall)
}
mode=="device"->{
Text(t("Concede acceso directo a un dispositivo existente; no instala un agente. Solo se muestran dispositivos visibles para tu cuenta.","Grants direct access to an existing device; does not install an agent. Only devices visible to your account are shown."),style=MaterialTheme.typography.bodySmall)
if(s.detailsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
field(t("Buscar dispositivo","Search device"),search,{ search=it })
if(s.nodes==null) Text(t("Listado de dispositivos no disponible.","Device list unavailable."))
val options=s.nodes.orEmpty().filter { it.optString("name").contains(search,true) || it.optString("_id").contains(search,true) }.take(100)
for(node in options) TextButton(enabled=canGrantDevice(s,node),onClick={ nodeId=node.optString("_id"); approved=false }) { Text((if(nodeId==node.optString("_id")) "• " else "")+node.optString("name")+"\n"+node.optString("_id")) }
Text(t("Máximo 100 resultados por búsqueda.","Up to 100 results per search."),style=MaterialTheme.typography.bodySmall)
val rights=listOf(8L to t("Control remoto","Remote control"),256L to t("Solo visualización","View only"),16L to t("Consola del agente","Agent console"),64L to t("Despertar dispositivo","Wake device"),128L to t("Editar notas","Edit notes"),16384L to t("Chat y notificaciones","Chat and notifications"),131072L to t("Ejecutar comandos","Run commands"),1048576L to t("Detalles del dispositivo","Device details"))
for((bit,label) in rights) option(label,deviceRights and bit != 0L,{ checked->deviceRights=if(checked) deviceRights or bit else deviceRights and bit.inv(); approved=false })
Text(t("El acceso efectivo puede incluir permisos heredados adicionales; esto no los revoca.","Effective access can include additional inherited rights; this does not revoke them."),style=MaterialTheme.typography.bodySmall)
option(t("Confirmo asignar estos permisos al usuario y dispositivo indicados.","I confirm assigning these rights to the indicated user and device."),approved,{ approved=it })
}
mode=="history"->{
Text(t("Historial de accesos web registrado por el servidor; puede no incluir los accesos de la app.","Server-recorded web sign-ins; app sign-ins may not be included."),style=MaterialTheme.typography.bodySmall)
if(s.historyBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
if(s.historyFailed) Text(t("Historial no disponible o respuesta incompatible.","History unavailable or unsupported response."),color=MaterialTheme.colorScheme.error)
val a=s.history.takeIf { s.historyUser==form.id }
if(a!=null && a.length()==0) Text(t("Sin registros.","No records."))
if(a!=null) for(i in 0 until minOf(a.length(),200)) a.optJSONObject(i)?.let { event->
Text(userDate(event.opt("t")).ifBlank { t("Fecha no disponible","Date unavailable") },style=MaterialTheme.typography.titleSmall)
Text(loginHistoryLabel(event.opt("m"),es)+" · "+(event.optJSONArray("a")?.let { args->(0 until args.length()).joinToString(" · ") { args.optString(it) } }.orEmpty()),style=MaterialTheme.typography.bodySmall)
HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
TextButton(onClick={ session.previousLogins(form.id) },enabled=!s.historyBusy) { Text(t("Actualizar","Refresh")) }
}
else->{
val missing=t("No disponible","Not available")
ManagementCard {
field(t("Correo electrónico","Email"),email,{ email=it },enabled=!s.emailIdentity)
if(userEmailStatusVisible(s.server?.opt("emailcheck"))) TextButton(enabled=contactReady,onClick={ contactField="emailVerified"; contact=true }) { Text(t("Estado del correo: ","Email status: ")+if(user.optBoolean("emailVerified")) t("Verificado","Verified") else t("No verificado","Not verified")) }
if(s.emailIdentity) Text(t("El correo identifica la cuenta y no se modifica con esta operación.","Email identifies the account and cannot be changed with this operation."),style=MaterialTheme.typography.bodySmall)
field(t("Nombre real","Real name"),realname,{ realname=it })
if(allowed && !contactReady && (userEmailStatusVisible(s.server?.opt("emailcheck")) || userPhoneVisible(s.server?.opt("features"),user.has("phone") && !user.isNull("phone")))) Text(t("Guarda primero los cambios de correo y nombre real antes de editar el contacto.","Save email and real-name changes before editing contact information."),style=MaterialTheme.typography.bodySmall)
if(userPhoneVisible(s.server?.opt("features"),user.has("phone") && !user.isNull("phone"))) TextButton(enabled=contactReady,onClick={ contactField="phone"; contact=true }) { Text(t("Teléfono: ","Phone: ")+user.optString("phone").ifBlank { t("No establecido","Not set") }) }
ProfileDisclosure(t("Información adicional","Additional information")) {
ManagementInfoRow(t("Dominio","Domain"),userDomain(form.id).ifBlank { t("Por defecto","Default") })
ManagementInfoRow(t("Características","Features"),if(user.has("flags")) user.optLong("flags").let { if(it==0L) t("Ninguno","None") else "0x"+it.toString(16) } else missing)
ManagementInfoRow(t("Permisos del servidor","Server rights"),userRights(user.optLong("siteadmin",0),es))
ManagementInfoRow(t("Creación","Created"),userDate(user.opt("creation")).ifBlank { missing })
ManagementInfoRow(t("Último ingreso","Last access"),userDate(user.opt("access")).ifBlank { missing })
val groupList=s.groups.filter { userAccess(s,user,it.optString("_id"))!=0L }
ManagementInfoRow(t("Grupos de dispositivos","Device groups"),if(s.userGroups==null) missing else "${groupList.size} "+t("grupos visibles","visible groups"))
val realms=user.optJSONArray("groups")
ManagementInfoRow(t("Reinos del administrador","Administrator realms"),if(realms==null) missing else if(realms.length()==0) t("Ninguno","None") else (0 until realms.length()).joinToString(", ") { realms.optString(it) })
ManagementInfoRow(t("Consentimiento del usuario","User consent"),if(user.has("consent")) user.optLong("consent").let { if(it==0L) t("Ninguno","None") else "0x"+it.toString(16) } else missing)
fun present(key:String)=when(val v=user.opt(key)) { is Boolean->v; is Number->v.toLong()>0; is JSONArray->v.length()>0; is String->v.isNotBlank(); else->false }
val security=buildList { if(present("otpsecret")) add(t("Aplicación de autenticación","Authenticator app")); if(present("otphkeys")) add(t("Llave de seguridad","Security key")); if(present("otpkeys")) add(t("Códigos de respaldo","Backup codes")) }
ManagementInfoRow(t("Seguridad","Security"),security.joinToString(", ").ifBlank { missing })
ManagementInfoRow(t("Sesiones activas","Active sessions"),s.sessions?.optInt(form.id,0)?.toString() ?: missing)
}
}
if(s.detailsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
ProfileGroupActionButton(t("Permisos, características y consentimiento","Permissions, features and consent"),Icons.Default.Security,allowed,onClick={ advanced=true })
ProfileGroupActionButton(t("Actualizar ficha","Refresh details"),Icons.Default.Refresh,!s.detailsBusy,onClick=session::loadDetails)
ProfileDisclosure(t("Accesos directos a grupos de dispositivos","Direct device-group access")) {
ProfileGroupActionButton(t("Conceder acceso directo","Grant direct access"),Icons.Default.Security,contactReady && !s.detailsBusy,onClick={directGrant=true;session.refresh()})
Text(t("Solo enlaces directos recibidos. No representa todos los permisos efectivos ni los accesos heredados.","Received direct links only. This does not represent all effective rights or inherited access."),style=MaterialTheme.typography.bodySmall)
val directGroups=s.groups.filter {g->user.optJSONObject("links")?.has(g.optString("_id"))==true || g.optJSONObject("links")?.has(form.id)==true}
if(directGroups.isEmpty()) Text(t("No se han recibido enlaces directos con los grupos visibles.","No direct links received for visible groups."))
for(g in directGroups) ManagementCard {
val presentation=profileReceivedLinkPresentation(s,user,g.optString("_id"),es)
Text(presentation.label,style=MaterialTheme.typography.titleSmall)
ProfileGroupActionButton(t("Revisar acceso directo","Review direct access"),Icons.Default.Security,presentation.usable && contactReady && !s.detailsBusy,onClick={directMesh=g.optString("_id");session.refresh()})
ProfileGroupActionButton(t("Editar derechos directos","Edit direct rights"),Icons.Default.Edit,presentation.usable && contactReady && !s.detailsBusy,onClick={directEdit=g.optString("_id")})
if(!presentation.usable) Text(t("Ficha incompleta o ambigua. Actualiza antes de abrir acciones para este grupo.","Incomplete or ambiguous entry. Refresh before opening actions for this group."),style=MaterialTheme.typography.bodySmall)
}
}
ProfileDisclosure(t("Grupos de dispositivos en común","Device groups in common")) {
Text(t("Solo los datos visibles recibidos; los permisos heredados requieren el listado de grupos de usuarios.","Received visible data only; inherited rights require the user-group list."),style=MaterialTheme.typography.bodySmall)
if(s.userGroups==null) Text(missing) else {
val common=s.groups.filter { group->s.user?.let { userAccess(s,it,group.optString("_id"))!=0L }==true && userAccess(s,user,group.optString("_id"))!=0L }
if(common.isEmpty()) Text(t("No se observan grupos en común en los datos disponibles.","No common groups observed in available data."))
for(group in common) ManagementCard {
val presentation=profileReceivedLinkPresentation(s,user,group.optString("_id"),es)
ManagementInfoRow(presentation.label,userRights(userAccess(s,user,group.optString("_id")),es,true))
ProfileGroupActionButton(t("Abrir grupo","Open group"),Icons.Default.Computer,presentation.usable && contactReady,onClick={onOpenGroup(group.optString("_id"))})
if(!presentation.usable) Text(t("Ficha incompleta o ambigua. Actualiza antes de abrir este grupo.","Incomplete or ambiguous entry. Refresh before opening this group."),style=MaterialTheme.typography.bodySmall)
}
}
}
ProfileDisclosure(t("Grupos de usuarios recibidos","Received user groups")) {
Text(t("Enlaces encontrados en la cuenta o en grupos recibidos. No son los reinos administrativos ni un cálculo de permisos efectivos.","Links found in the account or received groups. These are not administrative realms or a calculation of effective rights."),style=MaterialTheme.typography.bodySmall)
val membershipIds=profileUserMembershipGroupIds(s,user)
if(membershipIds==null) Text(t("Datos de membresía no disponibles.","Membership data unavailable.")) else if(membershipIds.isEmpty()) Text(t("No se han recibido enlaces de membresía.","No membership links received.")) else for(gid in membershipIds) {
val membershipPresentation=profileReceivedLinkPresentation(s,user,gid,es)
ManagementCard {
Text(membershipPresentation.label,style=MaterialTheme.typography.titleSmall)
if(membershipPresentation.usable && onOpenUserGroup!=null && canOpenUserGroup(gid)) TextButton(onClick={onOpenUserGroup(gid)}) {Text(t("Abrir grupo de usuarios","Open user group"))} else Text(t("Ficha no recibida, ambigua o temporalmente no disponible.","Entry not received, ambiguous or temporarily unavailable."),style=MaterialTheme.typography.bodySmall)
}
}
TextButton(enabled=s.ready && !s.busy && !s.loading && !s.detailsBusy && s.userGroupPhase!="loading",onClick={session.refresh();session.loadDetails();session.loadUserGroupDirectory()}) {Text(t("Actualizar membresías recibidas","Refresh received memberships"))}
}
ProfileDisclosure(t("Dispositivos en común","Devices in common")) {
if(s.nodes==null || s.userGroups==null) Text(missing) else {
val common=s.nodes.filter { userAccess(s,user,it.optString("_id"),it.optString("meshid"))!=0L }
if(common.isEmpty()) Text(t("No se observan dispositivos en común en los datos disponibles.","No common devices observed in available data."))
for(node in common.take(100)) ManagementCard {
val nodePresentation=profileReceivedLinkPresentation(s,user,node.optString("_id"),es)
val groupLabel=if(node.optString("meshid").isNotBlank()) profileReceivedLinkPresentation(s,user,node.optString("meshid"),es).label else t("Grupo no disponible","Group unavailable")
ManagementInfoRow(nodePresentation.label,groupLabel)
if(nodePresentation.usable && onOpenDevice!=null && canOpenDevice(node.optString("_id"))) TextButton(onClick={onOpenDevice(node.optString("_id"))}) {Text(t("Abrir ficha","Open details"))}
}
if(common.size>100) Text(t("Se muestran los primeros 100 dispositivos.","Showing the first 100 devices."))
}
}
ProfileDisclosure(t("Acciones y seguridad","Actions and security")) {
ProfileGroupActionButton(t("Agregar dispositivo","Add device"),Icons.Default.Computer,s.canWrite,onClick={ mode="device"; approved=false; session.loadDetails() })
ProfileGroupActionButton(t("Cambiar contraseña","Change password"),Icons.Default.Lock,s.canWrite && form.id!=s.id,onClick={ mode="password"; approved=false; password=""; repeated="" })
ProfileGroupActionButton(t("Inicios de sesión anteriores","Previous logins"),Icons.Default.History,true,onClick={ mode="history"; if(!session.previousLogins(form.id)) feedback=t("No se pudo solicitar el historial.","Could not request history.") })
}
}
}
if(feedback.isNotBlank()) Text(feedback,color=MaterialTheme.colorScheme.error)
}
},confirmButton={ if(mode!="history") TextButton(enabled=allowed && valid,onClick={
when {
creating->submit(JSONObject().put("action","adduser").put("username",newName).put("email",email.trim()).put("pass",password).put("removeEvents",removeEvents).put("resetNextLogin",reset))
deleting->submit(JSONObject().put("action","deleteuser").put("userid",form.id).put("username",user?.optString("name")))
locking->user?.let { u->userLockMask(u.optLong("siteadmin",0),form.operation=="lock")?.let { mask->submit(JSONObject().put("action","edituser").put("id",form.id).put("siteadmin",mask).put("_previousSite",ProfilePermissions.normalized(u.optLong("siteadmin",0)))) } }
mode=="password"->submit(JSONObject().put("action","changeuserpass").put("userid",form.id).put("pass",password).put("resetNextLogin",reset).put("removeMultiFactor",false))
mode=="device"->submit(JSONObject().put("action","adddeviceuser").put("nodeid",nodeId).put("nodename",selectedNode?.optString("name")).put("userids",JSONArray().put(form.id)).put("rights",deviceRights))
else->{ val p=JSONObject().put("action","edituser").put("id",form.id)
if(!s.emailIdentity && email!=original?.optString("email")) p.put("email",email).put("_beforeEmail",original?.optString("email"))
if(realname!=original?.optString("realname")) p.put("realname",realname).put("_beforeRealname",original?.optString("realname"))
submit(p) }
}
}) { Text(if(deleting) t("Eliminar","Delete") else if(creating) t("Crear","Create") else t("Confirmar","Confirm")) } },dismissButton={
Row { if(!creating && !deleting && !locking && mode!="profile") TextButton(onClick={ mode="profile"; password=""; repeated=""; approved=false; session.clearHistory() }) { Text(t("Volver","Back")) }; TextButton(onClick=onDismiss) { Text(t("Cerrar","Close")) } }
})
}
}
}
