package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.devices.LocalDeviceDetailsClient
import java.util.Locale
import kotlin.math.ceil

private val generalJson=GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
internal data class ServerReadableResult(val text:String,val missing:Boolean=false,val invalidJson:Boolean=false,val noErrorLog:Boolean=false)
internal fun serverReadableResult(raw:String,action:String):ServerReadableResult = runCatching {
    val packet=JsonParser.parseString(raw).asJsonObject
    if(action !in setOf("serverconfig","servererrors")) return ServerReadableResult(generalJson.toJson(packet))
    if(action=="servererrors" && packet.entrySet().size==1 && packet.get("action")?.asString=="servererrors") {
        return ServerReadableResult("",noErrorLog=true)
    }
    if(!packet.has("data")) return ServerReadableResult(generalJson.toJson(packet),missing=true)
    val data=packet.get("data")
    if(action=="servererrors") {
        val text=when {
            data.isJsonNull->""
            data.isJsonPrimitive && data.asJsonPrimitive.isString->data.asString
            else->generalJson.toJson(data)
        }
        return ServerReadableResult(text.replace("\r\n","\n").replace('\r','\n'))
    }
    val decoded=if(data.isJsonPrimitive && data.asJsonPrimitive.isString) runCatching { JsonParser.parseString(data.asString) }.getOrNull() else data
    if(decoded==null || !decoded.isJsonObject) {
        val text=if(data.isJsonPrimitive && data.asJsonPrimitive.isString) data.asString else generalJson.toJson(data)
        ServerReadableResult(text,invalidJson=true)
    } else ServerReadableResult(generalJson.toJson(decoded))
}.getOrElse { ServerReadableResult(raw,missing=true) }

internal data class ServerOverview(val load:List<Double?>,val total:Double?,val free:Double?,val available:Double?,val counts:Map<String,Double>) {
    val loadScale:Double get()=maxOf(1.0,ceil(load.filterNotNull().maxOrNull() ?: 0.0))
    val loadFraction:Double? get()=load.firstOrNull()?.let { (it/loadScale).coerceIn(0.0,1.0) }
    val memoryFraction:Double? get()=if(total!=null && total>0 && free!=null && free<=total) (total-free)/total else null
}
private fun generalNumber(value:JsonElement?):Double?=runCatching {
    if(value==null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) null else value.asDouble.takeIf { it.isFinite() && it>=0 }
}.getOrNull()
internal fun serverOverview(raw:String):ServerOverview?=runCatching {
    val packet=JsonParser.parseString(raw).asJsonObject
    val cpu=packet.get("cpuavg")
    val load=(0..2).map { i->generalNumber(when {
        cpu?.isJsonArray==true->cpu.asJsonArray.let { if(i<it.size()) it[i] else null }
        cpu?.isJsonObject==true->cpu.asJsonObject.get(i.toString())
        else->null
    }) }
    val values=packet.get("values")?.takeIf { it.isJsonObject }?.asJsonObject
    val state=values?.get("ServerState")?.takeIf { it.isJsonObject }?.asJsonObject
    val counts=listOf("UserAccounts","DeviceGroups","AgentSessions","ConnectedUsers","UsersSessions","RelaySessions","RelayCount","ConnectedIntelAMT","ConnectedIntelAMTCira","RelayErrors").mapNotNull { key->
        generalNumber(state?.get(key))?.takeIf { it%1.0==0.0 && it<=9007199254740991.0 }?.let { key to it }
    }.toMap()
    ServerOverview(load,generalNumber(packet.get("totalmem")),generalNumber(packet.get("freemem")),generalNumber(packet.get("availablemem")),counts)
}.getOrNull()
private fun generalDecimal(value:Double?,digits:Int=2)=value?.let { String.format(Locale.getDefault(),"%.${digits}f",it) } ?: "—"
private fun generalMemory(value:Double?)=value?.let { generalDecimal(it/1048576.0,1)+" MiB" } ?: "—"

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ServerGeneralActions(content:@Composable ColumnScope.()->Unit) {
    Text(stringResource(R.string.my_server_actions),style=MaterialTheme.typography.titleMedium)
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
            Column(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                content()
            }
        }
    }
}
@Composable private fun ServerMetricRing(fraction:Double?,icon:ImageVector,description:String) {
    val track=MaterialTheme.colorScheme.outlineVariant
    val color=MaterialTheme.colorScheme.primary
    Box(Modifier.size(36.dp).semantics { contentDescription=description },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke=5.dp.toPx(); val inset=stroke/2
            val top=Offset(inset,inset); val bounds=Size(size.width-stroke,size.height-stroke)
            drawArc(track,-90f,360f,false,topLeft=top,size=bounds,style=Stroke(stroke))
            fraction?.let { drawArc(color,-90f,(it.coerceIn(0.0,1.0)*360).toFloat(),false,topLeft=top,size=bounds,style=Stroke(stroke,cap=StrokeCap.Round)) }
        }
        Icon(icon,null,Modifier.size(18.dp),tint=if(fraction==null) MaterialTheme.colorScheme.onSurfaceVariant else color)
    }
}
@Composable private fun ServerMetricCard(modifier:Modifier,title:String,fraction:Double?,icon:ImageVector,description:String,body:@Composable ()->Unit) {
    Surface(modifier,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            ServerMetricRing(fraction,icon,description)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
                body()
            }
        }
    }
}
@Composable private fun ServerLoadLine(values:List<String>) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        values.forEachIndexed { index,value->
            Text(value,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
            if(index<values.lastIndex) Text("/",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun ServerGeneralOverview(raw:String,connected:Boolean) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun label(a:String,b:String)=if(es) a else b
    val stats=remember(raw) { serverOverview(raw) }
    Text(label("Estadísticas del servidor","Server statistics"),style=MaterialTheme.typography.titleMedium)
    if(!connected && stats!=null) Text(label("Última muestra recibida · conexión no disponible","Last received sample · connection unavailable"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    val used=stats?.memoryFraction?.let { fraction->stats.total?.times(fraction) }
    val memoryBody=if(stats?.memoryFraction!=null) "${generalDecimal(stats.memoryFraction?.times(100),0)}%\n${generalMemory(used)} / ${generalMemory(stats.total)}" else label("Uso: —\nTotal: ","Usage: —\nTotal: ")+generalMemory(stats?.total)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        ServerMetricCard(Modifier.weight(1f).fillMaxHeight(),label("Carga del servidor","Server load"),stats?.loadFraction,Icons.Default.Speed,
            label("Media de carga de un minuto, escala ","One-minute load average, scale ")+"0–${generalDecimal(stats?.loadScale,0)}") {
            Column {
                ServerLoadLine((0..2).map { generalDecimal(stats?.load?.getOrNull(it)) })
                ServerLoadLine(listOf("1m","5m","15m"))
            }
        }
        ServerMetricCard(Modifier.weight(1f).fillMaxHeight(),label("Memoria","Memory"),stats?.memoryFraction,Icons.Default.Memory,
            label("Memoria usada calculada como total menos libre","Used memory calculated as total minus free")+": "+generalDecimal(stats?.memoryFraction?.times(100),0)+"%") {
            Text(memoryBody,style=MaterialTheme.typography.bodySmall)
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top) {
        Text(label("Carga: medias de 1/5/15 min, no porcentaje de CPU. El círculo representa 1 min con escala automática 0–","Load: 1/5/15-minute averages, not CPU percent. The ring shows 1 minute on automatic scale 0–")+generalDecimal(stats?.loadScale,0),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label("Libre: ","Free: ")+generalMemory(stats?.free)+label(" · Disponible: "," · Available: ")+generalMemory(stats?.available),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.my_server_status),style=MaterialTheme.typography.titleMedium)
    val rows=listOf(
        Triple("UserAccounts",stringResource(R.string.my_server_users),Icons.Default.People),
        Triple("DeviceGroups",stringResource(R.string.my_server_groups),Icons.Default.Folder),
        Triple("AgentSessions",stringResource(R.string.my_server_agents),Icons.Default.Computer),
        Triple("ConnectedUsers",stringResource(R.string.my_server_online_users),Icons.Default.Person),
        Triple("UsersSessions",stringResource(R.string.my_server_sessions),Icons.Default.Devices),
        Triple("RelaySessions",stringResource(R.string.my_server_relays),Icons.Default.Link),
        Triple("RelayCount",stringResource(R.string.my_server_relay_count),Icons.Default.SwapHoriz),
        Triple("ConnectedIntelAMT",stringResource(R.string.my_server_amt),Icons.Default.Memory),
        Triple("ConnectedIntelAMTCira",stringResource(R.string.my_server_cira),Icons.Default.Router))+
        if(stats?.counts?.containsKey("RelayErrors")==true) listOf(Triple("RelayErrors",label("Errores de relé","Relay errors"),Icons.Default.ErrorOutline)) else emptyList()
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal=12.dp)) {
            rows.forEachIndexed { index,(key,title,icon)->
                Row(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text(generalDecimal(stats?.counts?.get(key),0),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                }
                if(index<rows.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if(stats==null) Text(stringResource(R.string.my_server_no_data),style=MaterialTheme.typography.bodySmall)
}

@Composable internal fun ServerReadResultDialog(raw:String,action:String,onClose:()->Unit) {
    if(action=="serverversion") {
        ServerVersionDialog(LocalDeviceDetailsClient.current,onClose)
        return
    }
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun label(a:String,b:String)=if(es) a else b
    val result=remember(raw,action) { serverReadableResult(raw,action) }
    var wrap by remember(raw,action) { mutableStateOf(true) }
    var font by remember(raw,action) { mutableIntStateOf(12) }
    val title=stringResource(when(action) { "serverconfig"->R.string.my_server_config; "servererrors"->R.string.my_server_errors; "serverversion"->R.string.my_server_version; else->R.string.my_server_result })
    if(result.noErrorLog) {
        AlertDialog(onDismissRequest=onClose,title={ Text(title) },text={ Text(label("El servidor no tiene registro de errores.","The server has no error log.")) },
            confirmButton={ TextButton(onClick=onClose) { Text(stringResource(R.string.my_server_close)) } })
        return
    }
    AlertDialog(onDismissRequest=onClose,title={ Text(title) },text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(result.missing) Text(label("Respuesta inesperada: falta el contenido solicitado. Se muestra la respuesta recibida.","Unexpected response: requested content missing. Showing the received response."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            if(result.invalidJson) Text(label("El contenido recibido no es una configuración JSON válida.","Received content is not a valid JSON configuration."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(label("Ajustar líneas","Wrap lines"),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                Switch(checked=wrap,onCheckedChange={ wrap=it })
                TextButton(enabled=font>10,onClick={ font-- },contentPadding=PaddingValues(4.dp)) { Text("A−") }
                TextButton(enabled=font<20,onClick={ font++ },contentPadding=PaddingValues(4.dp)) { Text("A+") }
            }
            val content=if(action=="servererrors" && !result.missing && result.text.isBlank()) label("No hay errores registrados.","No errors recorded.") else result.text
            SelectionContainer {
                Box(Modifier.fillMaxWidth().heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                    Text(content,if(wrap) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,
                        style=MaterialTheme.typography.bodySmall.copy(fontSize=font.sp,lineHeight=(font+5).sp))
                }
            }
        }
    },confirmButton={ TextButton(onClick=onClose) { Text(stringResource(R.string.my_server_close)) } })
}
