package iecxiv.meshfabric.ui.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.*
import iecxiv.meshfabric.data.remote.ConnectionStatus
import iecxiv.meshfabric.ui.components.MeshFabricAppBar
import iecxiv.meshfabric.ui.management.LocalDeviceActionMenu
import iecxiv.meshfabric.ui.theme.OfflineGray
import iecxiv.meshfabric.ui.theme.OnlineGreen
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DeviceListScreen(viewModel:DeviceListViewModel,onLogout:()->Unit,onOpenSettings:()->Unit,
    onOpenDesktop:(String,String)->Unit,onOpenTerminal:(String,String)->Unit,onOpenFiles:(String,String)->Unit) {
    val allGroups by viewModel.groupedDevices.collectAsState()
    val fallbackOnlineFilter = remember { MutableStateFlow(false) }
    val onlyOnline by (viewModel.sessionManager?.onlyOnlineDevicesFlow ?: fallbackOnlineFilter).collectAsState()
    val groups = remember(allGroups, onlyOnline) {
        if (!onlyOnline) allGroups else allGroups.mapNotNull { (group, nodes) ->
            nodes.filter { it.isOnline }.takeIf { it.isNotEmpty() }?.let { group to it }
        }
    }
    val connection by viewModel.connectionStatus.collectAsState()
    val search by viewModel.searchQuery.collectAsState()
    val collapsed by viewModel.collapsedGroups.collectAsState()
    val desktop by viewModel.desktopSessionsStatus.collectAsState()
    val terminal by viewModel.terminalSessionsStatus.collectAsState()
    val files by viewModel.fileSessionsStatus.collectAsState()
    val appName by (viewModel.sessionManager?.appNameFlow ?: remember { MutableStateFlow("MeshFabric") }).collectAsState()
    val hideSettings by (viewModel.sessionManager?.hideSettingsIconFlow ?: remember { MutableStateFlow(false) }).collectAsState()
    val searchFocus=remember { FocusRequester() }
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    var showSearch by remember { mutableStateOf(false) }
    var closeSessions by remember { mutableStateOf(false) }
    val expanded=groups.isEmpty() || groups.any { it.first.id !in collapsed }
    val owner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner,viewModel) {
        viewModel.refreshActiveSessions()
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshActiveSessions()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if(closeSessions) AlertDialog(onDismissRequest={ closeSessions=false },title={ Text(stringResource(R.string.confirm_disconnect_all_title),fontWeight=FontWeight.Bold,fontSize=18.sp) },
        text={ Text(stringResource(R.string.confirm_disconnect_all_msg)) },
        confirmButton={ TextButton(onClick={ closeSessions=false; viewModel.closeAllActiveSessions() }) { Text(stringResource(R.string.disconnect_all),fontWeight=FontWeight.Bold) } },
        dismissButton={ TextButton(onClick={ closeSessions=false }) { Text(stringResource(R.string.cancel)) } })
    Scaffold(topBar={ Column {
        MeshFabricAppBar(viewModel,onOpenSettings,actions={ Row(horizontalArrangement=Arrangement.spacedBy((-6).dp)) {
            if(!hideSettings) ToolIcon(Icons.Default.Settings,stringResource(R.string.settings),onOpenSettings)
            ToolIcon(Icons.Default.Refresh,stringResource(R.string.refresh)) { viewModel.refresh() }
            IconToggleButton(checked=onlyOnline,onCheckedChange={ enabled ->
                val manager = viewModel.sessionManager
                if (manager != null) manager.saveOnlyOnlineDevices(enabled) else fallbackOnlineFilter.value = enabled
            },modifier=Modifier.size(38.dp)) {
                Box(Modifier.size(24.dp),contentAlignment=Alignment.Center) {
                    Icon(Icons.Default.FilterAlt,
                        stringResource(if(onlyOnline) R.string.device_filter_show_all else R.string.device_filter_show_online),
                        Modifier.size(20.dp),tint=if(onlyOnline) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                }
            }
            ToolIcon(if(expanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                stringResource(if(expanded) R.string.collapse_all_groups else R.string.expand_all_groups)) { viewModel.toggleAllGroups() }
            ToolIcon(if(showSearch) Icons.Default.Close else Icons.Default.Search,stringResource(R.string.search)) {
                if(showSearch) {
                    viewModel.searchQuery.value=""
                    focusManager.clearFocus(force=true)
                    keyboard?.hide()
                }
                showSearch=!showSearch
            }
            ToolIcon(Icons.Default.PowerSettingsNew,stringResource(R.string.close_active_connections)) { closeSessions=true }
        } })
        AnimatedVisibility(showSearch) {
            LaunchedEffect(showSearch) {
                if(showSearch) {
                    withFrameNanos { }
                    searchFocus.requestFocus()
                    keyboard?.show()
                }
            }
            OutlinedTextField(search,{ viewModel.searchQuery.value=it },modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).focusRequester(searchFocus),
                placeholder={ Text(stringResource(R.string.search_devices_placeholder)) },singleLine=true,
                trailingIcon={ if(search.isNotEmpty()) IconButton(onClick={ viewModel.searchQuery.value="" }) {
                    Icon(Icons.Default.Close,stringResource(R.string.clear_search))
                } })
        }
    } }) { padding ->
        if(groups.isEmpty()) Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                if(connection==ConnectionStatus.CONNECTING) {
                    CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.connecting_server))
                } else {
                    Icon(Icons.Default.Computer,null,Modifier.size(64.dp),tint=MaterialTheme.colorScheme.outline)
                    Text(stringResource(R.string.no_devices_found))
                    Button(onClick={ viewModel.refresh() }) { Text(stringResource(R.string.reload_devices)) }
                }
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=12.dp,vertical=8.dp)) {
            groups.forEach { (group,nodes)->
                item(key="group_${group.id}") { GroupHeaderCard(group,nodes.count { it.isOnline },nodes.count { !it.isOnline },
                    search.isNotBlank() || group.id !in collapsed) { viewModel.toggleGroupExpanded(group.id) } }
                if(search.isNotBlank() || group.id !in collapsed) items(nodes,key={ "node_${it.id}" }) { node ->
                    val clean=node.id.removePrefix("node//").removePrefix("node/").trim()
                    DeviceCard(node,desktop[clean] ?: SessionStatus.NONE,terminal[clean] ?: SessionStatus.NONE,files[clean] ?: SessionStatus.NONE,
                        { onOpenDesktop(node.id,node.name) },{ onOpenTerminal(node.id,node.name) },{ onOpenFiles(node.id,node.name) })
                }
            }
        }
    }
}
@Composable private fun ToolIcon(icon:ImageVector,label:String,click:()->Unit) {
    IconButton(onClick=click,modifier=Modifier.size(38.dp)) { Icon(icon,label,Modifier.size(20.dp)) }
}
@Composable fun GroupHeaderCard(group:MeshGroup,onlineCount:Int,offlineCount:Int,isExpanded:Boolean,onToggleExpand:()->Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical=6.dp).clip(CardDefaults.shape).clickable(onClick=onToggleExpand),
        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(group.name.ifEmpty { stringResource(R.string.unnamed_group) },style=MaterialTheme.typography.titleMedium,
                    fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)
                Row {
                    Text(stringResource(R.string.online_count,onlineCount),style=MaterialTheme.typography.labelMedium,color=OnlineGreen)
                    if(offlineCount>0) Text(if(offlineCount==1) stringResource(R.string.offline_count_one) else stringResource(R.string.offline_count_many,offlineCount),
                        style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(if(isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,null)
        }
    }
}
@Composable fun DeviceCard(node:MeshNode,desktopStatus:SessionStatus=SessionStatus.NONE,terminalStatus:SessionStatus=SessionStatus.NONE,
    fileStatus:SessionStatus=SessionStatus.NONE,onOpenDesktop:()->Unit,onOpenTerminal:()->Unit,onOpenFiles:()->Unit) {
    val os=node.osdesc.orEmpty()
    Card(Modifier.fillMaxWidth().padding(vertical=4.dp),
        colors=CardDefaults.cardColors(containerColor=if(node.isOnline) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Surface(Modifier.size(40.dp),shape=CircleShape,color=(if(node.isOnline) OnlineGreen else OfflineGray).copy(alpha=0.15f)) {
                    Box(contentAlignment=Alignment.Center) {
                        Icon(when {
                            os.contains("Windows",true)->Icons.Default.DesktopWindows
                            os.contains("Android",true)->Icons.Default.Android
                            else->Icons.Default.Computer
                        },null,Modifier.size(24.dp),tint=if(node.isOnline) OnlineGreen else OfflineGray)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(node.name.ifEmpty { stringResource(R.string.unknown_device) },modifier=Modifier.weight(1f,fill=false),
                            style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Surface(shape=RoundedCornerShape(4.dp),color=if(node.isOnline) OnlineGreen else OfflineGray) {
                            Text(if(node.isOnline) "ONLINE" else "OFFLINE",Modifier.padding(horizontal=6.dp,vertical=2.dp),color=Color.White,fontSize=9.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                    if(os.isNotEmpty()) Text(os,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LocalDeviceActionMenu.current(node)
            }
            if(node.isOnline || listOf(desktopStatus,terminalStatus,fileStatus).any { it!=SessionStatus.NONE }) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(node.supportsDesktop || desktopStatus!=SessionStatus.NONE) SessionActionButton(stringResource(R.string.desktop),Icons.Default.DesktopWindows,desktopStatus,onOpenDesktop,Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    if(node.supportsTerminal || terminalStatus!=SessionStatus.NONE) SessionActionButton(stringResource(R.string.terminal),Icons.Default.Terminal,terminalStatus,onOpenTerminal,Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                    if(node.supportsFiles || fileStatus!=SessionStatus.NONE) SessionActionButton(stringResource(R.string.files),Icons.Default.Folder,fileStatus,onOpenFiles,Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
@Composable fun SessionActionButton(title:String,icon:ImageVector,status:SessionStatus,onOpen:()->Unit,modifier:Modifier=Modifier) {
    val isConnected=status==SessionStatus.CONNECTED
    val isDisconnectedWithBuffer=status==SessionStatus.DISCONNECTED_WITH_BUFFER
    val isActiveOrBuffered=isConnected || isDisconnectedWithBuffer
    val borderColor=when { isDisconnectedWithBuffer->MaterialTheme.colorScheme.error; isConnected->MaterialTheme.colorScheme.primary; else->MaterialTheme.colorScheme.outline }
    val containerColor=when { isDisconnectedWithBuffer->MaterialTheme.colorScheme.errorContainer.copy(alpha=0.5f); isConnected->MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.5f); else->Color.Unspecified }
    val contentColor=when { isDisconnectedWithBuffer->MaterialTheme.colorScheme.error; isConnected->MaterialTheme.colorScheme.primary; else->MaterialTheme.colorScheme.onSurface }
    OutlinedButton(onClick=onOpen,modifier=modifier,
        border=androidx.compose.foundation.BorderStroke(width=if(isActiveOrBuffered) 2.dp else 1.dp,color=borderColor),
        colors=ButtonDefaults.outlinedButtonColors(containerColor=containerColor),contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)) {
        Icon(imageVector=icon,contentDescription=null,modifier=Modifier.size(16.dp),tint=contentColor)
        Spacer(Modifier.width(4.dp))
        Text(text=title,fontSize=11.sp,fontWeight=if(isActiveOrBuffered) FontWeight.Bold else FontWeight.Normal,color=contentColor)
    }
}
