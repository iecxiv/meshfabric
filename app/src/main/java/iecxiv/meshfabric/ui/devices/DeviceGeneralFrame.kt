package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

internal val LocalDeviceGeneralClose=staticCompositionLocalOf<(() -> Unit)?> {null}

@Composable internal fun DeviceGeneralFrame(
    node:MeshNode,
    onClose:()->Unit,
    body:@Composable ColumnScope.()->Unit,
    footer:@Composable RowScope.()->Unit,
    subtitle:String="General",
    tagPrefix:String="device_general",
    scrollBody:Boolean=true,
    onMinimize:(()->Unit)?=null,
    inline:Boolean=false
) {
    val configuration=LocalConfiguration.current
    DeviceDetailsFrame(inline=inline,onDismiss=onClose) {
        DeviceDetailsContentSurface(inline=inline,modifier=if(inline) Modifier.fillMaxSize().testTag("${tagPrefix}_surface") else Modifier.padding(horizontal=16.dp,vertical=24.dp).widthIn(max=600.dp).fillMaxWidth().heightIn(max=(configuration.screenHeightDp*0.88f).dp).testTag("${tagPrefix}_surface")) {
            Column {
                Column(Modifier.padding(start=if(inline) 16.dp else 20.dp,end=if(inline) 16.dp else 12.dp,top=if(inline) 0.dp else 12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(inline) iecxiv.meshfabric.ui.components.SubviewHeader(onBack=onClose,title={Column {
                        Text(node.name.ifBlank {stringResource(R.string.unknown_device)})
                        Text(subtitle,style=MaterialTheme.typography.labelSmall,modifier=Modifier.testTag("${tagPrefix}_subtitle"))
                    }},backTag="${tagPrefix}_close") else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Computer,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(node.name.ifBlank { stringResource(R.string.unknown_device) },style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            Text(subtitle,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("${tagPrefix}_subtitle"))
                        }
                        if(onMinimize==null) Spacer(Modifier.size(48.dp)) else IconButton(onClick=onMinimize,modifier=Modifier.testTag("${tagPrefix}_minimize")) {
                            Icon(Icons.Default.Minimize,if(configuration.locales[0].language=="es") "Minimizar chat" else "Minimize chat")
                        }
                        IconButton(onClick=onClose,modifier=Modifier.testTag("${tagPrefix}_close")) {
                            Icon(Icons.Default.Close,stringResource(R.string.device_details_close))
                        }
                    }
                    DeviceStatusDecoration(node,Modifier.fillMaxWidth())
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                val bodyModifier=if(scrollBody) {
                    Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=8.dp)
                } else Modifier.weight(1f).fillMaxWidth()
                CompositionLocalProvider(LocalDeviceGeneralClose provides if(tagPrefix=="device_general") onClose else LocalDeviceGeneralClose.current) {
                    Column(bodyModifier,verticalArrangement=Arrangement.spacedBy(8.dp),content=body)
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.End,
                    verticalAlignment=Alignment.CenterVertically,content=footer)
            }
        }
    }
}
