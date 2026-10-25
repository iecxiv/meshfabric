package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.text.DateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal enum class ActivityKind { ACTION, SERVER, MESSAGE }
internal enum class ActivityStatus { SENT, ACCEPTED, OUTPUT, CONFIRMED, ERROR, UNKNOWN, INFO }
internal data class ActivityEntry(val id:String,val nodeId:String,val deviceName:String,val title:String,val description:String,
    val status:ActivityStatus,val summary:String,val time:Long=System.currentTimeMillis(),val updated:Long=time,
    val actor:String="",val requestId:String="",val kind:ActivityKind=ActivityKind.ACTION,val action:String="",val receivedTime:Boolean=false) {
    val target:String get()=deviceName.ifBlank { if(nodeId.isNotBlank()) "Equipo no identificado" else if(kind==ActivityKind.SERVER) "Servidor" else "Origen no identificado" }
}
private fun JSONObject.str(key:String):String=opt(key) as? String ?: ""
internal class ActivityJournal(private val resolveName:(String)->String,private val currentActor:()->String) {
    private val data=MutableStateFlow<List<ActivityEntry>>(emptyList())
    val entries=data.asStateFlow()
    private val names=ConcurrentHashMap<String,String>()
    fun rememberDevices(devices:Map<String,String>) { names.putAll(devices.filterValues { it.isNotBlank() }) }
    private fun name(id:String):String {
        val found=resolveName(id)
        if(id.isNotBlank() && found.isNotBlank()) names[id]=found
        return found.ifBlank { names[id].orEmpty() }
    }
    private fun put(entry:ActivityEntry) { data.update { (listOf(entry)+it.filterNot { old->old.id==entry.id }).take(200) } }
    fun sent(title:String,j:JSONObject,sent:Boolean) {
        val id=j.str("responseid").ifBlank { UUID.randomUUID().toString() }
        val node=j.str("nodeid").ifBlank { j.optJSONArray("nodeids")?.optString(0).orEmpty() }.ifBlank { j.str("id").takeIf { it.startsWith("node/") }.orEmpty() }
        val action=j.str("action")
        val description=when {
            action=="toast" -> "Título: ${j.str("title").take(160)}"
            action=="runcommands" -> "Comando remoto; su salida se consulta en la ventana de resultado."
            action=="msg" && j.str("type")=="openUrl" -> runCatching {
                val u=java.net.URI(j.str("url")); java.net.URI(u.scheme,null,u.host,u.port,u.path,null,null).toString()
            }.getOrDefault("URL enviada (parámetros ocultos)")
            else -> ""
        }
        val summary=if(!sent) "No se pudo enviar la solicitud." else if(action=="toast")
            "Enviada al servidor; visualización en el equipo no confirmada." else "Enviada al servidor; ejecución en el equipo sin confirmar."
        put(ActivityEntry(id,node,name(node).ifBlank { if(node.isNotBlank()) title.substringAfter(" · ","") else "" },
            title.substringBefore(" · "),description,if(sent) ActivityStatus.SENT else ActivityStatus.ERROR,summary,
            actor=currentActor(),requestId=id,action=action))
    }
    fun reply(j:JSONObject) {
        val id=j.str("responseid")
        if(id.isBlank()) return
        val node=j.str("nodeid")
        val result=when(val r=j.opt("result")) { is String->r; true->"OK"; false->"Solicitud rechazada"; else->"" }
        val error=j.str("error").ifBlank { if(j.has("success") && !j.optBoolean("success")) "Solicitud rechazada" else "" }
        val output=j.str("action")=="msg" && j.str("type")=="runcommands" && (j.has("result") || j.has("value"))
        val rejected=error.isNotBlank() || listOf("denied","error","invalid","failed","forbidden","rechazad").any { result.contains(it,true) }
        val accepted=result.equals("ok",true) || (j.has("success") && j.optBoolean("success"))
        if(!output && !rejected && !accepted && result.isBlank()) return
        val status=when { output->ActivityStatus.OUTPUT; rejected->ActivityStatus.ERROR; accepted->ActivityStatus.ACCEPTED; else->ActivityStatus.UNKNOWN }
        val message=when { output->"Salida recibida del agente."; rejected->error.ifBlank { result }.take(600); accepted->"Aceptada por el servidor; ejecución todavía sin confirmar."; else->"Respuesta recibida, resultado no interpretado: ${result.take(300)}" }
        data.update { rows->rows.map { if(it.requestId==id && (node.isBlank() || it.nodeId==node)) it.copy(status=status,summary=message,updated=System.currentTimeMillis()) else it } }
    }
    fun event(j:JSONObject) {
        val node=j.str("nodeid").ifBlank { (j.opt("node") as? String) ?: j.optJSONObject("node")?.str("_id").orEmpty() }
        val action=j.str("action")
        val title=mapOf("nodeconnect" to "Equipo conectado","nodedisconnect" to "Equipo desconectado","changenode" to "Datos del equipo actualizados",
            "removenode" to "Equipo eliminado","meshchange" to "Grupo actualizado","accountchange" to "Cuenta actualizada")[action] ?: "Evento del servidor"
        val raw=j.opt("time")
        val parsed=when(raw) {
            is Number->raw.toLong().takeIf { it>0 }?.let { if(it<100000000000L) it*1000 else it }
            is String->runCatching { Instant.parse(raw).toEpochMilli() }.getOrElse { runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull() }
            else->null
        }
        val id=j.str("eventid").ifBlank { j.str("_id") }.ifBlank { UUID.randomUUID().toString() }
        put(ActivityEntry("event-$id",node,name(node).ifBlank { j.str("nodename").ifBlank { j.optJSONObject("node")?.str("name").orEmpty() } },
            title,"",ActivityStatus.INFO,j.str("msg").ifBlank { "El servidor no incluyó una descripción adicional." }.take(1000),
            time=parsed ?: System.currentTimeMillis(),actor=j.str("username"),kind=ActivityKind.SERVER,action=action,receivedTime=parsed==null))
    }
    fun unmatched(j:JSONObject) {
        val rid=j.str("responseid")
        if(rid.isNotBlank() && data.value.any { it.requestId==rid }) return
        val body=j.str("error").ifBlank { j.str("result") }.ifBlank { if(j.str("type")=="notify") j.str("value").ifBlank { j.str("msg") } else "" }
        if(body.isBlank()) return
        val node=j.str("nodeid")
        put(ActivityEntry(UUID.randomUUID().toString(),node,name(node),"Mensaje sin operación vinculada","",ActivityStatus.UNKNOWN,body.take(600),
            kind=ActivityKind.MESSAGE,action=j.str("action"),receivedTime=true))
    }
    fun unconfirmed(id:String) { data.update { rows->rows.map { if(it.requestId==id && it.status in setOf(ActivityStatus.SENT,ActivityStatus.ACCEPTED))
        it.copy(status=ActivityStatus.UNKNOWN,summary="No hay confirmación de ejecución. Comprueba el equipo antes de repetir la acción.",updated=System.currentTimeMillis()) else it } } }
    fun interrupted() { data.update { rows->rows.map { if(it.status in setOf(ActivityStatus.SENT,ActivityStatus.ACCEPTED))
        it.copy(status=ActivityStatus.UNKNOWN,summary="Conexión interrumpida; resultado desconocido. No se repetirá la solicitud.",updated=System.currentTimeMillis()) else it } } }
    fun clear() { data.value=emptyList(); names.clear() }
}
private fun ActivityEntry.withCommand(command:CommandFeedback?):ActivityEntry {
    if(command==null) return this
    val s=when(command.phase) { CommandPhase.WAITING->ActivityStatus.SENT; CommandPhase.ACCEPTED->ActivityStatus.ACCEPTED; CommandPhase.OUTPUT->ActivityStatus.OUTPUT; CommandPhase.ERROR->ActivityStatus.ERROR; CommandPhase.UNKNOWN->ActivityStatus.UNKNOWN }
    return copy(status=s,summary=command.message)
}
private fun ActivityStatus.label():String=when(this) { ActivityStatus.SENT->"Solicitud enviada"; ActivityStatus.ACCEPTED->"Aceptada"; ActivityStatus.OUTPUT->"Salida recibida"; ActivityStatus.CONFIRMED->"Confirmada"; ActivityStatus.ERROR->"Rechazada / error"; ActivityStatus.UNKNOWN->"Sin confirmación"; ActivityStatus.INFO->"Evento" }
@Composable internal fun ActivityTimeline(journal:ActivityJournal,feedback:RemoteActionFeedback,modifier:Modifier=Modifier) {
    val records by journal.entries.collectAsState()
    val remote by feedback.state.collectAsState()
    var filter by remember { mutableIntStateOf(0) }
    var device by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val devices=records.filter { it.nodeId.isNotBlank() }.distinctBy { it.nodeId }.map { it.nodeId to it.target }.sortedBy { it.second.lowercase() }
    val choices=listOf("" to "Todos los dispositivos","__server" to "Servidor","__unknown" to "Origen no identificado")+devices
    val entries=records.map { entry->entry.withCommand(remote.commands.find { it.id==entry.requestId }) }.filter { entry->
        val kind=when(filter) { 1->entry.kind==ActivityKind.ACTION; 2->entry.kind==ActivityKind.SERVER; 3->entry.status==ActivityStatus.ERROR; else->true }
        kind && when(device) { ""->true; "__server"->entry.nodeId.isBlank() && entry.kind==ActivityKind.SERVER; "__unknown"->entry.nodeId.isBlank() && entry.kind!=ActivityKind.SERVER; else->entry.nodeId==device }
    }.sortedByDescending { it.time }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Todos","Mis acciones","Servidor","Errores").forEachIndexed { i,label->FilterChip(selected=filter==i,onClick={ filter=i },label={ Text(label) }) }
        }
        Box {
            OutlinedButton(onClick={ menu=true },modifier=Modifier.fillMaxWidth()) { Text(choices.find { it.first==device }?.second ?: "Todos los dispositivos",maxLines=1,overflow=TextOverflow.Ellipsis) }
            DropdownMenu(expanded=menu,onDismissRequest={ menu=false },modifier=Modifier.heightIn(max=280.dp)) {
                choices.forEach { (id,label)->DropdownMenuItem(text={ Text(label) },onClick={ device=id; menu=false }) }
            }
        }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(vertical=8.dp)) {
            if(entries.isEmpty()) item { Text("No hay actividad que coincida con los filtros.",style=MaterialTheme.typography.bodyMedium) }
            entries.groupBy { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it.time)) }.forEach { (date,group)->
                item(key="date-$date") { Text(date,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary) }
                items(group,key={ it.id }) { entry->
                    var details by remember(entry.id) { mutableStateOf(false) }
                    val color=when(entry.status) { ActivityStatus.ERROR->MaterialTheme.colorScheme.error; ActivityStatus.UNKNOWN->MaterialTheme.colorScheme.tertiary; ActivityStatus.CONFIRMED,ActivityStatus.OUTPUT->MaterialTheme.colorScheme.primary; else->MaterialTheme.colorScheme.onSurfaceVariant }
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text(entry.target,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            Text((if(entry.receivedTime) "Recibido " else "")+DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(entry.time)),style=MaterialTheme.typography.labelSmall)
                        }
                        Text(entry.title,style=MaterialTheme.typography.bodyLarge)
                        Surface(shape=MaterialTheme.shapes.small,color=color.copy(alpha=0.12f)) { Text(entry.status.label(),Modifier.padding(horizontal=8.dp,vertical=3.dp),color=color,style=MaterialTheme.typography.labelMedium) }
                        Text(entry.summary,style=MaterialTheme.typography.bodyMedium)
                        if(entry.description.isNotBlank()) Text(entry.description,style=MaterialTheme.typography.bodySmall)
                        if(entry.actor.isNotBlank()) Text("Por ${entry.actor}",style=MaterialTheme.typography.labelSmall)
                        Row {
                            if(remote.commands.any { it.id==entry.requestId }) TextButton(onClick={ feedback.openCommand(entry.requestId) }) { Text("Ver salida") }
                            TextButton(onClick={ details=!details }) { Text(if(details) "Ocultar detalles" else "Detalles") }
                        }
                        if(details) {
                            if(entry.nodeId.isNotBlank()) Text("ID del equipo: ${entry.nodeId}",style=MaterialTheme.typography.bodySmall)
                            if(entry.requestId.isNotBlank()) Text("Petición: ${entry.requestId}",style=MaterialTheme.typography.bodySmall)
                            if(entry.action.isNotBlank()) Text("Tipo técnico: ${entry.action}",style=MaterialTheme.typography.bodySmall)
                            if(entry.updated!=entry.time) Text("Última actualización: ${DateFormat.getDateTimeInstance().format(Date(entry.updated))}",style=MaterialTheme.typography.bodySmall)
                            if(entry.action=="runcommands" && remote.commands.none { it.id==entry.requestId }) Text("La salida ya no se conserva en esta sesión.",style=MaterialTheme.typography.bodySmall)
                        }
                    } }
                }
            }
        }
    }
}
