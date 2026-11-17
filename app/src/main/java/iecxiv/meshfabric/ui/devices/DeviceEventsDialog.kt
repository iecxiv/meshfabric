package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.management.*
import kotlinx.coroutines.awaitCancellation

@Composable internal fun DeviceEventsDialog(node:MeshNode,onDismiss:()->Unit) {
    val source=LocalDeviceDetailsClient.current
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun text(a:String,b:String)=if(es) a else b
    val actor=source?.userInfo?.collectAsState()?.value
    val connected=source?.connectionState?.collectAsState()?.value==true
    val groups=source?.groupedDevices?.collectAsState()?.value.orEmpty()
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    key(source,identity,node.id,actor?.id,actor?.siteadmin,connected) {
        val enabled=source!=null && connected && !source.isUserLogout && !actor?.id.isNullOrBlank() && source.authCookie.isNotBlank()
        val reader=remember {
            LogEventsReader(current={
                val user=source?.userInfo?.value
                if(source==null || source.isUserLogout || !source.connectionState.value || user==null || user.id.isBlank() || source.authCookie.isBlank()) null
                else LogEventsBinding(source.serverUrl,user.id,source.authCookie,source.ignoreSsl,user.siteadmin,source.extractRawToken(source.authCookie))
            })
        }
        var selectedType by rememberSaveable {mutableStateOf(ServerEventType.ALL.name)}
        var selectedLimit by rememberSaveable {mutableIntStateOf(60)}
        var query by rememberSaveable {mutableStateOf("")}
        val selection=ServerEventsQuery(ServerEventType.entries.firstOrNull {it.name==selectedType} ?: ServerEventType.ALL,selectedLimit.takeUnless {it==0},nodeId=node.id)
        val state by reader.state.collectAsState()
        val lifecycle=LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(reader,lifecycle,selection) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {reader.load(selection);awaitCancellation()} finally {reader.pause(selection)}
            }
        }
        DisposableEffect(reader) {onDispose {reader.close()}}
        val inventory=remember(groups,enabled) {if(enabled) serverEventInventory(groups) else ServerEventInventory()}
        val visible=if(state.selection==selection && enabled) state else ServerEventsSnapshot(if(enabled) ServerEventsPhase.LOADING else ServerEventsPhase.INVALIDATED,selection=selection)
        DeviceGeneralFrame(node,onDismiss,
            body={
                OutlinedTextField(query,{query=it},singleLine=true,label={Text(text("Buscar eventos","Search events"))},modifier=Modifier.fillMaxWidth().testTag("device_events_search"))
                ServerEventsPanel(visible,query,{query=it},enabled,{reader.load(selection)},Modifier.weight(1f).fillMaxWidth(),
                    selection=selection,onSelection={selectedType=it.type.name;selectedLimit=it.limit ?: 0},inventory=inventory,
                    scopeLabel=text("Eventos de este dispositivo: ","Events for this device: ")+node.name)
            },
            footer={
                TextButton(onClick={reader.load(selection)},enabled=enabled && state.phase!=ServerEventsPhase.LOADING,modifier=Modifier.testTag("device_events_refresh")) {Text(text("Actualizar","Refresh"))}
                TextButton(onClick=onDismiss) {Text(text("Cerrar","Close"))}
            },
            subtitle=text("Eventos","Events"),tagPrefix="device_events",scrollBody=false)
    }
}
