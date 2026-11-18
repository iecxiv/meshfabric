package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode

internal data class GeneralGroupTarget(val group:MeshGroup,val nodes:List<MeshNode>)
private fun generalGroupId(id:String,prefix:String):List<String>? {
    val parts=id.split('/')
    return parts.takeIf {it.size==3 && it[0]==prefix && it[2].isNotBlank() && id.length<=4096 && id.none {c->c.isISOControl()}}
}
internal fun generalGroupTarget(actor:String,id:String,groups:List<Pair<MeshGroup,List<MeshNode>>>,connected:Boolean,loggedOut:Boolean):GeneralGroupTarget? {
    if(!connected || loggedOut) return null
    val user=generalGroupId(actor,"user") ?: return null
    val mesh=generalGroupId(id,"mesh") ?: return null
    if(user[1]!=mesh[1]) return null
    val received=groups.singleOrNull {it.first.id==id} ?: return null
    val nodes=received.second.filter {node->
        val key=generalGroupId(node.id,"node")
        key!=null && key[1]==user[1] && node.meshid==id
    }.groupBy {it.id}.values.filter {it.size==1}.map {it.single()}
    return GeneralGroupTarget(received.first,nodes)
}
@Composable internal fun GeneralGroupLinkRow(target:GeneralGroupTarget?,es:Boolean,onOpen:()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("general_group_link")
        .clickable(enabled=target!=null,role=Role.Button,onClick=onOpen),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(if(es) "Grupo" else "Group",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(target?.group?.name?.ifBlank {if(es) "Grupo sin nombre" else "Unnamed group"} ?: if(es) "Grupo no disponible" else "Group unavailable",
                style=MaterialTheme.typography.bodyMedium,color=if(target!=null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun DeviceGeneralGroupLink(groupId:String) {
    val source=LocalDeviceDetailsClient.current
    val groups=source?.groupedDevices?.collectAsState()?.value.orEmpty()
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val configuration=LocalConfiguration.current
    val es=configuration.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,identity,actor,groupId) {
        var opened by remember {mutableStateOf(false)}
        val target=generalGroupTarget(actor,groupId,groups,connected,source?.isUserLogout!=false)
        val current:()->Boolean={source!=null && !source.isUserLogout && source.connectionState.value && identity==Triple(source.serverUrl,source.authCookie,source.ignoreSsl) && source.userInfo.value?.id==actor && generalGroupTarget(actor,groupId,source.groupedDevices.value,true,false)!=null}
        GeneralGroupLinkRow(target,es) {if(current()) opened=true}
        if(opened) Dialog(onDismissRequest={opened=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            iecxiv.meshfabric.ui.components.AppDialogSurface(
                shape=iecxiv.meshfabric.ui.components.AppDialogAppearance.shape,
                color=iecxiv.meshfabric.ui.components.AppDialogAppearance.containerColor(),
                tonalElevation=iecxiv.meshfabric.ui.components.AppDialogAppearance.tonalElevation,
                modifier=Modifier.padding(horizontal=16.dp,vertical=24.dp).widthIn(max=600.dp).fillMaxWidth().heightIn(max=(configuration.screenHeightDp*0.88f).dp).testTag("general_group_summary")
            ) {Column {
                Text(t("Resumen del grupo","Group summary"),Modifier.padding(16.dp),style=MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(target==null) Text(t("La ficha ya no está disponible con la identidad, conexión o inventario actuales. No se abre otro grupo por su nombre.","This entry is no longer available with current identity, connection or inventory. A different group is not opened by name.")) else {
                        Text(target.group.name.ifBlank {t("Grupo sin nombre","Unnamed group")},style=MaterialTheme.typography.titleSmall)
                        Text(target.group.desc.ifBlank {t("Sin descripción","No description")})
                        Text(t("Solo consulta. Datos del inventario visible recibido; no es la ficha administrativa de Perfil ni un listado completo de autorizaciones. Actualizar solicita una nueva lectura, sin confirmar por sí solo su finalización.","Read-only. Received visible inventory, not the administrative Profile entry or a complete authorization list. Refresh requests a new read and does not by itself confirm completion."),style=MaterialTheme.typography.bodySmall)
                        Text(t("Dispositivos visibles recibidos: ","Received visible devices: ")+target.nodes.size,style=MaterialTheme.typography.titleSmall)
                        if(target.nodes.isEmpty()) Text(t("No hay dispositivos inequívocos recibidos para este grupo. Esto no demuestra que el grupo esté vacío en el servidor.","No unambiguous devices received for this group. This does not prove the group is empty on the server."))
                        target.nodes.forEach {node->
                            HorizontalDivider()
                            Text(node.name.ifBlank {t("Dispositivo sin nombre","Unnamed device")})
                            Text(if(node.isOnline) t("En línea","Online") else t("Sin conexión","Offline"),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.End) {
                    TextButton(enabled=target!=null,onClick={if(current()) source?.requestServerData()}) {Text(t("Actualizar inventario","Refresh inventory"))}
                    TextButton(onClick={opened=false},modifier=Modifier.testTag("general_group_close")) {Text(t("Volver a General","Back to General"))}
                }
            }}
        }
    }
}
