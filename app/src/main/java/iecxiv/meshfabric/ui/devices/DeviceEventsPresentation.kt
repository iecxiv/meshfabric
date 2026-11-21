package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.components.SubviewHeader
import iecxiv.meshfabric.ui.management.*
@Composable internal fun DeviceEventsContent(
    node:MeshNode,inline:Boolean,query:String,onQuery:(String)->Unit,searchOpen:Boolean,onSearchOpen:(Boolean)->Unit,
    state:ServerEventsSnapshot,enabled:Boolean,selection:ServerEventsQuery,onSelection:(ServerEventsQuery)->Unit,
    inventory:ServerEventInventory,onRefresh:()->Unit,onDismiss:()->Unit
) {
    val configuration=LocalConfiguration.current
    val es=configuration.locales[0].language=="es"
    fun text(a:String,b:String)=if(es) a else b
    DeviceDetailsFrame(inline=inline,onDismiss=onDismiss) {
        val surface=if(inline) Modifier.fillMaxSize() else Modifier.padding(horizontal=16.dp,vertical=24.dp).widthIn(max=600.dp).fillMaxWidth().heightIn(max=(configuration.screenHeightDp*0.88f).dp)
        DeviceDetailsContentSurface(inline=inline,modifier=surface.testTag("device_events_surface")) {
            Column(Modifier.fillMaxSize()) {
                ServerEventsSearchHeader(query,onQuery,searchOpen,onSearchOpen,header={actions->
                    Column(Modifier.padding(start=if(inline) 16.dp else 20.dp,end=if(inline) 16.dp else 12.dp,top=if(inline) 0.dp else 12.dp)) {
                        val title:@Composable ()->Unit={Column {
                            Text(node.name.ifBlank {stringResource(R.string.unknown_device)},style=MaterialTheme.typography.titleMedium)
                            Text(text("Eventos","Events"),style=MaterialTheme.typography.labelSmall,modifier=Modifier.testTag("device_events_subtitle"))
                        }}
                        if(inline) SubviewHeader(onBack=onDismiss,title=title,backTag="device_events_close",actions={actions()})
                        else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Computer,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                            Box(Modifier.weight(1f)) {title()};actions()
                            IconButton(onClick=onDismiss,modifier=Modifier.testTag("device_events_close")) {Icon(Icons.Default.Close,stringResource(R.string.device_details_close))}
                        }
                    }
                },tabs={
                    Row(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Surface(shape=RoundedCornerShape(6.dp),color=if(node.isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(stringResource(if(node.isOnline) R.string.device_details_online else R.string.device_details_offline),Modifier.padding(horizontal=8.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall)
                        }
                        if(!node.osdesc.isNullOrBlank()) Text(node.osdesc.orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                })
                ServerEventsPanel(state,query,onQuery,enabled,onRefresh,Modifier.weight(1f).fillMaxWidth(),selection=selection,onSelection=onSelection,inventory=inventory,
                    scopeLabel=text("Eventos de este dispositivo: ","Events for this device: ")+node.name,showDeviceLabel=false,flushBottom=true)
                HorizontalDivider(Modifier.testTag("device_events_footer_divider"),color=MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onRefresh,enabled=enabled && state.phase!=ServerEventsPhase.LOADING,modifier=Modifier.testTag("device_events_refresh")) {Icon(Icons.Default.Refresh,null,Modifier.size(18.dp).testTag("device_events_refresh_icon"));Spacer(Modifier.width(6.dp));Text(text("Actualizar","Refresh"))}
                    TextButton(onClick=onDismiss) {Text(if(inline) text("Volver","Back") else text("Cerrar","Close"))}
                }
            }
        }
    }
}
