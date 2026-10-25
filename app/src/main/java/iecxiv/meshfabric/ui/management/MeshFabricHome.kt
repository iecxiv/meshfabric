package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import iecxiv.meshfabric.ui.devices.DeviceListScreen
import iecxiv.meshfabric.ui.devices.DeviceListViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val LocalDeviceActionMenu=staticCompositionLocalOf<@Composable (MeshNode)->Unit> { {} }
private fun json(vararg pairs:Pair<String,Any?>)=JSONObject().apply { pairs.forEach { (k,v)->put(k,v) } }
private fun JSONObject.value(k:String)=if(isNull(k)) "" else optString(k,"")
private data class Record(val title:String,val body:String)
private data class State(val ready:Boolean=false,val status:String="Sin conexión de acciones",val records:List<Record> = emptyList())
private class Management(private val source:MeshCentralClient):ViewModel() {
    val state=MutableStateFlow(State())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var socket:WebSocket?=null
    @Volatile private var generation=0L
    private var connecting=false
    @Synchronized fun connect() {
        if(connecting || state.value.ready || source.authCookie.isBlank()) return
        val epoch=++generation
        connecting=true; state.update { it.copy(status="Conectando acciones…") }
        socket=OkHttpHelper.createClient(source.ignoreSsl).newWebSocket(Request.Builder()
            .url(OkHttpHelper.toWebSocketUrl(source.serverUrl,"/control.ashx?moreargs=1"))
            .header("Cookie",source.authCookie).build(),object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) {
                if(epoch!=generation) { ws.cancel(); return }
                ws.send(json("action" to "urlargs","args" to json("auth" to source.extractRawToken(source.authCookie))).toString())
                ws.send(json("action" to "userinfo").toString())
            }
            override fun onMessage(ws:WebSocket,text:String) {
                synchronized(this@Management) {
                    if(epoch!=generation || text.length>256000) return
                    val j=runCatching { JSONObject(text) }.getOrNull() ?: return
                    when(j.value("action")) {
                        "ping"->ws.send(json("action" to "pong").toString())
                        "userinfo"->if(!j.optJSONObject("userinfo")?.value("_id").isNullOrBlank()) {
                            connecting=false; state.update { it.copy(ready=true,status="Acciones conectadas") }
                        }
                        "authcookie","serverinfo","pong"->Unit
                        "close","stopped"->fail(epoch)
                        "event"->j.optJSONObject("event")?.let { record("Evento",it.value("msg").ifBlank { "Cambio comunicado por el servidor" }) }
                        "getNotes"->record("Notas",j.value("notes").ifBlank { "No hay notas guardadas" })
                        else->{
                            val output=if(j.value("action")=="msg") j.value("value").ifBlank { j.value("data") } else ""
                            record(if(output.isNotBlank()) "Salida remota" else "Respuesta del servidor",
                                output.ifBlank { j.value("url") }.ifBlank { j.value("link") }.ifBlank { j.value("result") }
                                    .ifBlank { "Respuesta recibida. Comprueba el estado de la operación." }.take(32000))
                        }
                    }
                }
            }
            override fun onClosing(ws:WebSocket,code:Int,reason:String) { ws.close(code,"") }
            override fun onClosed(ws:WebSocket,code:Int,reason:String) { fail(epoch) }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) { fail(epoch) }
        })
        scope.launch { delay(20000); if(epoch==generation && !state.value.ready) fail(epoch) }
    }
    @Synchronized private fun fail(epoch:Long) {
        if(epoch!=generation) return
        generation++; connecting=false; socket?.cancel(); socket=null
        state.update { it.copy(ready=false,status="Acciones desconectadas; las sesiones remotas no se han cerrado") }
    }
    private fun record(title:String,body:String) { state.update { it.copy(records=(listOf(Record(title,body))+it.records).take(80)) } }
    @Synchronized fun send(title:String,payload:JSONObject,write:Boolean) {
        if(!state.value.ready) return
        val sent=socket?.send(payload.put("responseid",UUID.randomUUID().toString()).toString())==true
        if(write) record(title,if(sent) "Solicitud enviada. Esto no confirma su ejecución. No se reintentará automáticamente." else "No se ha enviado la solicitud.")
    }
    @Synchronized fun clear() { generation++; connecting=false; socket?.cancel(); socket=null; state.value=State() }
    override fun onCleared() { clear(); scope.cancel() }
}
private data class Operation(val title:String,val fields:List<Pair<String,String>>,val requiresAgent:Boolean=false,val build:(Map<String,String>)->JSONObject)

@Composable fun MeshFabricHome(viewModel:DeviceListViewModel,onLogout:()->Unit,onOpenSettings:()->Unit,
    onOpenDesktop:(String,String)->Unit,onOpenTerminal:(String,String)->Unit,onOpenFiles:(String,String)->Unit) {
    val factory=remember(viewModel.meshClient) { object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=Management(viewModel.meshClient) as T
    } }
    val management=androidx.lifecycle.viewmodel.compose.viewModel<Management>(factory=factory)
    val state by management.state.collectAsState()
    val groups by viewModel.meshClient.groupedDevices.collectAsState()
    val connected by viewModel.isConnected.collectAsState()
    val nodes=groups.flatMap { it.second }.distinctBy { it.id }
    var page by remember { mutableIntStateOf(0) }
    var operation by remember { mutableStateOf<Operation?>(null) }
    var operationNode by remember { mutableStateOf("") }
    LaunchedEffect(connected) { if(connected) management.connect() else management.clear() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if(page==0) CompositionLocalProvider(LocalDeviceActionMenu provides { node->
                DeviceActionMenu(node,state.ready,onRead={
                    management.send("Consultar notas · ${node.name}",json("action" to "getNotes","id" to node.id),false); page=1
                },onSelect={ op->operationNode=node.id; operation=op.copy(title="${op.title} · ${node.name}") })
            }) {
                DeviceListScreen(viewModel,{ management.clear(); onLogout() },onOpenSettings,onOpenDesktop,onOpenTerminal,onOpenFiles)
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Actividad",style=MaterialTheme.typography.headlineSmall)
                Text(state.status,style=MaterialTheme.typography.labelMedium)
                TextButton(onClick={ management.connect() },enabled=connected && !state.ready) { Text("Reconectar acciones") }
                Info("Estados de ejecución","Enviada no significa ejecutada. Si se corta la conexión, comprueba el equipo antes de repetir una operación.")
                state.records.forEach { Info(it.title,it.body) }
            }
        }
        NavigationBar {
            listOf("Equipos","Actividad").forEachIndexed { i,label->
                NavigationBarItem(selected=page==i,onClick={ page=i },icon={ Text(if(i==0) "▣" else "≋") },label={ Text(label) })
            }
        }
    }
    operation?.let { op->key(op.title,operationNode) {
        var values by remember { mutableStateOf(op.fields.associate { it.first to if(it.first=="shell") "shell" else "" }) }
        var review by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val available=nodes.any { it.id==operationNode && (!op.requiresAgent || it.conn and 1 != 0) }
        AlertDialog(onDismissRequest={ operation=null },title={ Text(op.title) },text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(!state.ready || !available) Text("La conexión o el equipo no están disponibles para esta operación.")
                if(review) {
                    Text("Confirma la operación sobre el equipo indicado. El servidor validará tus permisos.")
                    op.fields.forEach { (key,label)->Text("$label\n${values[key].orEmpty()}") }
                } else op.fields.forEach { (key,label)->
                    if(key=="shell") Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf("shell" to "Shell","cmd" to "CMD","powershell" to "PowerShell").forEach { (v,t)->
                            FilterChip(selected=values[key]==v,onClick={ values=values+(key to v) },label={ Text(t) })
                        }
                    } else OutlinedTextField(values[key].orEmpty(),{ values=values+(key to it) },label={ Text(label) },modifier=Modifier.fillMaxWidth())
                }
                if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            }
        },confirmButton={ TextButton(enabled=state.ready && available,onClick={
            runCatching { op.build(values) }.onSuccess {
                if(!review) review=true else { management.send(op.title,it,true); operation=null; page=1 }
            }.onFailure { error=it.message ?: "Revisa los valores" }
        }) { Text(if(review) "Confirmar envío" else "Revisar") } },dismissButton={ TextButton(onClick={ operation=null }) { Text("Cancelar") } })
    } }
}
@Composable private fun DeviceActionMenu(node:MeshNode,ready:Boolean,onRead:()->Unit,onSelect:(Operation)->Unit) {
    var expanded by remember(node.id) { mutableStateOf(false) }
    val agentOnline=node.conn and 1 != 0
    val targets=JSONArray(listOf(node.id))
    val actions=listOf(
        Operation("Ejecutar comando",listOf("cmds" to "Comando shell / script","shell" to "Intérprete"),true) { v->
            val type=when(v.getValue("shell")) { "shell"->3; "cmd"->0; "powershell"->2; else->error("Intérprete no válido") }
            require(v.getValue("cmds").isNotBlank()) { "Escribe un comando" }
            json("action" to "runcommands","nodeids" to targets,"type" to type,"cmds" to v.getValue("cmds"),"runAsUser" to 0,"reply" to true)
        },
        Operation("Guardar notas",listOf("notes" to "Notas (vacío elimina las notas)")) { json("action" to "setNotes","id" to node.id,"notes" to it.getValue("notes")) },
        Operation("Notificación remota",listOf("title" to "Título","msg" to "Mensaje"),true) { json("action" to "toast","nodeids" to targets,"title" to it.getValue("title"),"msg" to it.getValue("msg")) },
        Operation("Abrir URL remota",listOf("url" to "URL HTTP/HTTPS"),true) {
            val url=it.getValue("url").trim(); require(url.startsWith("https://") || url.startsWith("http://")) { "URL no válida" }
            json("action" to "msg","type" to "openUrl","nodeid" to node.id,"url" to url)
        },
        Operation("Enviar portapapeles",listOf("data" to "Texto para el portapapeles remoto"),true) { json("action" to "msg","type" to "setclip","nodeid" to node.id,"data" to it.getValue("data")) },
        Operation("Despertar (WoL)",emptyList()) { json("action" to "wakedevices","nodeids" to targets) },
        Operation("Reiniciar",emptyList(),true) { json("action" to "poweraction","nodeids" to targets,"actiontype" to 3) },
        Operation("Apagar",emptyList(),true) { json("action" to "poweraction","nodeids" to targets,"actiontype" to 2) },
        Operation("Suspender",emptyList(),true) { json("action" to "poweraction","nodeids" to targets,"actiontype" to 4) }
    )
    Box {
        IconButton(onClick={ expanded=true }) { Icon(Icons.Default.MoreVert,contentDescription="Acciones de ${node.name}") }
        DropdownMenu(expanded=expanded,onDismissRequest={ expanded=false },modifier=Modifier.heightIn(max=420.dp)) {
            DropdownMenuItem(text={ Text("Leer notas") },enabled=ready,onClick={ expanded=false; onRead() })
            actions.forEach { op->
                if(op.title=="Despertar (WoL)") HorizontalDivider()
                DropdownMenuItem(text={ Text(op.title) },enabled=ready && (!op.requiresAgent || agentOnline),onClick={ expanded=false; onSelect(op) })
            }
        }
    }
}
@Composable private fun Info(title:String,body:String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium)
        SelectionContainer { Text(body,fontFamily=if(title=="Salida remota") FontFamily.Monospace else FontFamily.Default) }
    } }
}
