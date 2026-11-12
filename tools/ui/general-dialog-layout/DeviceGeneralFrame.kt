package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode

@Composable internal fun DeviceGeneralFrame(
    node:MeshNode,
    onClose:()->Unit,
    body:@Composable ColumnScope.()->Unit,
    footer:@Composable RowScope.()->Unit
) {
    val configuration=LocalConfiguration.current
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        iecxiv.meshfabric.ui.components.AppDialogSurface(
            shape=iecxiv.meshfabric.ui.components.AppDialogAppearance.shape,
            color=iecxiv.meshfabric.ui.components.AppDialogAppearance.containerColor(),
            tonalElevation=iecxiv.meshfabric.ui.components.AppDialogAppearance.tonalElevation,
            modifier=Modifier.padding(horizontal=16.dp,vertical=24.dp)
                .widthIn(max=600.dp).fillMaxWidth().heightIn(max=(configuration.screenHeightDp*0.88f).dp)
                .testTag("device_general_surface")
        ) {
            Column {
                Column(Modifier.padding(start=20.dp,end=12.dp,top=12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Computer,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(node.name.ifBlank { stringResource(R.string.unknown_device) },style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            Text("General",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("device_general_subtitle"))
                        }
                        IconButton(onClick=onClose,modifier=Modifier.testTag("device_general_close")) {
                            Icon(Icons.Default.Close,stringResource(R.string.device_details_close))
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Surface(shape=RoundedCornerShape(6.dp),color=if(node.isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(stringResource(if(node.isOnline) R.string.device_details_online else R.string.device_details_offline),
                                Modifier.padding(horizontal=8.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall)
                        }
                        if(!node.osdesc.isNullOrBlank()) Text(node.osdesc.orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=8.dp),
                    verticalArrangement=Arrangement.spacedBy(8.dp),content=body)
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.End,
                    verticalAlignment=Alignment.CenterVertically,content=footer)
            }
        }
    }
}
