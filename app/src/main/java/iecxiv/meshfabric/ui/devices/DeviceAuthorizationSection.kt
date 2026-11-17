package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.ui.management.*
import kotlinx.coroutines.awaitCancellation

@Composable internal fun GeneralAuthorizationRows(rows:List<GeneralAuthorizationRow>,es:Boolean) {
    if(rows.isEmpty()) Text(if(es) "No se han recibido asignaciones en estos enlaces. No acredita ausencia de acceso." else "No assignments received in these links. This does not establish absence of access.")
    rows.take(100).forEach {row->
        Column(Modifier.fillMaxWidth().testTag("general_authorization_row"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(row.label,style=MaterialTheme.typography.bodyMedium)
            Text(if(row.inherited) {if(es) "Heredado del grupo de dispositivos" else "Inherited from device group"} else {if(es) "Directo al dispositivo" else "Direct device link"},style=MaterialTheme.typography.labelSmall)
            Text(row.rights?.let {deviceGroupRightsLabel(it,es)} ?: if(es) "Derechos no verificables en este enlace" else "Rights not verifiable in this link",style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }
    }
    if(rows.size>100) Text(if(es) "Se muestran los primeros 100 enlaces recibidos." else "Showing the first 100 received links.",style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun DeviceAuthorizationSection(nodeId:String,meshId:String) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    HorizontalDivider()
    Text(t("Autorizaciones de usuario","User authorizations"),style=MaterialTheme.typography.titleSmall,modifier=Modifier.testTag("general_authorizations_heading"))
    val source=LocalDeviceDetailsClient.current
    if(source==null || meshId.isBlank()) {Text(t("Pendiente de verificar el dispositivo y su grupo.","Device and group verification pending."));return}
    val actor=source.userInfo.collectAsState().value?.id.orEmpty()
    val connected by source.connectionState.collectAsState()
    val identity=Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    key(source,identity,actor,nodeId,meshId) {
        val session=remember {ProfileSession(source)}
        val state by session.state.collectAsState()
        var adding by remember {mutableStateOf(false)}
        var selectedUser by remember {mutableStateOf<String?>(null)}
        var query by remember {mutableStateOf("")}
        var submittedUser by remember {mutableStateOf<String?>(null)}
        DisposableEffect(session) {onDispose {session.close()}}
        LaunchedEffect(session,lifecycle,connected) {lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if(connected && !source.isUserLogout) {session.start();try {awaitCancellation()} finally {session.stop()}} else session.stop()
        }}
        val bound=connected && !source.isUserLogout && source.userInfo.value?.id==actor && actor.isNotBlank() && identity==Triple(source.serverUrl,source.authCookie,source.ignoreSsl) && state.id==actor && session.broadcastServerLabel().isNotBlank()
        GroupDetailsAutoLoad(session,nodeId,session.connectionGeneration(),state.ready,state.loading,state.busy,state.readError,state.groups.any {it.optString("_id")==meshId}) {
            if(ProfilePermissions.manageUsers(state.site)) session.loadDetails() else session.loadGroupDetails()
        }
        val scoped=generalAuthorizationScope(state,actor,nodeId,meshId,bound)
        val candidates=scoped?.let {generalAuthorizationCandidates(it,es)}.orEmpty()
        Text(t("Solo enlaces recibidos del dispositivo y su grupo. Los grupos de usuarios no se expanden a sus miembros y no se calcula aquí el acceso efectivo completo.","Received device and device-group links only. User groups are not expanded into members; complete effective access is not calculated here."),style=MaterialTheme.typography.bodySmall)
        if(state.loading || state.detailsBusy || !state.ready && connected) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(scoped==null) Text(t("Autorizaciones no disponibles o pendientes de una lectura válida. No se interpretan como una lista vacía.","Authorizations unavailable or awaiting a valid read. This is not interpreted as an empty list."))
        else GeneralAuthorizationRows(generalAuthorizationRows(scoped),es)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(enabled=candidates.isNotEmpty(),onClick={adding=true;selectedUser=null;query=""},modifier=Modifier.testTag("general_add_users")) {Text(t("Agregar usuarios","Add users"))}
            TextButton(enabled=state.ready && !state.busy && !state.loading && !state.detailsBusy && bound,onClick={
                session.refresh();if(ProfilePermissions.manageUsers(state.site)) session.loadDetails() else session.loadGroupDetails()
            }) {Text(t("Actualizar autorizaciones","Refresh authorizations"))}
        }
        if(candidates.isEmpty()) Text(t("Agregar requiere un directorio de usuarios administrable, permiso para asignar acceso y una cuenta existente identificada sin ambigüedad y sin enlace directo previo.","Adding requires a manageable user directory, permission to assign access and an unambiguously identified existing account without a prior direct link."),style=MaterialTheme.typography.bodySmall)
        if(submittedUser!=null) Text(when {
            state.notice=="observed"->t("El flujo de asignación observó los derechos solicitados en el dispositivo.","The grant flow observed the requested rights on the device.")
            state.notice=="rejected"->t("El servidor rechazó la asignación.","The server rejected the assignment.")
            else->t("Solicitud iniciada; resultado sin confirmar hasta observar los derechos. No hay reenvíos automáticos. Actualiza antes de repetir.","Request initiated; outcome unconfirmed until the rights are observed. No automatic resends. Refresh before repeating.")
        },style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("general_authorization_result"))
        if(adding && bound) {
            val uid=selectedUser
            if(uid!=null && scoped!=null) key(uid,nodeId) {
                UserDeviceGrantDialog(session,scoped,uid,onBack={selectedUser=null},onSaved={submittedUser=uid;selectedUser=null;adding=false})
            } else if(uid!=null) {
                iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={adding=false;selectedUser=null},title={Text(t("Asignación no disponible","Assignment unavailable"))},text={Text(t("Los datos o permisos cambiaron. Cierra y actualiza antes de revisar.","Data or permissions changed. Close and refresh before reviewing."))},confirmButton={TextButton(onClick={adding=false;selectedUser=null}) {Text(t("Cerrar","Close"))}})
            } else iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={adding=false},title={Text(t("Agregar usuario existente","Add existing user"))},
                text={Column(Modifier.fillMaxWidth().heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(t("No crea cuentas. En el siguiente paso se revisan los derechos; solo podrá seleccionarse el dispositivo abierto en General.","Does not create accounts. The next step reviews rights; only the device open in General can be selected."),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(query,{query=it.take(256)},singleLine=true,label={Text(t("Buscar usuario","Search users"))},modifier=Modifier.fillMaxWidth())
                    val visible=candidates.filter {it.optString("name").contains(query,true) || it.optString("_id").contains(query,true)}
                    visible.take(100).forEach {user->TextButton(onClick={selectedUser=user.optString("_id")},modifier=Modifier.testTag("general_user_candidate")) {Text(user.optString("name")+" ("+user.optString("_id")+")")}}
                    if(visible.isEmpty()) Text(t("Sin candidatos verificados para esta búsqueda.","No verified candidates for this search."))
                    if(visible.size>100) Text(t("Máximo 100 resultados; concreta la búsqueda.","Up to 100 results; narrow the search."))
                }},confirmButton={},dismissButton={TextButton(onClick={adding=false}) {Text(t("Cancelar","Cancel"))}})
        }
    }
}
