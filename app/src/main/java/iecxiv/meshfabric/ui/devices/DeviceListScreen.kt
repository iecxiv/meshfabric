package iecxiv.meshfabric.ui.devices

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.components.RemoteHeaderActions
import iecxiv.meshfabric.ui.components.RemoteHeaderIcon
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
    val fallbackNetworkVisibility = remember { MutableStateFlow(false) }
    val hideDeviceNetworkInfo by (viewModel.sessionManager?.hideDeviceNetworkInfoFlow ?: fallbackNetworkVisibility).collectAsState()
    val onlyOnline by (viewModel.sessionManager?.onlyOnlineDevicesFlow ?: fallbackOnlineFilter).collectAsState()
    val connection by viewModel.connectionStatus.collectAsState()
    val networkRefresh by viewModel.networkRefresh.collectAsState()
    val networkConnected by viewModel.isConnected.collectAsState()
    val networkLifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val search by viewModel.searchQuery.collectAsState()
    val collapsed by viewModel.collapsedGroups.collectAsState()
    val user by viewModel.userInfo.collectAsState()
    val server = viewModel.meshClient.serverUrl
    val account = user?.id.orEmpty()
    val scope = "${server.length}:$server${account.length}:$account"
    val preferences = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("device_list_browse", android.content.Context.MODE_PRIVATE)
    val preferenceKey = remember(scope) {
        java.security.MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    var sortName by rememberSaveable(scope) { mutableStateOf(preferences.getString("$preferenceKey.sort", "GROUP") ?: "GROUP") }
    val sort = DeviceListSort.entries.firstOrNull { it.name == sortName && it in deviceListAvailableSorts } ?: DeviceListSort.GROUP
    var reverse by rememberSaveable(scope) { mutableStateOf(preferences.getBoolean("$preferenceKey.reverse", false)) }
    val es=androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language=="es"
    var filterName by rememberSaveable(scope) { mutableStateOf(preferences.getString("$preferenceKey.filter", "ALL") ?: "ALL") }
    val filter=DeviceListFilter.entries.firstOrNull {it.name==filterName} ?: DeviceListFilter.ALL
    var showOsName by rememberSaveable(scope) {mutableStateOf(preferences.getBoolean("$preferenceKey.osName",true))}
    var starredIds by rememberSaveable(scope) {mutableStateOf(preferences.getStringSet("$preferenceKey.stars",emptySet()).orEmpty().toList())}
    LaunchedEffect(scope,sort,reverse,filter,showOsName,starredIds) {
        if(server.isNotBlank() && account.isNotBlank()) preferences.edit().putString("$preferenceKey.sort",sort.name)
            .putBoolean("$preferenceKey.reverse",reverse).putString("$preferenceKey.filter",filter.name)
            .putBoolean("$preferenceKey.osName",showOsName).putStringSet("$preferenceKey.stars",starredIds.toSet()).apply()
    }
    val desktop by viewModel.desktopSessionsStatus.collectAsState()
    val terminal by viewModel.terminalSessionsStatus.collectAsState()
    val files by viewModel.fileSessionsStatus.collectAsState()
    val localSessionIds=(desktop.filterValues {it==SessionStatus.CONNECTED}.keys+terminal.filterValues {it==SessionStatus.CONNECTED}.keys+files.filterValues {it==SessionStatus.CONNECTED}.keys).toSet()
    val filteredGroups=remember(allGroups,onlyOnline,filter,search,showOsName,starredIds,localSessionIds) {
        deviceListFiltered(allGroups,filter,!onlyOnline,search,showOsName,starredIds.toSet(),localSessionIds)
    }
    val groupBy=deviceListUsesSections(sort)
    val groups=remember(filteredGroups,sort,reverse,showOsName,es) {deviceListSections(filteredGroups,sort,reverse,showOsName,es)}
    val visibleIds=remember(filteredGroups) {filteredGroups.flatMap {it.second}.map {it.id}.toSet()}
    var selectionScope by rememberSaveable { mutableStateOf(scope) }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selection = DeviceListSelection(selectionScope, selectedIds.toSet()).reconcile(scope, visibleIds)
    val selectionActive = selectionMode && selectionScope == scope
    fun updateSelection(value: DeviceListSelection) {
        selectionScope = value.scope
        selectedIds = value.ids.toList()
    }
    LaunchedEffect(scope, visibleIds) {
        val changedScope = selectionScope != scope
        updateSelection(selection)
        if (changedScope) selectionMode = false
    }
    BackHandler(enabled = selectionActive) { selectionMode = false; selectedIds = emptyList() }
    val browseMenu=DeviceBrowseMenuState(sort,reverse,filter,showOsName,selectionActive,
        onSort={sortName=it.name},onReverse={reverse=!reverse},onFilter={filterName=it.name},
        onOsName={showOsName=!showOsName},onSelection={selectionMode=!selectionActive;updateSelection(selection.clear())})
    val hasConnectedSessions = desktop.values.any { it == SessionStatus.CONNECTED } ||
        terminal.values.any { it == SessionStatus.CONNECTED } ||
        files.values.any { it == SessionStatus.CONNECTED }
    val hideSettings by (viewModel.sessionManager?.hideSettingsIconFlow ?: remember { MutableStateFlow(false) }).collectAsState()
    val searchFocus=remember { FocusRequester() }
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    var showSearch by remember { mutableStateOf(false) }
    val disconnectAll = iecxiv.meshfabric.ui.settings.rememberDisconnectAllAction {
        viewModel.closeAllActiveSessions()
    }
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
    Scaffold(topBar={ Column {
        MeshFabricAppBar(viewModel,onOpenSettings,actions={ Row(horizontalArrangement=Arrangement.spacedBy((-6).dp)) {
            IconButton(onClick=disconnectAll,modifier=Modifier.size(38.dp)) {
                Icon(Icons.Default.Link,stringResource(R.string.close_active_connections),Modifier.size(20.dp),
                    tint=if(hasConnectedSessions) Color(0xFF2196F3) else LocalContentColor.current)
            }
            if (groupBy) ToolIcon(if(expanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                stringResource(if(expanded) R.string.collapse_all_groups else R.string.expand_all_groups)) { viewModel.toggleAllGroups(groups.map {it.first.id}.toSet()) }
            DeviceHeaderSort(browseMenu)
            DeviceHeaderBrowseFilter(showIps=!hideDeviceNetworkInfo,showDisconnected=deviceListEffectiveShowDisconnected(!onlyOnline,filter),
                menu=browseMenu,
                onShowIps={show->
                    val hidden=!show
                    val manager=viewModel.sessionManager
                    if(manager!=null) manager.saveHideDeviceNetworkInfo(hidden) else fallbackNetworkVisibility.value=hidden
                },
                onShowDisconnected={show->
                    val enabled=!show
                    val manager=viewModel.sessionManager
                    if(manager!=null) manager.saveOnlyOnlineDevices(enabled) else fallbackOnlineFilter.value=enabled
                })
            DeviceHeaderRefresh { viewModel.refresh() }
            IconButton(onClick={
                if(showSearch) {
                    viewModel.searchQuery.value=""
                    focusManager.clearFocus(force=true)
                    keyboard?.hide()
                }
                showSearch=!showSearch
            },modifier=Modifier.size(38.dp)) {
                Icon(if(showSearch) Icons.Default.Close else Icons.Default.Search,stringResource(R.string.search),Modifier.size(20.dp),
                    tint=if(showSearch) Color(0xFF2196F3) else LocalContentColor.current)
            }
            if(!hideSettings) ToolIcon(Icons.Default.Settings,stringResource(R.string.settings),onOpenSettings)
        } })
        if(selectionActive) Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),
        shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,
        border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            IconButton(onClick={selectionMode=false;selectedIds=emptyList()}) {Icon(Icons.Default.Close,if(es) "Cancelar selección" else "Cancel selection")}
            Checkbox(checked=visibleIds.isNotEmpty() && selection.ids.containsAll(visibleIds),enabled=visibleIds.isNotEmpty(),onCheckedChange={
                updateSelection(if(selection.ids.containsAll(visibleIds)) selection.clear() else selection.selectVisible(scope,visibleIds))
            })
            Text(if(es) "Todos (${selection.ids.size})" else "All (${selection.ids.size})",style=MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            DeviceListExportButton(viewModel.meshClient,filteredGroups.flatMap {(g,ns)->ns.filter {it.id in selection.ids}.map {g to it}}.distinctBy {it.second.id})
        }
    }
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
                val groupExpanded = !groupBy || search.isNotBlank() || group.id !in collapsed
                if (groupBy) item(key="group_${group.id}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectionActive) Checkbox(
                            checked = nodes.isNotEmpty() && nodes.all { it.id in selection.ids },
                            enabled = nodes.isNotEmpty(),
                            onCheckedChange = { updateSelection(selection.toggleGroup(scope, nodes.map { it.id }.toSet(), visibleIds)) }
                        )
                        Box(Modifier.weight(1f)) {
                            GroupHeaderCard(group,nodes.count { it.isOnline },nodes.count { !it.isOnline },groupExpanded) { viewModel.toggleGroupExpanded(group.id) }
                        }
                    }
                }
                if(groupExpanded) items(nodes,key={ "node_${group.id.length}:${group.id}_${it.id}" }) { node ->
                    LaunchedEffect(node.id, networkRefresh, networkLifecycle, networkConnected) {
                        if (networkConnected && networkLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            while (true) {
                                viewModel.requestDeviceNetworkInfo(node.id)
                                kotlinx.coroutines.delay(10_000L)
                            }
                        }
                    }
                    val clean=node.id.removePrefix("node//").removePrefix("node/").trim()
                    DeviceCard(node,desktop[clean] ?: SessionStatus.NONE,terminal[clean] ?: SessionStatus.NONE,files[clean] ?: SessionStatus.NONE,
                        { onOpenDesktop(node.id,node.name) },{ onOpenTerminal(node.id,node.name) },{ onOpenFiles(node.id,node.name) }, showNetworkInfo = !hideDeviceNetworkInfo,
                        selectionMode = selectionActive, selected = node.id in selection.ids,
                        onToggleSelection = { if (selectionActive) updateSelection(selection.toggle(scope, node.id, visibleIds)) },
                        onStartSelection = { selectionMode = true; updateSelection(selection.toggle(scope, node.id, visibleIds)) },
                        showOsName=showOsName,starred=node.id in starredIds,onToggleStar={starredIds=if(node.id in starredIds) starredIds-node.id else starredIds+node.id})
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
@OptIn(ExperimentalFoundationApi::class)
@Composable fun DeviceCard(node:MeshNode,desktopStatus:SessionStatus=SessionStatus.NONE,terminalStatus:SessionStatus=SessionStatus.NONE,
    fileStatus:SessionStatus=SessionStatus.NONE,onOpenDesktop:()->Unit,onOpenTerminal:()->Unit,onOpenFiles:()->Unit,showNetworkInfo:Boolean=true,
    selectionMode:Boolean=false,selected:Boolean=false,onToggleSelection:(()->Unit)?=null,onStartSelection:(()->Unit)?=null,showOsName:Boolean=false,starred:Boolean=false,onToggleStar:(()->Unit)?=null) {
    val os=node.osDisplayName.ifEmpty { stringResource(R.string.device_os_unknown) }
    var otherExpanded by androidx.compose.runtime.saveable.rememberSaveable(node.id) { mutableStateOf(false) }
    var statusBadgeHeight by remember(node.id) { mutableStateOf(0.dp) }
    val statusBadgeDensity=LocalDensity.current
    Card(Modifier.fillMaxWidth().padding(vertical=4.dp).combinedClickable(
        enabled = onStartSelection != null || selectionMode,
        onClick = { onToggleSelection?.invoke() }, onLongClick = { onStartSelection?.invoke() }),
        border=androidx.compose.foundation.BorderStroke(if(selected && selectionMode) 2.dp else 1.dp,if(selected && selectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors= iecxiv.meshfabric.ui.theme.appCardColors(
CardDefaults.cardColors(containerColor=if(node.isOnline) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onToggleSelection?.invoke() })
                Surface(Modifier.size(40.dp),shape=CircleShape,color=(if(node.isOnline) OnlineGreen else OfflineGray).copy(alpha=0.15f)) {
                    Box(contentAlignment=Alignment.Center) {
                        Icon(when {
                            node.platform==DevicePlatform.WINDOWS->Icons.Default.DesktopWindows
                            node.platform==DevicePlatform.ANDROID->Icons.Default.Android
                            else->Icons.Default.Computer
                        },null,Modifier.size(24.dp),tint=if(node.isOnline) OnlineGreen else OfflineGray)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(deviceListDisplayName(node,showOsName).ifEmpty { stringResource(R.string.unknown_device) },modifier=Modifier.weight(1f,fill=false),
                            style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(iecxiv.meshfabric.ui.theme.LocalAppDarkTheme.current) Color.White else LocalContentColor.current)
                        Spacer(Modifier.width(8.dp))
                        Surface(modifier=Modifier.onSizeChanged { statusBadgeHeight=with(statusBadgeDensity) { it.height.toDp() } }.testTag("device_connection_badge_${node.id}"),shape=RoundedCornerShape(4.dp),color=if(node.isOnline) OnlineGreen else OfflineGray) {
                            Text(if(node.isOnline) "ONLINE" else "OFFLINE",Modifier.padding(horizontal=6.dp,vertical=2.dp),color=Color.White,fontSize=9.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                    if(os.isNotEmpty()) Text(os,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(iecxiv.meshfabric.ui.theme.LocalAppDarkTheme.current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    val networkText = DeviceNetworkDisplay.text(node.networkIpv4.orEmpty(), hidden = !showNetworkInfo)
                    if (networkText != null) Text(
                        text = networkText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if(iecxiv.meshfabric.ui.theme.LocalAppDarkTheme.current) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!selectionMode) Row(horizontalArrangement=Arrangement.spacedBy((-6).dp),verticalAlignment=Alignment.CenterVertically) {
                    if(onToggleStar!=null) IconButton(onClick=onToggleStar,modifier=Modifier.size(38.dp)) {
                        Icon(if(starred) Icons.Default.Star else Icons.Default.StarBorder,if(androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language=="es") "Destacado local en MeshFabric" else "Local MeshFabric favorite",Modifier.size(20.dp),tint=if(starred) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    DeviceOptionsToggle(otherExpanded) { otherExpanded = !otherExpanded }
                    Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {
                        LocalDeviceActionMenu.current(node)
                    }
                }
            }
            AnimatedVisibility(otherExpanded && !selectionMode) { DeviceOptionsRow(node,if(statusBadgeHeight>0.dp) statusBadgeHeight else 28.dp) }
            if(!selectionMode && (node.isOnline || listOf(desktopStatus,terminalStatus,fileStatus).any { it!=SessionStatus.NONE })) {
                Spacer(Modifier.height(if(otherExpanded) 4.dp else 8.dp))
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
