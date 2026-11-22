package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.management.*
import kotlinx.coroutines.awaitCancellation

@Composable internal fun DeviceEventsDialog(node:MeshNode,onDismiss:()->Unit) {
    val inline=LocalDeviceEventsSubviewInline.current
    val source=LocalDeviceDetailsClient.current
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
        var showSearch by rememberSaveable {mutableStateOf(false)}
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
        DeviceEventsContent(node=node,inline=inline,query=query,onQuery={query=it},searchOpen=showSearch,onSearchOpen={showSearch=it},
            state=visible,enabled=enabled,selection=selection,onSelection={selectedType=it.type.name;selectedLimit=it.limit ?: 0},
            inventory=inventory,onRefresh={reader.load(selection)},onDismiss=onDismiss)
    }
}
