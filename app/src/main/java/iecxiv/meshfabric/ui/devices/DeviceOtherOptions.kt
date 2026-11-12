package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.data.model.MeshNode

internal data class DeviceOptionActions(
    val events: ((MeshNode)->Unit)?=null,
    val console: ((MeshNode)->Unit)?=null,
    val software: ((MeshNode)->Unit)?=null,
    val softwareAllowed: (MeshNode)->Boolean={ false }
)
internal val LocalDeviceOptionActions=staticCompositionLocalOf { DeviceOptionActions() }

@Composable private fun spanish()=LocalConfiguration.current.locales[0].language=="es"

@Composable internal fun DeviceOptionsToggle(expanded:Boolean,onToggle:()->Unit) {
    val es=spanish()
    IconButton(onClick=onToggle,modifier=Modifier.size(38.dp).testTag("device_options_toggle")) {
        Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            if(es) { if(expanded) "Ocultar otras opciones" else "Mostrar otras opciones" }
            else { if(expanded) "Hide other options" else "Show other options" },
            tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun DeviceOptionsRow(node:MeshNode) {
    val es=spanish()
    val actions=LocalDeviceOptionActions.current
    var details by rememberSaveable(node.id) { mutableStateOf(false) }
    var general by rememberSaveable(node.id) { mutableStateOf(false) }
    val software=actions.software!=null && actions.softwareAllowed(node)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top=8.dp)) {
        val count=if(software) 5 else 4
        val width=((maxWidth-((count-1)*4).dp)/count).coerceAtLeast(76.dp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("device_options_row"),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            DeviceOptionButton(if(es) "General" else "General",Icons.Default.Dashboard,"general",width,true) { general=true }
            DeviceOptionButton(if(es) "Eventos" else "Events",Icons.Default.Event,"events",width,actions.events!=null) { actions.events?.invoke(node) }
            DeviceOptionButton(if(es) "Detalles" else "Details",Icons.Default.Info,"details",width,true) { details=true }
            DeviceOptionButton(if(es) "Consola" else "Console",Icons.Default.Code,"console",width,actions.console!=null) { actions.console?.invoke(node) }
            if(software) DeviceOptionButton("Software",Icons.Default.Apps,"software",width,true) { actions.software?.invoke(node) }
        }
    }
    if(details) DeviceDetailsDialog(node) { details=false }
    if(general) iecxiv.meshfabric.ui.components.AppAlertDialog(
        onDismissRequest={ general=false },
        title={ Text(node.name, fontSize=18.sp,fontWeight=FontWeight.Bold) },
        text={ Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(if(es) "General" else "General",style=MaterialTheme.typography.titleSmall)
            Text((if(es) "Nombre: " else "Name: ")+node.name)
            Text((if(es) "Sistema: " else "OS: ")+node.osdesc.orEmpty().ifBlank { if(es) "Sin datos" else "No data" })
            Text(if(node.isOnline) { if(es) "Conectado" else "Connected" } else { if(es) "Desconectado" else "Disconnected" })
            Text(if(es) "Vista inicial de consulta. Energía, etiquetas y edición aún no disponibles." else "Initial read-only view. Power history, tags and editing are not available yet.",style=MaterialTheme.typography.bodySmall)
        } },
        confirmButton={ TextButton(onClick={ general=false }) { Text(if(es) "Cerrar" else "Close") } })
}

@Composable private fun DeviceOptionButton(label:String,icon:ImageVector,key:String,width:androidx.compose.ui.unit.Dp,enabled:Boolean,onClick:()->Unit) {
    val contentColor=if(iecxiv.meshfabric.ui.theme.LocalAppDarkTheme.current) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.width(width).testTag("device_option_$key"),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=contentColor,disabledContentColor=contentColor.copy(alpha=0.38f)),
        contentPadding=PaddingValues(horizontal=2.dp,vertical=4.dp)) {
        Icon(icon,null,Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text(label,fontSize=10.sp,maxLines=1,softWrap=false)
    }
}
