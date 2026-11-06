package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.log10

internal data class HistoryPoint(val time:Long,val server:String,val first:Boolean,val values:Map<String,Double>)
internal fun historyTime(value:JsonElement?):Long? = runCatching {
    if(value==null || value.isJsonNull) null else {
        val text=value.asString
        val number=text.toDoubleOrNull()
        if(number!=null) {
            if(!number.isFinite() || number<=0 || number>Long.MAX_VALUE.toDouble()/1000) null
            else (if(number<100000000000.0) number*1000 else number).toLong()
        } else runCatching { Instant.parse(text).toEpochMilli() }.getOrElse { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
    }
}.getOrNull()
internal fun serverHistory(raw:String):List<HistoryPoint> {
    if(raw.isBlank()) return emptyList()
    val events=JsonParser.parseString(raw).asJsonObject.getAsJsonArray("events") ?: error("Missing events")
    require(events.size()<=20000) { "History exceeds viewer limit" }
    fun number(value:JsonElement?):Double?=runCatching { value?.asDouble?.takeIf { it.isFinite() && it>=0 } }.getOrNull()
    fun indexed(value:JsonElement?,index:Int):JsonElement?=when {
        value==null || value.isJsonNull->null
        value.isJsonArray->value.asJsonArray.let { if(index<it.size()) it[index] else null }
        value.isJsonObject->value.asJsonObject.get(index.toString())
        else->null
    }
    fun total(value:JsonElement?):Double? {
        if(value==null || value.isJsonNull) return null
        if(value.isJsonPrimitive) return number(value)
        val parts=if(value.isJsonArray) value.asJsonArray.toList() else if(value.isJsonObject) value.asJsonObject.entrySet().map { it.value } else emptyList()
        val numbers=parts.mapNotNull { number(it) }
        return if(numbers.isEmpty()) null else numbers.sum().takeIf { it.isFinite() }
    }
    return events.mapNotNull { element->
        if(!element.isJsonObject) return@mapNotNull null
        val item=element.asJsonObject
        val time=historyTime(item.get("time")) ?: return@mapNotNull null
        val values=mutableMapOf<String,Double>()
        repeat(3) { i->number(indexed(item.get("cpu"),i))?.let { values["cpu.$i"]=it } }
        val mem=item.get("mem")?.takeIf { it.isJsonObject }?.asJsonObject
        listOf("rss","heapTotal","heapUsed","external").forEach { key->number(mem?.get(key))?.let { values["mem.$key"]=it/1048576.0 } }
        val traffic=item.get("traffic")?.takeIf { it.isJsonObject }?.asJsonObject
        fun combined(key:String,vararg parts:Double?) {
            val available=parts.filterNotNull()
            if(available.isNotEmpty()) available.sum().takeIf { it.isFinite() }?.let { values["traffic.$key"]=it/1048576.0 }
        }
        combined("agent",total(traffic?.get("AgentCtrlIn")),total(traffic?.get("AgentCtrlOut")))
        combined("http",total(traffic?.get("httpIn")),total(traffic?.get("httpOut")))
        listOf("terminal" to 1,"desktop" to 2,"files" to 5).forEach { (key,index)->
            combined(key,number(indexed(traffic?.get("relayIn"),index)),number(indexed(traffic?.get("relayOut"),index)))
        }
        HistoryPoint(time,runCatching { item.get("s")?.asString }.getOrNull().orEmpty(),runCatching { item.get("first")?.asBoolean }.getOrNull()==true,values)
    }.distinctBy { it.server to it.time }.sortedBy { it.time }
}
internal fun historySegments(points:List<HistoryPoint>,key:String,logarithmic:Boolean):List<List<Pair<Long,Double>>> {
    val segments=mutableListOf<List<Pair<Long,Double>>>()
    var current=mutableListOf<Pair<Long,Double>>()
    var previousServer:String?=null
    fun flush() { if(current.isNotEmpty()) segments.add(current.toList()); current=mutableListOf() }
    points.forEach { point->
        if(point.first || (previousServer!=null && previousServer!=point.server)) flush()
        previousServer=point.server
        val value=point.values[key]?.takeIf { it.isFinite() && it>=0 && (!logarithmic || it>0) }
        if(value==null) flush() else current.add(point.time to if(logarithmic) log10(value) else value)
    }
    flush()
    return segments
}

private data class ServerReadState(val ready:Boolean=false,val failed:Boolean=false,val stats:String="",val pending:String="",val result:String="",val history:String="",val hours:Int=24)
private class ServerReader(private val source:MeshCentralClient) {
    val state=MutableStateFlow(ServerReadState())
    private var socket:WebSocket?=null
    private var generation=0L
    @Synchronized fun connect() {
        close()
        if(source.isUserLogout || source.authCookie.isBlank()) { state.value=ServerReadState(failed=true); return }
        val epoch=generation
        try {
            socket=OkHttpHelper.createClient(source.ignoreSsl).newWebSocket(Request.Builder()
                .url(OkHttpHelper.toWebSocketUrl(source.serverUrl,"/control.ashx?moreargs=1"))
                .header("Cookie",source.authCookie).build(),object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) { synchronized(this@ServerReader) {
                    if(epoch!=generation) { webSocket.cancel(); return }
                    webSocket.send(JSONObject().put("action","urlargs").put("args",JSONObject().put("auth",source.extractRawToken(source.authCookie))).toString())
                    webSocket.send("{\"action\":\"userinfo\"}")
                } }
                override fun onMessage(webSocket:WebSocket,text:String) { synchronized(this@ServerReader) {
                    if(epoch!=generation) return
                    if(text.length>8000000) { failed(epoch); return }
                    val packet=runCatching { JSONObject(text) }.getOrNull() ?: return
                    when(val action=packet.optString("action")) {
                        "ping"->webSocket.send("{\"action\":\"pong\"}")
                        "userinfo"->if(!packet.optJSONObject("userinfo")?.optString("_id").isNullOrBlank()) {
                            state.update { it.copy(ready=true,failed=false) }
                            webSocket.send("{\"action\":\"serverstats\",\"interval\":5000}")
                        }
                        "serverstats"->state.update { it.copy(stats=packet.toString()) }
                        "servertimelinestats"->if(state.value.pending==action) state.update { it.copy(pending="",history=packet.toString()) }
                        "serverversion","servererrors","serverconfig"->if(state.value.pending==action) state.update { it.copy(pending="",result=packet.toString(2)) }
                        "close","stopped"->failed(epoch)
                    }
                } }
                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,"") }
                override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { failed(epoch) }
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { failed(epoch) }
            })
        } catch(_:Exception) { state.value=ServerReadState(failed=true) }
    }
    @Synchronized private fun failed(epoch:Long) {
        if(epoch!=generation) return
        generation++; val previous=socket; socket=null
        state.update { it.copy(ready=false,failed=true,pending="",result="",history="") }
        previous?.cancel()
    }
    @Synchronized fun request(action:String,hours:Int=24) {
        if(action !in setOf("serverversion","servererrors","serverconfig","servertimelinestats") || !state.value.ready || state.value.pending.isNotEmpty()) return
        if(action=="servertimelinestats" && hours !in setOf(3,8,24,48,72,720)) return
        val packet=JSONObject().put("action",action)
        if(action=="servertimelinestats") packet.put("hours",hours)
        state.update { it.copy(pending=action,result="",failed=false,history=if(action=="servertimelinestats") "" else it.history,hours=if(action=="servertimelinestats") hours else it.hours) }
        if(socket?.send(packet.toString())!=true) failed(generation)
    }
    @Synchronized fun timeout(action:String) { if(state.value.pending==action) failed(generation) }
    @Synchronized fun connectionTimeout() { if(!state.value.ready) failed(generation) }
    @Synchronized fun dismiss() { state.update { it.copy(result="") } }
    @Synchronized fun close() {
        generation++
        socket?.send("{\"action\":\"serverstats\"}"); socket?.cancel(); socket=null
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
    LaunchedEffect(reader,lifecycle,retry) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        try { reader?.connect(); awaitCancellation() } finally { reader?.close() }
    } }
    LaunchedEffect(reader,retry,state.ready) { if(!state.ready) { delay(20000); reader?.connectionTimeout() } }
    LaunchedEffect(state.pending) { if(state.pending.isNotEmpty()) { delay(25000); reader?.timeout(state.pending) } }
    LaunchedEffect(tab,state.ready) { if(tab==1 && state.ready && state.history.isBlank()) reader?.request("servertimelinestats",state.hours) }
    DisposableEffect(reader) { onDispose { reader?.close() } }
    Column(modifier) {
        Text(stringResource(R.string.my_server_title),Modifier.padding(16.dp),style=MaterialTheme.typography.headlineSmall)
        ScrollableTabRow(selectedTabIndex=tab,edgePadding=8.dp) {
            listOf(R.string.my_server_general,R.string.my_server_statistics,R.string.my_server_console,R.string.my_server_trace).forEachIndexed { index,label->
                Tab(selected=tab==index,onClick={ tab=index },text={ Text(stringResource(label)) })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(stringResource(when { state.failed->R.string.my_server_failure; state.ready->R.string.my_server_connected; else->R.string.my_server_connecting }),color=if(state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(stringResource(R.string.my_server_memory)+": "+listOf("availablemem","freemem","totalmem").joinToString(" / ") { key->if(stats.has(key)) "${stats.optLong(key)/1048576} MiB" else "—" })
                        val values=stats.optJSONObject("values")?.optJSONObject("ServerState")
                        listOf("UserAccounts" to R.string.my_server_users,"DeviceGroups" to R.string.my_server_groups,"AgentSessions" to R.string.my_server_agents,"ConnectedUsers" to R.string.my_server_online_users,"UsersSessions" to R.string.my_server_sessions,"RelaySessions" to R.string.my_server_relays,"RelayCount" to R.string.my_server_relay_count,"ConnectedIntelAMT" to R.string.my_server_amt,"ConnectedIntelAMTCira" to R.string.my_server_cira).forEach { (key,label)->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(stringResource(label),Modifier.weight(1f)); Text(if(values?.has(key)==true) values.optString(key) else "—") }
                        }
                    }
                }
                1->HistoryPanel(state.history,state.hours,state.ready,state.pending.isNotEmpty()) { reader?.request("servertimelinestats",it) }
                else->ServerAdminPanel(source,tab==3)
            }
            if(state.pending.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if(confirmConfig) AlertDialog(onDismissRequest={ confirmConfig=false },title={ Text(stringResource(R.string.my_server_config)) },text={ Text(stringResource(R.string.my_server_sensitive)) },
        confirmButton={ TextButton(onClick={ confirmConfig=false; reader?.request("serverconfig") }) { Text(stringResource(R.string.my_server_view)) } },dismissButton={ TextButton(onClick={ confirmConfig=false }) { Text(stringResource(R.string.my_server_cancel)) } })
    if(state.result.isNotEmpty()) AlertDialog(onDismissRequest={ reader?.dismiss() },title={ Text(stringResource(R.string.my_server_result)) },
        text={ SelectionContainer { Text(state.result,Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall) } },confirmButton={ TextButton(onClick={ reader?.dismiss() }) { Text(stringResource(R.string.my_server_close)) } })
}

@Composable private fun HistoryPanel(raw:String,hours:Int,ready:Boolean,busy:Boolean,onLoad:(Int)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var logarithmic by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable { mutableStateOf("") }
    val parsed=remember(raw) { runCatching { serverHistory(raw) } }
    val points=parsed.getOrDefault(emptyList())
    val servers=points.map { it.server }.distinct()
    val selected=server.takeIf { it in servers } ?: servers.firstOrNull().orEmpty()
    val filtered=remember(points,selected) { points.filter { it.server==selected } }
    val keys=when(kind) { 0->listOf("cpu.0","cpu.1","cpu.2"); 1->listOf("mem.rss","mem.heapTotal","mem.heapUsed","mem.external"); else->listOf("traffic.agent","traffic.http","traffic.terminal","traffic.desktop","traffic.files") }
    val labels=when(kind) { 0->listOf("1 min","5 min","15 min"); 1->listOf("RSS","Heap total",if(es) "Heap usado" else "Heap used",if(es) "Externa" else "External"); else->listOf(if(es) "Agente" else "Agent","HTTP","Terminal",if(es) "Escritorio" else "Desktop",if(es) "Archivos" else "Files") }
    val colors=listOf(Color(0xFFB28704),Color(0xFF2878C0),Color(0xFFD94A78),Color(0xFF238A37),Color(0xFF9B4AAA))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf("CPU",if(es) "Memoria" else "Memory",if(es) "Tráfico" else "Traffic").forEachIndexed { i,label->FilterChip(selected=kind==i,onClick={ kind=i },label={ Text(label) }) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf(3,8,24,48,72,720).forEach { period->FilterChip(selected=hours==period,enabled=ready && !busy,onClick={ onLoad(period) },label={ Text(if(period==720) { if(es) "30 días" else "30 days" } else "$period h") }) }
    }
    Row {
        TextButton(onClick={ onLoad(hours) },enabled=ready && !busy) { Text(if(es) "Actualizar" else "Refresh") }
        FilterChip(selected=logarithmic,onClick={ logarithmic=!logarithmic },label={ Text(if(es) "Escala logarítmica" else "Log scale") })
    }
    if(servers.size>1) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        servers.forEach { id->FilterChip(selected=selected==id,onClick={ server=id },label={ Text(id.ifBlank { if(es) "Servidor local" else "Local server" }) }) }
    }
    Text(when(kind) { 0->if(es) "Carga media, no porcentaje de CPU" else "Load average, not CPU percent"; 1->"MiB"; else->if(es) "MiB por muestra · entrada + salida" else "MiB per sample · inbound + outbound" },style=MaterialTheme.typography.bodySmall)
    if(logarithmic) Text(if(es) "Los ceros y valores ausentes interrumpen la línea." else "Zero and missing values break the line.",style=MaterialTheme.typography.bodySmall)
    val series=remember(filtered,kind,logarithmic) { keys.map { historySegments(filtered,it,logarithmic) } }
    val all=series.flatten().flatten()
    when {
        parsed.isFailure->Text(if(es) "Historial no válido o demasiado grande para el visor." else "Invalid history or viewer limit exceeded.",color=MaterialTheme.colorScheme.error)
        all.isEmpty()->Text(if(es) "Sin datos para esta métrica. Algunos servidores no incluyen CPU en el historial." else "No data for this metric. Some servers omit CPU from history.")
        else->{
            val minTime=filtered.minOf { it.time }; val maxTime=filtered.maxOf { it.time }
            val low=if(logarithmic) all.minOf { it.second } else 0.0
            val high=all.maxOf { it.second }.let { if(it<=low) low+1.0 else it }
            val grid=MaterialTheme.colorScheme.outlineVariant
            val axis=if(logarithmic) "log10" else ""
            Text("$axis ${String.format(java.util.Locale.getDefault(),"%.3f",high)}",style=MaterialTheme.typography.labelSmall)
            val description=if(es) "Gráfico temporal. Valores de la última muestra debajo." else "Time chart. Latest sample values follow."
            Canvas(Modifier.fillMaxWidth().height(300.dp).semantics { contentDescription=description }) {
                val width=(size.width-12.dp.toPx()).coerceAtLeast(1f)
                val height=(size.height-12.dp.toPx()).coerceAtLeast(1f)
                val offset=6.dp.toPx()
                repeat(5) { i->val y=offset+height*i/4; drawLine(grid,Offset(offset,y),Offset(offset+width,y)) }
                fun position(point:Pair<Long,Double>):Offset=Offset(offset+(((point.first-minTime).toDouble()/(maxTime-minTime).coerceAtLeast(1))*width).toFloat(),offset+((1-(point.second-low)/(high-low))*height).toFloat())
                series.forEachIndexed { i,segments->segments.forEach { segment->
                    segment.zipWithNext().forEach { (a,b)->drawLine(colors[i],position(a),position(b),strokeWidth=2.dp.toPx()) }
                    if(segment.size==1) drawCircle(colors[i],2.dp.toPx(),position(segment.first()))
                } }
            }
            Text("$axis ${String.format(java.util.Locale.getDefault(),"%.3f",low)}",style=MaterialTheme.typography.labelSmall)
            val formatter=remember { DateTimeFormatter.ofPattern("dd/MM HH:mm z").withZone(ZoneId.systemDefault()) }
            Text("${formatter.format(Instant.ofEpochMilli(minTime))} — ${formatter.format(Instant.ofEpochMilli(maxTime))}",style=MaterialTheme.typography.labelSmall)
            Text(if(es) "Última muestra:" else "Latest sample:",style=MaterialTheme.typography.titleSmall)
            keys.forEachIndexed { i,key->
                val value=filtered.lastOrNull()?.values?.get(key)
                Text("${labels[i]}: ${value?.let { String.format(java.util.Locale.getDefault(),"%.3f",it) } ?: "—"}",color=colors[i],style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
