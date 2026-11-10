package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.ui.components.MeshFabricActivityScreen
import iecxiv.meshfabric.ui.components.MeshFabricAppBar
import iecxiv.meshfabric.ui.devices.DeviceListViewModel
import kotlinx.coroutines.awaitCancellation
import java.text.DateFormat
import java.util.Date
@Composable internal fun LogSectionContent(selected:Int,content:@Composable (Int)->Unit) {
    val holder=rememberSaveableStateHolder()
    holder.SaveableStateProvider(selected) {content(selected)}
}
@Composable internal fun LogScreen(viewModel:DeviceListViewModel,journal:ActivityJournal,feedback:RemoteActionFeedback,actionsReady:Boolean,actionsStatus:String,onReconnect:()->Unit,onOpenSettings:()->Unit) {
    val actor by viewModel.userInfo.collectAsState()
    var tab by rememberSaveable {mutableIntStateOf(0)}
    key(viewModel.meshClient.serverUrl,viewModel.meshClient.authCookie,actor?.id) {
        LogSectionContent(tab) {selected->
            if(selected==0) MeshFabricActivityScreen(viewModel,journal,feedback,actionsReady,actionsStatus,onReconnect,onOpenSettings,header={RegistroSectionTabs(tab) {tab=it}})
            else ServerEventsLogScreen(viewModel,onOpenSettings,header={RegistroSectionTabs(tab) {tab=it}})
        }
    }
}
@Composable private fun ServerEventsLogScreen(viewModel:DeviceListViewModel,onOpenSettings:()->Unit,header:@Composable ()->Unit) {
    val source=viewModel.meshClient
    val actor by source.userInfo.collectAsState()
    val connected by source.connectionState.collectAsState()
    val groups by source.groupedDevices.collectAsState()
    val inventory=remember(groups,connected,actor?.id,source.serverUrl,source.authCookie) {if(connected && !actor?.id.isNullOrBlank() && !source.isUserLogout) serverEventInventory(groups) else ServerEventInventory()}
    var selectedType by rememberSaveable {mutableStateOf(ServerEventType.ALL.name)}
    var selectedLimit by rememberSaveable {mutableIntStateOf(60)}
    val selection=ServerEventsQuery(ServerEventType.entries.firstOrNull {it.name==selectedType} ?: ServerEventType.ALL,selectedLimit.takeUnless {it==0})
    val reader=remember(source,source.serverUrl,source.authCookie,actor?.id,actor?.siteadmin,connected) {LogEventsReader(current={
        val user=source.userInfo.value
        if(source.isUserLogout || !source.connectionState.value || user==null || user.id.isBlank() || source.authCookie.isBlank()) null
        else LogEventsBinding(source.serverUrl,user.id,source.authCookie,source.ignoreSsl,user.siteadmin,source.extractRawToken(source.authCookie))
    })}
    val state by reader.state.collectAsState()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var query by rememberSaveable {mutableStateOf("")}
    LaunchedEffect(reader,lifecycle,selection) {lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {try {reader.load(selection);awaitCancellation()} finally {reader.pause(selection)}}}
    DisposableEffect(reader) {onDispose {reader.close()}}
    Scaffold(topBar={Column {MeshFabricAppBar(viewModel,onOpenSettings);header()}}) {padding->
        val visible=if(state.selection==selection) state else ServerEventsSnapshot(ServerEventsPhase.LOADING,selection=selection)
        ServerEventsPanel(visible,query,{query=it},connected && !actor?.id.isNullOrBlank(),{reader.load(selection)},Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),selection=selection,onSelection={selectedType=it.type.name;selectedLimit=it.limit ?: 0},inventory=inventory)
    }
}
@Composable internal fun ServerEventsPanel(state:ServerEventsSnapshot,query:String,onQuery:(String)->Unit,enabled:Boolean,onRefresh:()->Unit,modifier:Modifier=Modifier,selection:ServerEventsQuery=legacyServerEventsQuery,onSelection:((ServerEventsQuery)->Unit)?=null,inventory:ServerEventInventory=ServerEventInventory()) {
    var detail by remember(state.records,state.phase,selection,inventory) {mutableStateOf<ServerEventRecord?>(null)}
    val status=when(state.phase) {
        ServerEventsPhase.IDLE->R.string.log_idle
        ServerEventsPhase.LOADING->R.string.log_loading
        ServerEventsPhase.DENIED->R.string.log_denied
        ServerEventsPhase.ERROR->R.string.log_error
        ServerEventsPhase.INVALID->when(state.issue?.kind) {
            ServerEventsIssueKind.FRAME_SIZE->R.string.log_invalid_frame
            ServerEventsIssueKind.JSON,ServerEventsIssueKind.ENVELOPE->R.string.log_invalid_json
            ServerEventsIssueKind.IDENTITY->R.string.log_invalid_identity
            ServerEventsIssueKind.HISTORY_SHAPE,ServerEventsIssueKind.RECORD_SHAPE,ServerEventsIssueKind.FIELD_TYPE,ServerEventsIssueKind.FIELD_LENGTH->R.string.log_invalid_history
            ServerEventsIssueKind.INTERNAL->R.string.log_invalid_internal
            null->R.string.log_invalid
        }
        ServerEventsPhase.TIMEOUT->R.string.log_timeout
        ServerEventsPhase.INVALIDATED->R.string.log_invalidated
        ServerEventsPhase.READY->null
    }
    Column(modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        detail?.let {ServerEventDetailsDialog(it) {detail=null}}
        Text(stringResource(R.string.log_scope),style=MaterialTheme.typography.bodySmall)
        if(onSelection!=null) ServerEventsControls(selection,enabled,onSelection)
        OutlinedTextField(query,onQuery,label={Text(stringResource(R.string.log_search))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("log-events-search"))
        TextButton(onClick=onRefresh,enabled=enabled && state.phase!=ServerEventsPhase.LOADING,modifier=Modifier.testTag("log-events-refresh")) {Text(stringResource(R.string.log_refresh))}
        if(status!=null) Text(stringResource(status),Modifier.testTag("log-events-state"),color=if(state.phase in setOf(ServerEventsPhase.DENIED,ServerEventsPhase.ERROR,ServerEventsPhase.INVALID,ServerEventsPhase.TIMEOUT)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        if(state.phase==ServerEventsPhase.INVALID && state.issue!=null) Text(stringResource(R.string.log_diagnostic,state.issue.safeCode()),Modifier.testTag("log-events-diagnostic"),style=MaterialTheme.typography.bodySmall)
        if(state.phase==ServerEventsPhase.LOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.phase==ServerEventsPhase.READY) {
            val rows=state.records.map {it.withInventory(inventory)}.filter {row->listOf(row.message,row.actor,row.nodeName,row.action,row.groupName,row.nodeId,row.meshId,row.details["cmds"].orEmpty()).any {it.contains(query.trim(),true)}}
            if(selection.limit!=null && state.records.size==selection.limit) Text(stringResource(R.string.log_limit),style=MaterialTheme.typography.bodySmall)
            if(state.records.isEmpty()) Text(stringResource(R.string.log_empty),Modifier.testTag("log-events-empty"))
            else if(rows.isEmpty()) Text(stringResource(R.string.log_no_match),Modifier.testTag("log-events-no-match"))
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {itemsIndexed(rows) {index,row->
                Card(Modifier.fillMaxWidth().testTag("log-event-row-$index").clickable {detail=row}) {Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ServerEventIcon(row.action)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(row.message.ifBlank {stringResource(R.string.log_event)},style=MaterialTheme.typography.bodyMedium)
                    if(row.deviceLabel().isNotBlank()) Text(row.deviceLabel(),Modifier.testTag("log-event-device-$index"),style=MaterialTheme.typography.labelMedium)
                    if(row.nodeId.isNotBlank()) Text(stringResource(R.string.log_detail_node)+": "+row.nodeId,Modifier.testTag("log-event-node-$index"),style=MaterialTheme.typography.labelSmall)
                    if(row.meshId.isNotBlank()) Text(stringResource(R.string.log_detail_mesh)+": "+row.meshId,Modifier.testTag("log-event-mesh-$index"),style=MaterialTheme.typography.labelSmall)
                    if(row.inventoryResolved) Text(stringResource(R.string.log_detail_inventory),style=MaterialTheme.typography.bodySmall)
                    if(row.actor.isNotBlank()) Text(row.actor,style=MaterialTheme.typography.labelSmall)
                    Text(row.time?.let {DateFormat.getDateTimeInstance().format(Date(it))} ?: stringResource(R.string.log_time_unknown),style=MaterialTheme.typography.labelSmall)
                }}}
            }}
        }
    }
}
