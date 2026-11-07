package iecxiv.meshfabric.ui.management

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.awaitCancellation
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal data class ProfileForm(val users: Boolean, val operation: String, val id: String = "", val name: String = "", val email: String = "", val description: String = "")

@Composable internal fun ProfileDestination(source: MeshCentralClient, onDismiss: () -> Unit, onNavigate: (Int) -> Unit) {
val es = LocalConfiguration.current.locales[0].language == "es"
fun text(spanish: String, english: String) = if (es) spanish else english
val context = LocalContext.current
val lifecycle = LocalLifecycleOwner.current.lifecycle
val session = remember(source) { ProfileSession(source) }
val state by session.state.collectAsState()
val connected by source.connectionState.collectAsState()
var tab by rememberSaveable { mutableIntStateOf(0) }
var form by remember { mutableStateOf<ProfileForm?>(null) }
var webAction by remember { mutableStateOf<String?>(null) }
var localError by remember { mutableStateOf("") }
DisposableEffect(session) { onDispose { session.close() } }
LaunchedEffect(session, lifecycle, connected) {
lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
if (connected && !source.isUserLogout) {
session.start()
try { awaitCancellation() } finally { session.stop() }
} else session.stop()
}
}
LaunchedEffect(state.ready) { if (!state.ready) { form = null; webAction = null } }
Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
Surface(Modifier.fillMaxSize()) {
Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
topBar = { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
Text(text("Perfil", "Profile"), Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge)
IconButton(onClick = { localError = ""; if (state.ready) session.refresh() else session.start() }, enabled = !state.busy && !state.loading) {
Icon(Icons.Default.Refresh, text("Actualizar", "Refresh"))
}
} },
bottomBar = { ProfileNavigationBar(3, onNavigate) }) { padding ->
Column(Modifier.fillMaxSize().padding(padding)) {
ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
listOf(text("Mi usuario", "My user"), text("Usuarios", "Users"), text("Grupos de dispositivos", "Device groups")).forEachIndexed { index, label ->
Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
}
}
if (state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
val notice = when (state.notice) {
"ready" -> ""
"connecting" -> text("Conectando…", "Connecting…")
"confirmed" -> text("El servidor confirmó la solicitud. Actualizando los datos.", "The server confirmed the request. Refreshing data.")
"rejected" -> text("El servidor rechazó la solicitud. Comprueba permisos y restricciones.", "The server rejected the request. Check permissions and restrictions.")
"sent" -> text("Solicitud enviada; ejecución aún sin confirmar.", "Request sent; execution not yet confirmed.")
"unknown" -> text("Resultado sin confirmar. Actualiza y comprueba los datos antes de reintentar. No se reenviará automáticamente.", "Outcome unconfirmed. Refresh and inspect the data before retrying. Nothing will be automatically resent.")
"readFailed" -> text("No se pudo obtener el listado completo. Las modificaciones están bloqueadas hasta actualizar.", "Could not obtain the complete list. Changes are blocked until refresh.")
"oversize" -> text("Respuesta demasiado grande. Se ha cerrado la conexión de Perfil.", "Response too large. The Profile connection was closed.")
"timeout" -> text("El servidor no respondió a tiempo.", "The server did not respond in time.")
else -> text("Perfil desconectado.", "Profile disconnected.")
}
if (notice.isNotBlank()) Text(notice, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
if (localError.isNotBlank()) Text(localError, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
if (tab == 0) {
LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
item { Text(text("Mi cuenta", "My account"), style = MaterialTheme.typography.titleMedium) }
item { Text(state.user?.optString("name").orEmpty()); Text(state.user?.optString("email").orEmpty()); Text(state.id, style = MaterialTheme.typography.bodySmall) }
item { Text(text("Seguridad y acciones se completan en la interfaz web del servidor. Estos accesos no ejecutan la operación en la app y pueden requerir iniciar sesión de nuevo.", "Security and account actions are completed in the server web interface. These shortcuts do not perform the operation in the app and may require signing in again."), style = MaterialTheme.typography.bodySmall) }
item { Text(text("Seguridad de la cuenta", "Account security"), style = MaterialTheme.typography.titleMedium) }
items(listOf(text("Administrar la aplicación de autenticación", "Manage authenticator app"), text("Administrar claves de seguridad", "Manage security keys"), text("Administrar códigos de respaldo", "Manage backup codes"))) { label ->
OutlinedButton(onClick = { webAction = label }, enabled = state.ready, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
item { Text(text("Acciones", "Actions"), style = MaterialTheme.typography.titleMedium) }
items(listOf(text("Ver inicios de sesión anteriores", "View previous logins"), text("Habilitar notificaciones", "Enable notifications"), text("Opciones de hora e idioma", "Time and language options"), text("Opciones de notificación", "Notification options"), text("Cambiar contraseña", "Change password"), text("Eliminar cuenta", "Delete account"), text("Crear token de inicio de sesión", "Create login token"))) { label ->
OutlinedButton(onClick = { webAction = label }, enabled = state.ready, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
}
} else {
val users = tab == 1
val permitted = !users || ProfilePermissions.manageUsers(state.site)
val rows = if (users) state.users else state.groups
val create = state.canWrite && permitted && (users || ProfilePermissions.createGroup(state.site))
Button(onClick = { form = ProfileForm(users, "create") }, enabled = create, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
Text(if (users) text("Crear usuario", "Create user") else text("Crear grupo", "Create group"))
}
if (!permitted) Text(text("Tu cuenta no tiene permiso para administrar usuarios.", "Your account cannot manage users."), Modifier.padding(16.dp))
else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
if (rows.isEmpty() && !state.loading) item { Text(if (!state.ready || state.readError) text("Listado no disponible.", "List unavailable.") else text("No hay elementos.", "No items.")) }
items(rows, key = { it.optString("_id") }) { row ->
val id = row.optString("_id")
val name = row.optString("name").ifBlank { id }
Card(Modifier.fillMaxWidth()) {
Column(Modifier.padding(12.dp)) {
Text(name, style = MaterialTheme.typography.titleSmall)
Text(id, style = MaterialTheme.typography.bodySmall)
Text(row.optString(if (users) "email" else "desc"), style = MaterialTheme.typography.bodySmall)
Row {
val edit = state.canWrite && if (users) ProfilePermissions.manageUsers(state.site) else ProfilePermissions.editGroup(state.site, state.rights(row))
val delete = state.canWrite && if (users) ProfilePermissions.manageUsers(state.site) && id != state.id else ProfilePermissions.deleteGroup(state.site, state.rights(row))
TextButton(onClick = { form = ProfileForm(users, "edit", id, name, row.optString("email"), row.optString("desc")) }, enabled = edit) { Text(text("Editar", "Edit")) }
TextButton(onClick = { form = ProfileForm(users, "delete", id, name) }, enabled = delete) { Text(text("Eliminar", "Delete"), color = MaterialTheme.colorScheme.error) }
}
}
}
}
}
}
}
}
}
form?.let { selected -> key(selected) {
var name by remember { mutableStateOf(selected.name) }
var email by remember { mutableStateOf(selected.email) }
var description by remember { mutableStateOf(selected.description) }
var password by remember { mutableStateOf("") }
var confirmation by remember { mutableStateOf("") }
var error by remember { mutableStateOf("") }
val deleting = selected.operation == "delete"
val creating = selected.operation == "create"
val target = (if (selected.users) state.users else state.groups).firstOrNull { it.optString("_id") == selected.id }
val allowed = state.canWrite && when {
selected.users -> ProfilePermissions.manageUsers(state.site) && (creating || target != null) && (!deleting || selected.id != state.id)
creating -> ProfilePermissions.createGroup(state.site)
target == null -> false
deleting -> ProfilePermissions.deleteGroup(state.site, state.rights(target))
else -> ProfilePermissions.editGroup(state.site, state.rights(target))
}
val valid = if (deleting) confirmation == selected.id else if (selected.users && !creating) email.length <= 320 else name.isNotBlank() && name.length <= 256 && (!selected.users || (!name.contains('/') && password.isNotEmpty() && password.length <= 256 && email.length <= 320))
AlertDialog(onDismissRequest = { form = null }, title = { Text(when { deleting -> text("Confirmar eliminación", "Confirm deletion"); creating -> text("Crear", "Create"); else -> text("Editar", "Edit") }) },
text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
if (deleting) {
Text(selected.name); Text(selected.id)
Text(if (selected.users) text("Se eliminará esta cuenta. Esta acción no se puede deshacer desde la app.", "This account will be deleted. This cannot be undone from the app.") else text("Se eliminará este grupo y puede afectar al acceso a sus dispositivos. No se puede deshacer desde la app.", "This group will be deleted and device access may be affected. This cannot be undone from the app."))
OutlinedTextField(confirmation, { if (it.length <= 1024) confirmation = it }, label = { Text(text("Escribe el ID completo para confirmar", "Type the complete ID to confirm")) }, singleLine = true)
} else {
if (!selected.users || creating) OutlinedTextField(name, { if (it.length <= 256) name = it }, label = { Text(text("Nombre", "Name")) }, singleLine = true)
else { Text(selected.name); Text(selected.id, style = MaterialTheme.typography.bodySmall) }
if (selected.users) {
OutlinedTextField(email, { if (it.length <= 320) email = it }, label = { Text(text("Correo electrónico", "Email")) }, singleLine = true)
if (creating) OutlinedTextField(password, { if (it.length <= 256) password = it }, label = { Text(text("Contraseña", "Password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
} else {
if (creating) Text(text("Nuevo grupo de dispositivos con agente.", "New agent-based device group."), style = MaterialTheme.typography.bodySmall)
OutlinedTextField(description, { if (it.length <= 1024) description = it }, label = { Text(text("Descripción", "Description")) }, maxLines = 3)
}
}
if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
} },
confirmButton = { TextButton(enabled = allowed && valid, onClick = {
val action = if (selected.users) when { deleting -> "deleteuser"; creating -> "adduser"; else -> "edituser" } else when { deleting -> "deletemesh"; creating -> "createmesh"; else -> "editmesh" }
val packet = JSONObject().put("action", action)
if (!creating) packet.put(if (selected.users) "userid" else "meshid", selected.id)
if (!deleting) {
if (selected.users) {
packet.put("email", email)
if (creating) packet.put("username", name.trim()).put("pass", password)
} else {
packet.put("meshname", name.trim()).put("desc", description)
if (creating) packet.put("meshtype", 2)
}
}
if (session.submit(packet)) { password = ""; form = null } else error = text("No se ha enviado la solicitud. Comprueba conexión, permisos y destino.", "Request was not sent. Check connection, permissions and target.")
}) { Text(if (deleting) text("Eliminar", "Delete") else text("Enviar", "Send")) } },
dismissButton = { TextButton(onClick = { password = ""; form = null }) { Text(text("Cancelar", "Cancel")) } })
} }
webAction?.let { action ->
AlertDialog(onDismissRequest = { webAction = null }, title = { Text(action) }, text = { Text(text("Se abrirá el sitio web de tu servidor. Inicia sesión si es necesario y entra en Mi cuenta para completar esta acción. No se enviarán cookies, contraseñas ni tokens al navegador. Las notificaciones web no equivalen a notificaciones push de Android.", "Your server website will open. Sign in if needed and open My account to complete this action. No cookies, passwords or tokens will be passed to the browser. Web notifications are not Android push notifications.")) },
confirmButton = { TextButton(onClick = {
val url = source.serverUrl.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.query(null)?.fragment(null)?.build()
if (url == null || url.scheme != "https") localError = text("Este acceso requiere la URL HTTPS válida del servidor.", "This shortcut requires a valid HTTPS server URL.")
else if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url.toString()))) }.isFailure) localError = text("No se pudo abrir el navegador.", "Could not open a browser.")
webAction = null
}) { Text(text("Abrir sitio web", "Open website")) } },
dismissButton = { TextButton(onClick = { webAction = null }) { Text(text("Cancelar", "Cancel")) } })
}
}
}
