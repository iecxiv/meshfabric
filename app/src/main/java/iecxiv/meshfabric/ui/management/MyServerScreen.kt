package iecxiv.meshfabric.ui.management

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
internal val connectionHistoryKeys=listOf("ca","cu","us","rs","am","amc")
internal fun connectionHistoryValues(value:JsonElement?):Map<String,Double> {
    if(value==null || !value.isJsonObject) return emptyMap()
    return connectionHistoryKeys.mapNotNull { key->
        val count=runCatching {
            val field=value.asJsonObject.get(key)
            if(field==null || !field.isJsonPrimitive || !field.asJsonPrimitive.isNumber) null
            else field.asDouble.takeIf { it.isFinite() && it>=0 && it<=9007199254740991.0 && it%1.0==0.0 }
        }.getOrNull()
        count?.let { "conn.$key" to it }
    }.toMap()
}
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
        values.putAll(connectionHistoryValues(item.get("conn")))
        repeat(3) { i->number(indexed(item.get("cpu"),i))?.let { values["cpu.$i"]=it } }
        val mem=item.get("mem")?.takeIf { it.isJsonObject }?.asJsonObject
        listOf("rss","heapTotal","heapUsed","external").forEach { key->number(mem?.get(key))?.let { values["mem.$key"]=it/1048576.0 } }
        val traffic=item.get("traffic")?.takeIf { it.isJsonObject }?.asJsonObject
        fun directions(key:String,inbound:Double?,outbound:Double?) {
            inbound?.let { values["traffic.$key.in"]=it/1048576.0 }
            outbound?.let { values["traffic.$key.out"]=it/1048576.0 }
            val available=listOfNotNull(inbound,outbound)
            if(available.isNotEmpty()) available.sum().takeIf { it.isFinite() }?.let { values["traffic.$key"]=it/1048576.0 }
        }
        directions("agent",total(traffic?.get("AgentCtrlIn")),total(traffic?.get("AgentCtrlOut")))
        directions("http",total(traffic?.get("httpIn")),total(traffic?.get("httpOut")))
        listOf("terminal" to 1,"desktop" to 2,"files" to 5).forEach { (key,index)->
            directions(key,number(indexed(traffic?.get("relayIn"),index)),number(indexed(traffic?.get("relayOut"),index)))
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

private data class ServerReadState(val ready:Boolean=false,val failed:Boolean=false,val stats:String="",val pending:String="",val result:String="",val history:String="",val hours:Int=3,val resultKind:String="")
private class ServerReader(private val source:MeshCentralClient,initialHours:Int) {
    val state=MutableStateFlow(ServerReadState(hours=normalizedHistoryHours(initialHours)))
    private var socket:WebSocket?=null
    private var generation=0L
    @Synchronized fun connect() {
        close()
        if(source.isUserLogout || source.authCookie.isBlank()) { state.value=ServerReadState(failed=true,hours=state.value.hours); return }
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
                        "serverversion","servererrors","serverconfig"->if(state.value.pending==action) state.update { it.copy(pending="",result=packet.toString(),resultKind=action) }
                        "close","stopped"->failed(epoch)
                    }
                } }
                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,"") }
                override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { failed(epoch) }
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { failed(epoch) }
            })
        } catch(_:Exception) { state.value=ServerReadState(failed=true,hours=state.value.hours) }
    }
    @Synchronized private fun failed(epoch:Long) {
        if(epoch!=generation) return
        generation++; val previous=socket; socket=null
        state.update { it.copy(ready=false,failed=true,pending="",result="",history="",resultKind="") }
        previous?.cancel()
    }
    @Synchronized fun request(action:String,hours:Int=state.value.hours) {
        if(action !in setOf("serverversion","servererrors","serverconfig","servertimelinestats") || !state.value.ready || state.value.pending.isNotEmpty()) return
        if(action=="servertimelinestats" && hours !in serverHistoryPeriods) return
        val packet=JSONObject().put("action",action)
        if(action=="servertimelinestats") packet.put("hours",hours)
        state.update { it.copy(pending=action,result="",resultKind="",failed=false,history=if(action=="servertimelinestats") "" else it.history,hours=if(action=="servertimelinestats") hours else it.hours) }
        if(socket?.send(packet.toString())!=true) failed(generation)
    }
    @Synchronized fun timeout(action:String) { if(state.value.pending==action) failed(generation) }
    @Synchronized fun connectionTimeout() { if(!state.value.ready) failed(generation) }
    @Synchronized fun dismiss() { state.update { it.copy(result="",resultKind="") } }
    @Synchronized fun close() {
        generation++
        socket?.send("{\"action\":\"serverstats\"}"); socket?.cancel(); socket=null
        state.value=ServerReadState(hours=state.value.hours)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MyServerScreen(source:MeshCentralClient?,modifier:Modifier=Modifier,header:@Composable (@Composable ()->Unit)->Unit={}) {
    val context=LocalContext.current.applicationContext
    val preferences=remember(context) { ServerHistoryPreferences(context.getSharedPreferences("server_history",Context.MODE_PRIVATE)) }
    val reader=remember(source,preferences) { source?.let { ServerReader(it,preferences.hours()) } }
    val state=reader?.state?.collectAsState()?.value ?: ServerReadState(failed=true,hours=preferences.hours())
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
    fun refreshHistory(period:Int) {
        if(period in serverHistoryPeriods && state.ready && state.pending.isEmpty()) {preferences.saveHours(period);reader?.request("servertimelinestats",period)}
    }
    Column(modifier) {
        ServerStatisticsHeader(tab,state.ready,state.pending.isNotEmpty(),header) {refreshHistory(state.hours)}
        ServerSectionTabs(tab) { tab=it }
        ManagementConnectionStatus(ready=state.ready,connecting=!state.ready && !state.failed,modifier=Modifier.padding(horizontal=16.dp),onReconnect={retry++})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(managementConnectionContentPadding),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            when(tab) {
                0->{
                    ServerGeneralActions {
                        ServerBackupPanel(source)
                        val enabled=state.ready && state.pending.isEmpty()
                        TextButton(onClick={ reader?.request("serverversion") },enabled=enabled) { Text(stringResource(R.string.my_server_version)) }
                        TextButton(onClick={ reader?.request("servererrors") },enabled=enabled) { Text(stringResource(R.string.my_server_errors)) }
                        TextButton(onClick={ confirmConfig=true },enabled=enabled) { Text(stringResource(R.string.my_server_config)) }
                    }
                    ServerGeneralOverview(state.stats,state.ready)
                }
                1->HistoryPanel(state.history,state.hours,state.ready,state.pending.isNotEmpty(),onLoad=::refreshHistory)
                else->ServerAdminPanel(source,tab==3)
            }
            if(state.pending.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if(confirmConfig) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ confirmConfig=false },title={ Text(stringResource(R.string.my_server_config)) },text={ Text(stringResource(R.string.my_server_sensitive)) },
        confirmButton={ TextButton(onClick={ confirmConfig=false; reader?.request("serverconfig") }) { Text(stringResource(R.string.my_server_view)) } },dismissButton={ TextButton(onClick={ confirmConfig=false }) { Text(stringResource(R.string.my_server_cancel)) } })
    if(state.result.isNotEmpty()) ServerReadResultDialog(state.result,state.resultKind) { reader?.dismiss() }
}

@Composable private fun HistoryPanel(raw:String,hours:Int,ready:Boolean,busy:Boolean,onLoad:(Int)->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var inbound by rememberSaveable { mutableStateOf(true) }
    var outbound by rememberSaveable { mutableStateOf(true) }
    var logarithmic by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable { mutableStateOf("") }
    val parsed=remember(raw) { runCatching { serverHistory(raw) } }
    val points=parsed.getOrDefault(emptyList())
    val servers=points.map { it.server }.distinct()
    val selected=server.takeIf { it in servers } ?: servers.firstOrNull().orEmpty()
    val filtered=remember(points,selected) { points.filter { it.server==selected } }
    val directions=buildList { if(inbound) add("in"); if(outbound) add("out") }
    val trafficKeys=listOf("agent","http","terminal","desktop","files")
    val trafficLabels=listOf(if(es) "Agente" else "Agent","HTTP","Terminal",if(es) "Escritorio" else "Desktop",if(es) "Archivos" else "Files")
    val keys=when(kind) {
        0->connectionHistoryKeys.map { "conn.$it" }
        1->listOf("cpu.0","cpu.1","cpu.2")
        2->listOf("mem.rss","mem.heapTotal","mem.heapUsed","mem.external")
        else->trafficKeys.flatMap { key->directions.map { direction->"traffic.$key.$direction" } }
    }
    val labels=when(kind) {
        0->if(es) listOf("Agentes","Usuarios conectados","Sesiones de usuario","Sesiones de relé","Intel AMT directo","Intel AMT CIRA") else listOf("Agents","Connected users","User sessions","Relay sessions","Direct Intel AMT","Intel AMT CIRA")
        1->listOf("1 min","5 min","15 min")
        2->listOf("RSS","Heap total",if(es) "Heap usado" else "Heap used",if(es) "Externa" else "External")
        else->trafficLabels.flatMap { label->directions.map { direction->label+" · "+if(direction=="in") { if(es) "Entrante" else "Inbound" } else { if(es) "Saliente" else "Outbound" } } }
    }
    val colors=listOf(Color(0xFFB28704),Color(0xFF2878C0),Color(0xFFD94A78),Color(0xFF238A37),Color(0xFF9B4AAA),Color(0xFF00838F))
    fun seriesColor(index:Int)=colors[if(kind==3) index/directions.size.coerceAtLeast(1) else index]
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf(if(es) "Conexiones" else "Connections","CPU",if(es) "Memoria" else "Memory",if(es) "Tráfico" else "Traffic").forEachIndexed { i,label->FilterChip(selected=kind==i,onClick={ kind=i },label={ Text(label) }) }
    }
    if(kind==3) {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(selected=inbound,enabled=!inbound || outbound,onClick={ inbound=!inbound },label={ Text(if(es) "Entrante" else "Inbound") })
            FilterChip(selected=outbound,enabled=!outbound || inbound,onClick={ outbound=!outbound },label={ Text(if(es) "Saliente" else "Outbound") })
        }
        Text(if(es) "Desde el servidor: entrante, línea continua; saliente, línea discontinua. Al menos una dirección activa." else "Server perspective: inbound, solid line; outbound, dashed line. At least one direction stays active.",style=MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        serverHistoryPeriods.forEach { period->FilterChip(selected=hours==period,enabled=ready && !busy,onClick={ onLoad(period) },label={ Text(if(period==720) { if(es) "30 días" else "30 days" } else "$period h") }) }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
            FilterChip(selected=logarithmic,onClick={ logarithmic=!logarithmic },label={ Text(if(es) "Escala logarítmica" else "Log scale") })
        }
        val exportable=parsed.isSuccess && filtered.any { point->keys.any { point.values[it]!=null } }
        ServerCsvDownload(enabled=ready && !busy && exportable,fileName="ServerStarts.csv",create={ serverHistoryCsv(filtered,keys,hours) })
    }
    if(servers.size>1) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        servers.forEach { id->FilterChip(selected=selected==id,onClick={ server=id },label={ Text(id.ifBlank { if(es) "Servidor local" else "Local server" }) }) }
    }
    Text(when(kind) { 0->if(es) "Recuentos en cada muestra · no conexiones por segundo" else "Counts at each sample · not connections per second"; 1->if(es) "Carga media, no porcentaje de CPU" else "Load average, not CPU percent"; 2->"MiB"; else->if(es) "MiB por muestra · direcciones separadas · no es una velocidad" else "MiB per sample · separate directions · not a rate" },style=MaterialTheme.typography.bodySmall)
    if(logarithmic) Text(if(es) "Los ceros y valores ausentes interrumpen la línea." else "Zero and missing values break the line.",style=MaterialTheme.typography.bodySmall)
    val series=remember(filtered,keys,logarithmic) { keys.map { historySegments(filtered,it,logarithmic) } }
    val all=series.flatten().flatten()
    when {
        parsed.isFailure->Text(if(es) "Historial no válido o demasiado grande para el visor." else "Invalid history or viewer limit exceeded.",color=MaterialTheme.colorScheme.error)
        all.isEmpty()->Text(when {
            logarithmic && filtered.any { point->keys.any { point.values[it]==0.0 } }->if(es) "No hay valores positivos para mostrar en escala logarítmica. Usa la escala lineal para ver los ceros." else "No positive values to plot on log scale. Use linear scale to see zeros."
            kind==1->if(es) "Sin datos para esta métrica. Algunos servidores no incluyen CPU en el historial." else "No data for this metric. Some servers omit CPU from history."
            kind==0->if(es) "Sin datos históricos de conexiones para este servidor y periodo." else "No connection history for this server and period."
            else->if(es) "Sin datos para esta métrica." else "No data for this metric."
        })
        else->{
            val minTime=filtered.minOf { it.time }; val maxTime=filtered.maxOf { it.time }
            val low=if(logarithmic) all.minOf { it.second } else 0.0
            val high=all.maxOf { it.second }.let { if(it<=low) low+1.0 else it }
            val grid=MaterialTheme.colorScheme.outlineVariant
            val axis=if(logarithmic) "log10" else ""
            val axisFormat=if(kind==0 && !logarithmic) "%.0f" else "%.3f"
            Text("$axis ${String.format(java.util.Locale.getDefault(),axisFormat,high)}",style=MaterialTheme.typography.labelSmall)
            val description=if(es) "Gráfico temporal. Valores de la última muestra debajo." else "Time chart. Latest sample values follow."
            Canvas(Modifier.fillMaxWidth().height(300.dp).semantics { contentDescription=description }) {
                val width=(size.width-12.dp.toPx()).coerceAtLeast(1f)
                val height=(size.height-12.dp.toPx()).coerceAtLeast(1f)
                val offset=6.dp.toPx()
                val dashed=PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx()))
                repeat(5) { i->val y=offset+height*i/4; drawLine(grid,Offset(offset,y),Offset(offset+width,y)) }
                fun position(point:Pair<Long,Double>):Offset=Offset(offset+(((point.first-minTime).toDouble()/(maxTime-minTime).coerceAtLeast(1))*width).toFloat(),offset+((1-(point.second-low)/(high-low))*height).toFloat())
                series.forEachIndexed { i,segments->segments.forEach { segment->
                    val effect=if(kind==3 && keys[i].endsWith(".out")) dashed else null
                    segment.zipWithNext().forEach { (a,b)->drawLine(seriesColor(i),position(a),position(b),strokeWidth=2.dp.toPx(),pathEffect=effect) }
                    if(segment.size==1) drawCircle(seriesColor(i),2.dp.toPx(),position(segment.first()))
                } }
            }
            Text("$axis ${String.format(java.util.Locale.getDefault(),axisFormat,low)}",style=MaterialTheme.typography.labelSmall)
            val formatter=remember { DateTimeFormatter.ofPattern("dd/MM HH:mm z").withZone(ZoneId.systemDefault()) }
            Text("${formatter.format(Instant.ofEpochMilli(minTime))} — ${formatter.format(Instant.ofEpochMilli(maxTime))}",style=MaterialTheme.typography.labelSmall)
            Text(if(es) "Última muestra:" else "Latest sample:",style=MaterialTheme.typography.titleSmall)
            keys.forEachIndexed { i,key->
                val value=filtered.lastOrNull()?.values?.get(key)
                Text("${labels[i]}: ${value?.let { String.format(java.util.Locale.getDefault(),if(kind==0) "%.0f" else "%.3f",it) } ?: "—"}",color=seriesColor(i),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
