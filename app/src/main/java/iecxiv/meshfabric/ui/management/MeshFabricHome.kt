package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.DesktopRelayClient
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import iecxiv.meshfabric.ui.components.MeshFabricActivityScreen
import iecxiv.meshfabric.ui.devices.DeviceListScreen
import iecxiv.meshfabric.ui.devices.DeviceListViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val LocalDeviceActionMenu=staticCompositionLocalOf<@Composable (MeshNode)->Unit> { {} }
private fun json(vararg pairs:Pair<String,Any?>)=JSONObject().apply { pairs.forEach { (k,v)->put(k,v) } }
private fun JSONObject.value(k:String)=if(isNull(k)) "" else optString(k,"")
private data class State(val ready:Boolean=false,val status:String="Sin conexión de acciones",val notes:DeviceNotes?=null)
private class Management(private val source:MeshCentralClient,private val desktop:DesktopRelayClient):ViewModel() {
    val state=MutableStateFlow(State())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    @Volatile private var socket:WebSocket?=null
    @Volatile private var generation=0L
    private var connecting=false
    private var notesJob:Job?=null
    private var notesEpoch=0L
    private var notesWriteId=""
    private val notesReads=mutableSetOf<String>()
    val activity=ActivityJournal(
        resolveName={ id->source.groupedDevices.value.asSequence().flatMap { it.second.asSequence() }.firstOrNull { it.id==id }?.name.orEmpty() },
        currentActor={ source.userInfo.value?.name.orEmpty() })
    val feedback=RemoteActionFeedback(scope,
        sendPacket={ packet->state.value.ready && socket?.send(packet.toString())==true },
        nativeClipboard={ id,text->
            if(!desktop.isConnectedToNode(id)) null else {
                val session=desktop.getSession(source,id)
                val packet=clipboardPacket(text)
                if(packet==null || !session.isConnected()) null else session.webSocket?.send(packet.toByteString())==true
            }
        })
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
                    if(epoch!=generation || text.length>2000000) return
                    val j=runCatching { JSONObject(text) }.getOrNull() ?: return
                    activity.reply(j)
                    if(feedback.receive(j)) return
                    when(j.value("action")) {
                        "ping"->ws.send(json("action" to "pong").toString())
                        "userinfo"->if(!j.optJSONObject("userinfo")?.value("_id").isNullOrBlank()) {
                            connecting=false; state.update { it.copy(ready=true,status="Acciones conectadas") }
                        }
                        "authcookie","serverinfo","pong"->Unit
                        "close","stopped"->fail(epoch)
                        "event"->j.optJSONObject("event")?.let { activity.event(it) }
                        "getNotes"->receiveNotes(j)
                        "setNotes"->{
                            if(notesWriteId.isNotBlank() && (j.value("responseid")==notesWriteId || j.value("id")==state.value.notes?.nodeId)) {
                                val error=j.value("error").ifBlank { j.value("result").takeUnless { it.equals("ok",true) }.orEmpty() }
                                if(error.isNotBlank()) failNotes("El servidor ha rechazado el guardado: $error")
                            }
                        }
                        else->activity.unmatched(j)
                    }
                }
            }
            override fun onClosing(ws:WebSocket,code:Int,reason:String) { ws.close(code,"") }
            override fun onClosed(ws:WebSocket,code:Int,reason:String) { fail(epoch) }
            override fun onFailure(ws:WebSocket,t:Throwable,response:Response?) { fail(epoch) }
        })
        scope.launch { delay(20000); if(epoch==generation && !state.value.ready) fail(epoch) }
    }
    @Synchronized private fun fail(epoch:Long) { if(epoch==generation) pause() }
    @Synchronized fun pause() {
        feedback.interrupted(); activity.interrupted()
        generation++; connecting=false; socket?.cancel(); socket=null
        notesEpoch++; notesJob?.cancel(); notesReads.clear(); notesWriteId=""
        state.update { it.copy(ready=false,status="Acciones desconectadas; las sesiones remotas no se han cerrado",
            notes=it.notes?.failed("Sin conexión. El borrador local se conserva; el guardado no está confirmado.")) }
    }
    @Synchronized fun send(title:String,payload:JSONObject,write:Boolean):Boolean {
        if(!state.value.ready) return false
        val id=UUID.randomUUID().toString()
        val request=JSONObject(payload.toString()).put("responseid",id)
        val sent=if(feedback.handles(request)) feedback.submit(title,request) else socket?.send(request.toString())==true
        if(write) {
            activity.sent(title,request,sent)
            if(sent && request.value("action")!="runcommands") scope.launch { delay(20000); activity.unconfirmed(id) }
        }
        return sent
    }
    @Synchronized fun openNotes(node:MeshNode,editable:Boolean) {
        notesEpoch++; notesJob?.cancel(); notesReads.clear(); notesWriteId=""
        val epoch=notesEpoch
        state.update { it.copy(notes=DeviceNotes(node.id,node.name,editable)) }
        if(!requestNotes(node.id)) { failNotes("No se pudo solicitar las notas. Comprueba la conexión."); return }
        notesJob=scope.launch {
            delay(10000)
            synchronized(this@Management) {
                if(epoch==notesEpoch && state.value.notes?.phase==NotesPhase.LOADING)
                    failNotes("El servidor no ha devuelto las notas. Comprueba conexión y permisos.")
            }
        }
    }
    @Synchronized private fun requestNotes(id:String):Boolean {
        if(!state.value.ready) return false
        val rid=UUID.randomUUID().toString()
        notesReads.add(rid)
        return socket?.send(json("action" to "getNotes","id" to id,"responseid" to rid).toString())==true
    }
    @Synchronized private fun receiveNotes(j:JSONObject) {
        val n=state.value.notes ?: return
        val id=j.value("id")
        val rid=j.value("responseid")
        if(id.isNotBlank() && id!=n.nodeId) return
        if(rid.isNotBlank() && rid !in notesReads) return
        if(id!=n.nodeId && rid !in notesReads) return
        if(rid.isNotBlank()) notesReads.remove(rid)
        if(!j.has("notes")) {
            failNotes(j.value("error").ifBlank { j.value("result") }.ifBlank { "Respuesta de notas sin contenido. No se reemplazará el borrador." }); return
        }
        val text=j.value("notes")
        if(text.length>64000) { failNotes("Las notas superan el límite del editor (64000 caracteres). No se han truncado ni sobrescrito."); return }
        state.update { s->s.copy(notes=s.notes?.received(n.nodeId,text)) }
        if(state.value.notes?.phase==NotesPhase.READY) { notesJob?.cancel(); notesWriteId=""; notesReads.clear() }
    }
    @Synchronized fun editNotes() { state.update { it.copy(notes=it.notes?.copy(editable=true)) } }
    @Synchronized fun changeNotes(text:String) {
        if(text.length>64000) { failNotes("Máximo 64000 caracteres. No se ha enviado ningún guardado."); return }
        state.update { it.copy(notes=it.notes?.edit(text)) }
    }
    @Synchronized fun saveNotes() {
        val n=state.value.notes ?: return
        if(!n.canSave) return
        if(!state.value.ready) { failNotes("Sin conexión. No se ha enviado el guardado."); return }
        notesEpoch++; notesJob?.cancel(); notesReads.clear()
        val epoch=notesEpoch
        notesWriteId=UUID.randomUUID().toString()
        state.update { it.copy(notes=it.notes?.saving()) }
        val sent=socket?.send(json("action" to "setNotes","id" to n.nodeId,"notes" to n.draft,"responseid" to notesWriteId).toString())==true
        if(!sent) { failNotes("No se pudo enviar el guardado. El borrador se conserva."); return }
        notesJob=scope.launch {
            repeat(6) { attempt->
                delay(500L+250L*attempt)
                synchronized(this@Management) {
                    if(epoch!=notesEpoch || state.value.notes?.phase!=NotesPhase.SAVING) return@launch
                    requestNotes(n.nodeId)
                }
            }
            delay(1000)
            synchronized(this@Management) {
                if(epoch==notesEpoch && state.value.notes?.phase==NotesPhase.SAVING)
                    failNotes("No se ha confirmado el guardado por relectura. Comprueba permisos y contenido antes de reintentar. El borrador local se conserva.")
            }
        }
    }
    @Synchronized private fun failNotes(message:String) {
        notesJob?.cancel(); notesWriteId=""; notesReads.clear()
        state.update { it.copy(notes=it.notes?.failed(message.take(600))) }
    }
    @Synchronized fun closeNotes() {
        notesEpoch++; notesJob?.cancel(); notesReads.clear(); notesWriteId=""
        state.update { it.copy(notes=null) }
    }
    @Synchronized fun clear() { pause(); feedback.clear(); activity.clear(); state.value=State() }
    override fun onCleared() { clear(); scope.cancel() }
}
private data class Operation(val title:String,val fields:List<Pair<String,String>>,val requiresAgent:Boolean=false,
    val icon:ImageVector=Icons.Default.Computer,val dangerous:Boolean=false,val directSend:Boolean=false,val build:(Map<String,String>)->JSONObject)

@Composable fun MeshFabricHome(viewModel:DeviceListViewModel,onLogout:()->Unit,onOpenSettings:()->Unit,
    onOpenDesktop:(String,String)->Unit,onOpenTerminal:(String,String)->Unit,onOpenFiles:(String,String)->Unit) {
    val factory=remember(viewModel.meshClient) { object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=Management(viewModel.meshClient,viewModel.desktopClient) as T
    } }
    val management=androidx.lifecycle.viewmodel.compose.viewModel<Management>(factory=factory)
    val state by management.state.collectAsState()
    val desktopSessions by viewModel.desktopSessionsStatus.collectAsState()
    val terminalSessions by viewModel.terminalSessionsStatus.collectAsState()
    val fileSessions by viewModel.fileSessionsStatus.collectAsState()
    val groups by viewModel.meshClient.groupedDevices.collectAsState()
    val connected by viewModel.isConnected.collectAsState()
    val nodes=groups.flatMap { it.second }.distinctBy { it.id }
    val snackbar=remember { SnackbarHostState() }
    val uiScope=rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var operation by remember { mutableStateOf<Operation?>(null) }
    var operationNode by remember { mutableStateOf("") }
    LaunchedEffect(connected) { if(connected) management.connect() else management.pause() }
    LaunchedEffect(nodes) { management.activity.rememberDevices(nodes.associate { it.id to it.name }) }
    Scaffold(contentWindowInsets=WindowInsets(0,0,0,0),snackbarHost={ SnackbarHost(snackbar) },bottomBar={ CompactBottomNavigation(page) { page=it } }) { padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if(page==0) CompositionLocalProvider(LocalDeviceActionMenu provides { node->
                val cleanNodeId = node.id.removePrefix("node//").removePrefix("node/").trim()
                val hasActiveSessions = iecxiv.meshfabric.ui.devices.hasDeviceConnectedSession(
                    desktopSessions[cleanNodeId] ?: iecxiv.meshfabric.data.model.SessionStatus.NONE,
                    terminalSessions[cleanNodeId] ?: iecxiv.meshfabric.data.model.SessionStatus.NONE,
                    fileSessions[cleanNodeId] ?: iecxiv.meshfabric.data.model.SessionStatus.NONE)
                DeviceActionMenu(node,state.ready,hasActiveSessions,
                    onDisconnectSessions={ viewModel.closeDeviceActiveSessions(node.id) },onRead={ management.openNotes(node,false) },onEdit={ management.openNotes(node,true) },
                    onSelect={ op->operationNode=node.id; operation=op.copy(title="${op.title} · ${node.name}") })
            }) {
                DeviceListScreen(viewModel,{ management.clear(); onLogout() },onOpenSettings,onOpenDesktop,onOpenTerminal,onOpenFiles)
            } else MeshFabricActivityScreen(viewModel=viewModel,journal=management.activity,feedback=management.feedback,
                actionsReady=state.ready,actionsStatus=state.status,onReconnect=management::connect,onOpenSettings=onOpenSettings)
        }
    }
    ActionFeedbackDialogs(management.feedback)
    state.notes?.let { n->DeviceNotesDialog(n,management::closeNotes,management::editNotes,management::changeNotes,management::saveNotes,
        onReload={ nodes.find { it.id==n.nodeId }?.let { management.openNotes(it,n.editable) } },connected=state.ready) }
    operation?.let { op->key(op.title,operationNode) {
        val targetOs=nodes.find { it.id==operationNode }?.osdesc
        val interpreters=commandInterpreters(targetOs)
        val singleConfirmation=!needsReviewStep(op.fields.size)
        var values by remember { mutableStateOf(op.fields.associate { it.first to if(it.first=="shell") defaultInterpreterKey(targetOs) else "" }) }
        var review by remember { mutableStateOf(singleConfirmation) }
        var error by remember { mutableStateOf("") }
        val available=nodes.any { it.id==operationNode && (!op.requiresAgent || it.conn and 1 != 0) }
        val confirmLabel=if(op.directSend) "Enviar" else if(singleConfirmation) op.title.substringBefore(" · ") else if(review) "Confirmar envío" else "Revisar"
        OperationFormDialog(op.fields.isNotEmpty(),op.title,{ operation=null },confirmLabel,enabled=state.ready && available,dangerous=op.dangerous,onConfirm={
            runCatching {
                if(op.fields.any { it.first=="shell" }) require(interpreters.any { it.key==values["shell"] }) { "Elige un intérprete válido para este equipo" }
                op.build(values)
            }.onSuccess { payload->
                error=""
                if(!review && !op.directSend) review=true else {
                    if(management.send(op.title,payload,true)) {
                        operation=null
                        if(!management.feedback.handles(payload)) uiScope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result=snackbar.showSnackbar(message="${op.title}: solicitud enviada; ejecución sin confirmar.",
                                actionLabel="Ver actividad",withDismissAction=true,duration=SnackbarDuration.Long)
                            if(result==SnackbarResult.ActionPerformed) page=1
                        }
                    } else error="No se ha podido enviar la solicitud. Revisa la conexión; no se reintentará automáticamente."
                }
            }.onFailure { error=it.message ?: "Revisa los valores" }
        }) {
            if(!state.ready || !available) Text("La conexión o el equipo no están disponibles para esta operación.")
            if(review) {
                Text(if(singleConfirmation) "¿Confirmas esta acción sobre el equipo indicado? Puede interrumpir sus sesiones remotas." else "Confirma la operación sobre el equipo indicado. El servidor validará tus permisos.")
                op.fields.forEach { (key,label)->
                    val display=if(key=="shell") interpreters.find { it.key==values[key] }?.label.orEmpty() else values[key].orEmpty()
                    Text("$label\n$display")
                }
            } else op.fields.forEach { (key,label)->
                if(key=="shell") {
                    if(interpreters.size==1) Text("Intérprete: ${interpreters.single().label}",style=MaterialTheme.typography.bodyMedium)
                    else {
                        if(devicePlatform(targetOs)==DevicePlatform.UNKNOWN) Text("Sistema no identificado: elige el intérprete del equipo remoto.",style=MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            interpreters.forEach { choice->FilterChip(selected=values[key]==choice.key,onClick={ values=values+(key to choice.key); error="" },label={ Text(choice.label) }) }
                        }
                    }
                } else OutlinedTextField(value=values[key].orEmpty(),
                    onValueChange={ values=values+(key to it); error="" },
                    label={ Text(label) },textStyle=MaterialTheme.typography.bodyLarge,
                    singleLine=key=="url" || key=="title",minLines=1,
                    maxLines=if(key=="url" || key=="title") 1 else 3,
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp,max=90.dp))
            }
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        }
    } }
}
@Composable private fun DeviceActionMenu(node:MeshNode,ready:Boolean,hasActiveSessions:Boolean,onDisconnectSessions:()->Unit,onRead:()->Unit,onEdit:()->Unit,onSelect:(Operation)->Unit) {
    var expanded by remember(node.id) { mutableStateOf(false) }
    val configuration=LocalConfiguration.current
    val density=LocalDensity.current
    val safeInsets=WindowInsets.safeDrawing
    val verticalInsets=with(density) { (safeInsets.getTop(this)+safeInsets.getBottom(this)).toDp() }
    val availableMenuHeight=(configuration.screenHeightDp.dp-verticalInsets-48.dp).coerceAtLeast(0.dp)
    val menuMaxHeight=availableMenuHeight
    val agentOnline=node.conn and 1 != 0
    val targets=JSONArray(listOf(node.id))
    val interpreters=commandInterpreters(node.osdesc)
    val actions=listOf(
        Operation("Ejecutar comando",listOf("cmds" to "Comando / script","shell" to "Intérprete"),true,Icons.Default.Terminal) { v->
            val interpreter=interpreters.find { it.key==v["shell"] } ?: error("Intérprete no válido para este equipo")
            require(v.getValue("cmds").isNotBlank()) { "Escribe un comando" }
            require(v.getValue("cmds").length<=64000) { "Máximo 64000 caracteres" }
            json("action" to "runcommands","nodeids" to targets,"type" to interpreter.type,"cmds" to v.getValue("cmds"),"runAsUser" to 0,"reply" to true)
        },
        Operation("Notificación remota",listOf("title" to "Título","msg" to "Mensaje"),true,Icons.Default.Notifications,directSend=true) { json("action" to "toast","nodeids" to targets,"title" to it.getValue("title"),"msg" to it.getValue("msg")) },
        Operation("Abrir URL remota",listOf("url" to "URL HTTP/HTTPS"),true,Icons.Default.OpenInNew) {
            val url=it.getValue("url").trim(); require(url.startsWith("https://") || url.startsWith("http://")) { "URL no válida" }
            json("action" to "msg","type" to "openUrl","nodeid" to node.id,"url" to url)
        },
        Operation("Despertar (WoL)",emptyList(),icon=Icons.Default.PowerSettingsNew) { json("action" to "wakedevices","nodeids" to targets) },
        Operation("Reiniciar",emptyList(),true,Icons.Default.Refresh,true) { json("action" to "poweraction","nodeids" to targets,"actiontype" to 3) },
        Operation("Apagar",emptyList(),true,Icons.Default.PowerSettingsNew,true) { json("action" to "poweraction","nodeids" to targets,"actiontype" to 2) },
        Operation("Suspender",emptyList(),true,Icons.Default.Pause,true) { json("action" to "poweraction","nodeids" to targets,"actiontype" to 4) }
    )
    val visibleActions=actions.filter { actionVisible(it.requiresAgent,agentOnline) }
    val (regularActions,powerActions)=visibleActions.partition { it.fields.isNotEmpty() }
    Box {
        IconButton(onClick={ expanded=true }) { Icon(Icons.Default.MoreVert,contentDescription="Acciones de ${node.name}",tint=MaterialTheme.colorScheme.onSurfaceVariant) }
        DropdownMenu(expanded=expanded,onDismissRequest={ expanded=false },modifier=Modifier.heightIn(max=menuMaxHeight),
            shape=MaterialTheme.shapes.medium,containerColor=if(node.isOnline) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            tonalElevation=2.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            if (hasActiveSessions) {
                MenuEntry(androidx.compose.ui.res.stringResource(iecxiv.meshfabric.R.string.device_disconnect_active_sessions), Icons.Default.LinkOff, true, true) {
                    expanded=false
                    onDisconnectSessions()
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            MenuEntry("Leer notas",Icons.Default.Description,ready) { expanded=false; onRead() }
            MenuEntry("Guardar notas",Icons.Default.Edit,ready) { expanded=false; onEdit() }
            if(regularActions.isNotEmpty()) {
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                regularActions.forEach { op->MenuEntry(op.title,op.icon,ready,op.dangerous) { expanded=false; onSelect(op) } }
            }
            if(powerActions.isNotEmpty()) {
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                powerActions.forEach { op->MenuEntry(op.title,op.icon,ready,op.dangerous) { expanded=false; onSelect(op) } }
            }
        }
    }
}
@Composable private fun MenuEntry(label:String,icon:ImageVector,enabled:Boolean,dangerous:Boolean=false,click:()->Unit) {
    val color=if(dangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(text={ Text(label,fontSize=14.sp,fontWeight=FontWeight.Medium) },leadingIcon={ Icon(icon,null,Modifier.size(20.dp)) },
        enabled=enabled,colors=MenuDefaults.itemColors(textColor=color,leadingIconColor=color),onClick=click)
}
