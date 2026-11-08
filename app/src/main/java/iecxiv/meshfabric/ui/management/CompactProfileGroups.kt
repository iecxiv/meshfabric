package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

internal fun deviceGroupMatches(query:String,name:String,description:String)=query.trim().let { it.isEmpty() || name.contains(it,true) || description.contains(it,true) }
internal fun profileReturnUserId(id:String?,available:Set<String>,ready:Boolean)=id?.takeIf { ready && it.isNotBlank() && it in available }
internal fun deviceGroupRightsLabel(value:Long,es:Boolean):String {
    val mask=ProfilePermissions.normalized(value)
    if(mask==0xffffffffL) return if(es) "Administrador del grupo" else "Group administrator"
    if(mask==0L) return if(es) "Sin permisos directos" else "No direct rights"
    val labels=if(es) listOf("Editar grupo","Administrar accesos","Administrar dispositivos","Control remoto","Consola","Archivos del servidor","Despertar","Notas","Solo visualizar","Sin terminal","Sin archivos","Sin AMT","Entrada limitada","Eventos limitados","Chat","Desinstalar agente","Sin escritorio","Comandos","Sin reiniciar/apagar","Compartir acceso","Detalles","Relay","Sin Registro","Sin software") else listOf("Edit group","Manage access","Manage devices","Remote control","Console","Server files","Wake","Notes","View only","No terminal","No files","No AMT","Limited input","Limited events","Chat","Uninstall agent","No desktop","Commands","No reset/power off","Share access","Details","Relay","No Registry","No software")
    val selected=labels.filterIndexed { i,_->mask and (1L shl i)!=0L }.toMutableList()
    if(mask and 0xff000000L!=0L) selected.add(if(es) "Permisos adicionales" else "Additional rights")
    return selected.joinToString(", ")
}
@Composable internal fun ProfileDisclosure(title:String,summary:String="",content:@Composable ()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics { stateDescription=if(expanded) { if(es) "Expandido" else "Expanded" } else { if(es) "Plegado" else "Collapsed" } },contentPadding=PaddingValues(horizontal=0.dp,vertical=4.dp)) {
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.Start) {
                Text(title,style=MaterialTheme.typography.titleSmall)
                if(summary.isNotBlank()) Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,contentDescription=null)
        }
        if(expanded) Column(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { content() }
    }
}
@Composable internal fun DeviceGroupDetailsDialog(session:ProfileSession,s:ProfileState,id:String,returningToUser:Boolean,onDismiss:()->Unit,onEdit:()->Unit,onDelete:()->Unit,onOpenUser:((String)->Unit)?=null,onOpenUserGroup:((String)->Unit)?=null,initialTab:Int=0,onTabChanged:(Int)->Unit={},onOpenDevice:((String)->Unit)?=null,canOpenDevice:(String)->Boolean={false}) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    var tab by remember(id,initialTab) { mutableIntStateOf(initialTab.coerceIn(0,2)) }
    var query by remember(id) { mutableStateOf("") }
    val group=s.groups.firstOrNull { it.optString("_id")==id }
    LaunchedEffect(session,id,s.ready,s.busy) { if(group!=null && s.ready && !s.busy) session.loadGroupDetails() }
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false),title={Text(group?.optString("name")?.ifBlank { t("Grupo sin nombre","Unnamed group") } ?: t("Grupo no disponible","Group unavailable"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(s.busy || s.detailsBusy || s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(group==null) Text(t("El grupo ya no está disponible en los datos recibidos.","The group is no longer available in the received data.")) else {
                ManagementSectionTabs(listOf(t("Resumen","Summary"),t("Dispositivos","Devices"),t("Accesos","Access")),tab) {tab=it;onTabChanged(it)}
                when(tab) {
                    0->{
                        Text(group.optString("desc").ifBlank { t("Sin descripción","No description") },style=MaterialTheme.typography.bodyMedium)
                        ProfileDisclosure(t("Identificador del grupo","Group identifier")) { SelectionContainer { Text(id,style=MaterialTheme.typography.bodySmall) } }
                        TextButton(enabled=s.canWrite && ProfilePermissions.editGroup(s.site,s.rights(group)),onClick=onEdit) {Text(t("Editar nombre y descripción","Edit name and description"))}
                        TextButton(enabled=s.canWrite && ProfilePermissions.deleteGroup(s.site,s.rights(group)),onClick=onDelete,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) {Text(t("Eliminar grupo","Delete group"))}
                    }
                    1->{
                        OutlinedTextField(query,{query=it.take(256)},label={Text(t("Buscar dispositivo","Search device"))},singleLine=true,modifier=Modifier.fillMaxWidth())
                        val nodes=s.nodes?.filter { it.optString("meshid")==id && deviceGroupMatches(query,it.optString("name"),it.optString("desc")) }
                        if(nodes==null) Text(t("Listado de dispositivos no disponible. Usa Actualizar.","Device list unavailable. Use Refresh.")) else {
                            Text("${nodes.size} "+t("dispositivos visibles","visible devices"),style=MaterialTheme.typography.bodySmall)
                            for(node in nodes.take(100)) ListItem(headlineContent={Text(node.optString("name").ifBlank { t("Dispositivo sin nombre","Unnamed device") },maxLines=2,overflow=TextOverflow.Ellipsis)},leadingContent={Icon(Icons.Default.Computer,null)},trailingContent={if(onOpenDevice!=null && canOpenDevice(node.optString("_id"))) TextButton(onClick={onOpenDevice(node.optString("_id"))}) {Text(t("Abrir ficha","Open details"))}})
                            if(nodes.size>100) Text(t("Se muestran 100 resultados; concreta la búsqueda.","Showing 100 results; refine the search."),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    2->{
                        Text(t("Asignaciones directas recibidas. Los accesos heredados pueden añadir otros permisos; esta entrega no modifica miembros.","Received direct assignments. Inherited access may add other rights; this release does not modify members."),style=MaterialTheme.typography.bodySmall)
                        val links=group.optJSONObject("links")
                        val members=links?.keys()?.asSequence()?.filter { it.startsWith("user/") || it.startsWith("ugrp/") }?.sorted()?.toList()
                        if(members==null) Text(t("Información de accesos no disponible.","Access information unavailable.")) else if(members.isEmpty()) Text(t("No se han recibido asignaciones directas.","No direct assignments received.")) else {
                            for(member in members.take(100)) {
                                val name=s.users.firstOrNull { it.optString("_id")==member }?.optString("name") ?: s.userGroups?.firstOrNull { it.optString("_id")==member }?.optString("name") ?: member
                                val rights=runCatching { val raw=requireNotNull(links).getJSONObject(member).get("rights"); require(raw is Number); userAdvancedMask(raw) }.getOrNull()
                                ListItem(headlineContent={Text(name,maxLines=2,overflow=TextOverflow.Ellipsis)},supportingContent={Text(rights?.let { deviceGroupRightsLabel(it,es) } ?: t("Permisos no informados","Rights not reported"))},leadingContent={Icon(Icons.Default.Group,null)})
if(onOpenUser!=null && profileAccessUserTarget(s,member)!=null) TextButton(enabled=s.canWrite,onClick={onOpenUser(member)}) {Text(t("Abrir usuario","Open user"))}
if(onOpenUserGroup!=null && profileReceivedUserGroup(s,member)!=null) TextButton(enabled=s.ready && !s.busy && !s.loading && !s.detailsBusy,onClick={onOpenUserGroup(member)}) {Text(t("Abrir grupo de usuarios","Open user group"))}
                            }
                            if(members.size>100) Text(t("Se muestran los primeros 100 accesos recibidos.","Showing the first 100 received assignments."),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if(s.readError) Text(t("Datos incompletos: no se habilitan modificaciones.","Incomplete data: changes remain disabled."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(enabled=s.ready && !s.busy && !s.detailsBusy,onClick={session.loadGroupDetails()}){Text(t("Actualizar","Refresh"))}},dismissButton={TextButton(onClick=onDismiss){Text(if(returningToUser) t("Volver al usuario","Back to user") else t("Cerrar","Close"))}})
}
