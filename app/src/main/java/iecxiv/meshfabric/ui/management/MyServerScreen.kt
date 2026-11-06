package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import org.json.JSONObject

private data class ServerReadState(val ready:Boolean=false,val failed:Boolean=false,val stats:String="",val pending:String="",val result:String="")

private class ServerReader(private val source:MeshCentralClient) {
    val state=MutableStateFlow(ServerReadState())
    private var socket:WebSocket?=null
    private var generation=0L
    @Synchronized fun connect() {
        close()
        if(source.isUserLogout || source.authCookie.isBlank()) { state.value=ServerReadState(failed=true); return }
        val epoch=generation
        state.value=ServerReadState()
        try {
            socket=OkHttpHelper.createClient(source.ignoreSsl).newWebSocket(Request.Builder()
                .url(OkHttpHelper.toWebSocketUrl(source.serverUrl,"/control.ashx?moreargs=1"))
                .header("Cookie",source.authCookie).build(),object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) {
                    synchronized(this@ServerReader) {
                        if(epoch!=generation) { webSocket.cancel(); return }
                        webSocket.send(JSONObject().put("action","urlargs").put("args",JSONObject().put("auth",source.extractRawToken(source.authCookie))).toString())
                        webSocket.send("{\"action\":\"userinfo\"}")
                    }
                }
                override fun onMessage(webSocket:WebSocket,text:String) {
                    synchronized(this@ServerReader) {
                        if(epoch!=generation || text.length>2000000) return
                        val packet=runCatching { JSONObject(text) }.getOrNull() ?: return
                        val action=packet.optString("action")
                        when(action) {
                            "ping"->webSocket.send("{\"action\":\"pong\"}")
                            "userinfo"->if(!packet.optJSONObject("userinfo")?.optString("_id").isNullOrBlank()) {
                                state.update { it.copy(ready=true,failed=false) }
                                webSocket.send("{\"action\":\"serverstats\",\"interval\":5000}")
                            }
                            "serverstats"->state.update { it.copy(stats=packet.toString()) }
                            "serverversion","servererrors","serverconfig","servertimelinestats"->if(state.value.pending==action) {
                                state.update { it.copy(pending="",result=packet.toString(2)) }
                            }
                            "close","stopped"->{ state.update { it.copy(ready=false,failed=true,pending="",result="") }; webSocket.cancel() }
                        }
                    }
                }
                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,"") }
                override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { failed(epoch) }
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { failed(epoch) }
            })
        } catch(_:Exception) { state.value=ServerReadState(failed=true) }
    }
    @Synchronized private fun failed(epoch:Long) { if(epoch==generation) state.update { it.copy(ready=false,failed=true,pending="",result="") } }
    @Synchronized fun request(action:String) {
        if(action !in setOf("serverversion","servererrors","serverconfig","servertimelinestats") || !state.value.ready || state.value.pending.isNotEmpty()) return
        val packet=JSONObject().put("action",action)
        if(action=="servertimelinestats") packet.put("hours",24)
        state.update { it.copy(pending=action,result="",failed=false) }
        if(socket?.send(packet.toString())!=true) state.update { it.copy(pending="",failed=true) }
    }
    @Synchronized fun timeout(action:String) { if(state.value.pending==action) state.update { it.copy(pending="",failed=true) } }
    @Synchronized fun connectionTimeout() { if(!state.value.ready) state.update { it.copy(failed=true) } }
    @Synchronized fun dismiss() { state.update { it.copy(result="") } }
    @Synchronized fun close() {
        generation++
        socket?.send("{\"action\":\"serverstats\"}")
        socket?.cancel(); socket=null
        state.value=ServerReadState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MyServerScreen(source:MeshCentralClient?,modifier:Modifier=Modifier) {
    val reader=remember(source) { source?.let { ServerReader(it) } }
    val state=reader?.state?.collectAsState()?.value ?: ServerReadState(failed=true)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var retry by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmConfig by remember { mutableStateOf(false) }
    LaunchedEffect(reader,lifecycle,retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try { reader?.connect(); awaitCancellation() } finally { reader?.close() }
        }
    }
    LaunchedEffect(reader,retry,state.ready) { if(!state.ready) { delay(20000); reader?.connectionTimeout() } }
    LaunchedEffect(state.pending) { if(state.pending.isNotEmpty()) { delay(25000); reader?.timeout(state.pending) } }
    DisposableEffect(reader) { onDispose { reader?.close() } }
    Column(modifier) {
        Text(stringResource(R.string.my_server_title),Modifier.padding(16.dp),style=MaterialTheme.typography.headlineSmall)
        ScrollableTabRow(selectedTabIndex=tab,edgePadding=8.dp) {
            listOf(R.string.my_server_general,R.string.my_server_statistics,R.string.my_server_console,R.string.my_server_trace).forEachIndexed { index,label->
                Tab(selected=tab==index,onClick={ tab=index },text={ Text(stringResource(label)) })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(when { state.failed->R.string.my_server_failure; state.ready->R.string.my_server_connected; else->R.string.my_server_connecting }),
                color=if(state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.failed) TextButton(onClick={ retry++ }) { Text(stringResource(R.string.my_server_retry)) }
            when(tab) {
                0->{
                    Text(stringResource(R.string.my_server_actions),style=MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.my_server_backup_pending),style=MaterialTheme.typography.bodySmall)
                    val enabled=state.ready && state.pending.isEmpty()
                    TextButton(onClick={ reader?.request("serverversion") },enabled=enabled) { Text(stringResource(R.string.my_server_version)) }
                    TextButton(onClick={ reader?.request("servererrors") },enabled=enabled) { Text(stringResource(R.string.my_server_errors)) }
                    TextButton(onClick={ confirmConfig=true },enabled=enabled) { Text(stringResource(R.string.my_server_config)) }
                    HorizontalDivider()
                    Text(stringResource(R.string.my_server_status),style=MaterialTheme.typography.titleMedium)
                    val stats=remember(state.stats) { runCatching { JSONObject(state.stats) }.getOrNull() }
                    if(stats==null) Text(stringResource(R.string.my_server_no_data)) else {
                        Text(stringResource(R.string.my_server_cpu)+": "+(stats.optJSONArray("cpuavg")?.toString() ?: "—"))
                        Text(stringResource(R.string.my_server_memory)+": "+listOf("availablemem","freemem","totalmem").joinToString(" / ") { key->
                            if(stats.has(key)) "${stats.optLong(key)/1048576} MiB" else "—"
                        })
                        val values=stats.optJSONObject("values")?.optJSONObject("ServerState")
                        listOf("UserAccounts" to R.string.my_server_users,"DeviceGroups" to R.string.my_server_groups,
                            "AgentSessions" to R.string.my_server_agents,"ConnectedUsers" to R.string.my_server_online_users,
                            "UsersSessions" to R.string.my_server_sessions,"RelaySessions" to R.string.my_server_relays,
                            "RelayCount" to R.string.my_server_relay_count,"ConnectedIntelAMT" to R.string.my_server_amt,
                            "ConnectedIntelAMTCira" to R.string.my_server_cira).forEach { (key,label)->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(stringResource(label),Modifier.weight(1f))
                                Text(if(values?.has(key)==true) values.optString(key) else "—")
                            }
                        }
                    }
                }
                1->{
                    Text(stringResource(R.string.my_server_history_description))
                    TextButton(onClick={ reader?.request("servertimelinestats") },enabled=state.ready && state.pending.isEmpty()) { Text(stringResource(R.string.my_server_history)) }
                }
                else->Text(stringResource(R.string.my_server_section_pending))
            }
            if(state.pending.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if(confirmConfig) AlertDialog(onDismissRequest={ confirmConfig=false },title={ Text(stringResource(R.string.my_server_config)) },
        text={ Text(stringResource(R.string.my_server_sensitive)) },confirmButton={ TextButton(onClick={ confirmConfig=false; reader?.request("serverconfig") }) { Text(stringResource(R.string.my_server_view)) } },
        dismissButton={ TextButton(onClick={ confirmConfig=false }) { Text(stringResource(R.string.my_server_cancel)) } })
    if(state.result.isNotEmpty()) AlertDialog(onDismissRequest={ reader?.dismiss() },title={ Text(stringResource(R.string.my_server_result)) },
        text={ SelectionContainer { Text(state.result,Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall) } },
        confirmButton={ TextButton(onClick={ reader?.dismiss() }) { Text(stringResource(R.string.my_server_close)) } })
}
