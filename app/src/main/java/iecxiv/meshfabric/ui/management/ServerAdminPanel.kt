package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject

internal fun fullServerAdmin(rights:Long)=rights==-1L || rights==4294967295L
internal fun boundedAdminOutput(previous:List<String>,text:String):List<String> {
    val entry=if(text.length>16000) text.take(16000)+"\n[… truncated]" else text
    val result=(previous+entry).takeLast(200).toMutableList()
    var size=result.sumOf { it.length }
    while(size>64000 && result.size>1) size-=result.removeAt(0).length
    return result
}
internal val serverTraceSources=listOf("cookie","dispatch","main","peer","agent","agentupdate","cert","db","email","web","webrequest","relay","httpheaders","authlog","amt","webrelay","mps","mpscmd")
internal fun validTraceSelection(current:Set<String>,desired:Set<String>)=desired.all { it in serverTraceSources || it in current }
private data class AdminState(val ready:Boolean=false,val admin:Boolean=false,val failed:Boolean=false,val sources:Set<String>?=null,
    val console:List<String> = emptyList(),val trace:List<String> = emptyList(),val pending:String="",val token:Long=0,val desired:Set<String> = emptySet(),val notice:String="")
private data class TraceProposal(val base:Set<String>,val desired:Set<String>)
private class ServerAdminChannel(private val source:MeshCentralClient) {
    val state=MutableStateFlow(AdminState())
    private var socket:WebSocket?=null
    private var generation=0L
    private var identity:Triple<String,String,Boolean>?=null
    private fun currentIdentity()=Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
    @Synchronized fun connect() {
        close()
        if(source.isUserLogout || source.authCookie.isBlank()) { state.value=AdminState(failed=true,notice="connection"); return }
        identity=currentIdentity()
        val epoch=generation
        try {
            socket=OkHttpHelper.createClient(source.ignoreSsl).newWebSocket(Request.Builder()
                .url(OkHttpHelper.toWebSocketUrl(source.serverUrl,"/control.ashx?moreargs=1"))
                .header("Cookie",source.authCookie).build(),object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) { synchronized(this@ServerAdminChannel) {
                    if(epoch!=generation) { webSocket.cancel(); return }
                    webSocket.send(JSONObject().put("action","urlargs").put("args",JSONObject().put("auth",source.extractRawToken(source.authCookie))).toString())
                    webSocket.send("{\"action\":\"userinfo\"}")
                } }
                override fun onMessage(webSocket:WebSocket,text:String) { synchronized(this@ServerAdminChannel) {
                    if(epoch!=generation) return
                    if(text.length>2000000) { fail(epoch); return }
                    val packet=runCatching { JSONObject(text) }.getOrNull() ?: return
                    when(packet.optString("action")) {
                        "ping"->webSocket.send("{\"action\":\"pong\"}")
                        "userinfo"->packet.optJSONObject("userinfo")?.let { user->
                            if(user.optString("_id").isNotBlank()) state.update { it.copy(ready=true,admin=fullServerAdmin(user.optLong("siteadmin",0)),failed=false) }
                        }
                        "serverconsole"->if(state.value.admin && packet.has("value")) state.update {
                            it.copy(console=boundedAdminOutput(it.console,packet.optString("value")),pending=if(it.pending=="console") "" else it.pending,notice=if(it.pending=="console") "reply" else it.notice)
                        }
                        "traceinfo"->receiveSources(packet)
                        "event"->packet.optJSONObject("event")?.let { if(it.optString("action")=="traceinfo") receiveSources(it) }
                        "trace"->if(state.value.admin) state.update { it.copy(trace=boundedAdminOutput(it.trace,readableServerTrace(packet.toString()))) }
                        "close","stopped"->fail(epoch)
                    }
                } }
                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,"") }
                override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { fail(epoch) }
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { fail(epoch) }
            })
        } catch(_:Exception) { fail(epoch) }
    }
    private fun receiveSources(packet:JSONObject) {
        val array=packet.optJSONArray("traceSources") ?: return
        if(array.length()>256) return
        val sources=(0 until array.length()).mapNotNull { array.optString(it).takeIf { name->name.isNotBlank() && name.length<=100 } }.toSet()
        state.update { it.copy(sources=sources,pending=if(it.pending=="trace") "" else it.pending,
            notice=if(it.pending=="trace") { if(sources==it.desired) "confirmed" else "changed" } else it.notice) }
    }
    private fun available()=state.value.ready && state.value.admin && state.value.pending.isEmpty() && !source.isUserLogout && identity==currentIdentity()
    @Synchronized fun canCommand()=available()
    @Synchronized fun command(exact:String):Boolean {
        if(!available() || !validServerConsoleCommand(exact)) return false
        state.update { it.copy(pending="console",token=it.token+1,notice="sent") }
        if(socket?.send(JSONObject().put("action","serverconsole").put("value",exact).toString())!=true) { fail(generation); return false }
        state.update { it.copy(console=boundedAdminOutput(it.console,"> "+exact)) }
        return true
    }
    @Synchronized fun apply(proposal:TraceProposal):Boolean {
        if(!available()) return false
        if(state.value.sources!=proposal.base || !validTraceSelection(proposal.base,proposal.desired)) { state.update { it.copy(notice="changed") }; return false }
        if(proposal.base==proposal.desired) return false
        state.update { it.copy(pending="trace",token=it.token+1,desired=proposal.desired,notice="sent") }
        if(socket?.send(JSONObject().put("action","traceinfo").put("traceSources",JSONArray(proposal.desired.sorted())).toString())!=true) { fail(generation); return false }
        return true
    }
    @Synchronized fun timeout(token:Long) { if(state.value.pending.isNotEmpty() && state.value.token==token) fail(generation) }
    @Synchronized fun connectionTimeout() { if(!state.value.ready && !state.value.failed) fail(generation) }
    @Synchronized private fun fail(epoch:Long) {
        if(epoch!=generation) return
        generation++; val old=socket; socket=null
        state.update { it.copy(ready=false,admin=false,failed=true,pending="",sources=null,notice=if(it.pending.isNotEmpty()) "unknown" else "connection") }
        old?.cancel()
    }
    @Synchronized fun clear(trace:Boolean) { state.update { if(trace) it.copy(trace=emptyList()) else it.copy(console=emptyList()) } }
    @Synchronized fun close() { generation++; val old=socket; socket=null; identity=null; state.value=AdminState(); old?.cancel() }
}

@Composable internal fun ServerAdminPanel(source:MeshCentralClient?,traceView:Boolean) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun label(spanish:String,english:String)=if(es) spanish else english
    val channel=remember(source) { source?.let { ServerAdminChannel(it) } }
    val state=channel?.state?.collectAsState()?.value ?: AdminState(failed=true,notice="connection")
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var retry by remember { mutableIntStateOf(0) }
    var traceMenu by remember { mutableStateOf(false) }
    LaunchedEffect(channel,lifecycle,retry) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        try { channel?.connect(); awaitCancellation() } finally { channel?.close() }
    } }
    DisposableEffect(channel) { onDispose { channel?.close() } }
    LaunchedEffect(state.ready,retry) { if(!state.ready) { traceMenu=false; delay(20000); channel?.connectionTimeout() } }
    LaunchedEffect(state.token,state.pending) { if(state.pending.isNotEmpty()) { delay(25000); channel?.timeout(state.token) } }
    val enabled=state.ready && state.admin && state.pending.isEmpty()
    Text(label("Canal de administración independiente", "Independent administration channel"),style=MaterialTheme.typography.titleSmall)
    Text(when { state.failed->label("Sin conexión. Reconecta manualmente.","Disconnected. Reconnect manually."); !state.ready->label("Conectando…","Connecting…"); !state.admin->label("Se requiere administrador completo del servidor.","Full server administrator required."); else->label("Administrador conectado. El servidor valida también sus restricciones.","Administrator connected. Server restrictions still apply.") })
    if(state.failed) TextButton(onClick={ retry++ }) { Text(label("Reconectar","Reconnect")) }
    val privacy=label("Salida local limitada a 200 entradas y 64 000 caracteres. No se guarda en disco ni en diagnóstico; se borra al salir o pasar a segundo plano. Puede contener datos sensibles.","Local output limited to 200 entries and 64,000 characters. Not saved to disk or diagnostic logs; cleared on exit or background. May contain sensitive data.")
    val notice=when(state.notice) {
        "sent"->label("Solicitud enviada; resultado pendiente.","Request sent; result pending.")
        "reply"->label("Respuesta recibida del servidor. Revisa su contenido.","Server reply received. Review its contents.")
        "confirmed"->label("El estado global recibido coincide con las fuentes solicitadas.","Received global state matches the requested sources.")
        "changed"->label("Las fuentes han cambiado. Se muestra el estado recibido del servidor.","Sources changed. Showing the state received from the server.")
        "unknown"->label("Conexión interrumpida o sin respuesta. Resultado desconocido; no se reenviará la operación.","Interrupted or no response. Result unknown; the operation will not be resent.")
        else->""
    }
    if(!traceView) {
        ServerConsoleTerminal(state.console,onClear={ channel?.clear(false) },enabled=enabled,canSend={ channel?.canCommand()==true },onCommand={ channel?.command(it)==true })
        Text(label("Enter envía el comando directamente, sin revisión ni confirmación. Puede modificar el servidor o reiniciarlo. No se reintentará automáticamente.","Enter sends the command directly, without review or confirmation. It may modify or restart the server. No automatic retry."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(privacy,style=MaterialTheme.typography.bodySmall)
        if(notice.isNotBlank()) Text(notice,color=if(state.notice=="unknown" || state.notice=="changed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(privacy,style=MaterialTheme.typography.bodySmall)
        if(notice.isNotBlank()) Text(notice,color=if(state.notice=="unknown" || state.notice=="changed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label("Marcar o desmarcar una fuente aplica el cambio directamente, sin revisión ni confirmación. Las fuentes son globales y afectan a otros administradores. Cerrar esta vista no las desactiva.","Checking or unchecking a source applies the change directly, without review or confirmation. Sources are global and affect other administrators. Closing this view does not disable them."),style=MaterialTheme.typography.bodySmall)
        val current=state.sources
        val options=availableTraceOptions(current.orEmpty())
        val allSelected=options.isNotEmpty() && options.all { it in current.orEmpty() }
        val allState=when { allSelected->ToggleableState.On; current.isNullOrEmpty()->ToggleableState.Off; else->ToggleableState.Indeterminate }
        Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label("Fuentes de rastreo","Trace sources"),style=MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick={ traceMenu=true },enabled=enabled && current!=null,modifier=Modifier.fillMaxWidth()) {
                        Text(label("Seleccionar fuentes (${current?.size ?: 0})","Select sources (${current?.size ?: 0})"),Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown,null,Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded=traceMenu && state.ready && state.admin && current!=null,onDismissRequest={ traceMenu=false },modifier=Modifier.heightIn(max=320.dp).widthIn(min=240.dp,max=360.dp),
                        shape=MaterialTheme.shapes.medium,containerColor=MaterialTheme.colorScheme.surfaceVariant,tonalElevation=2.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        DropdownMenuItem(enabled=enabled && current!=null,text={ Text(if(allSelected) label("Desmarcar todas","Uncheck all") else label("Marcar todas","Check all")) },
                            leadingIcon={ TriStateCheckbox(state=allState,onClick=null,enabled=enabled) },onClick={
                                if(enabled && current!=null) channel?.apply(TraceProposal(current,if(allSelected) emptySet() else options.toSet()))
                            })
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        if(state.pending=="trace") {
                            Text(label("Aplicando cambios…","Applying changes…"),Modifier.padding(horizontal=16.dp,vertical=8.dp),style=MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        options.forEach { name->
                            DropdownMenuItem(enabled=enabled,text={ Column {
                                Text(traceSourceName(name,es),style=MaterialTheme.typography.bodyMedium)
                                if(traceSourceName(name,es)!=name) Text(name,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            } },leadingIcon={ Checkbox(checked=name in current.orEmpty(),onCheckedChange=null) },onClick={
                                if(enabled && current!=null) {
                                    val desired=if(name in current) current-name else current+name
                                    channel?.apply(TraceProposal(current,desired))
                                }
                            })
                        }
                    }
                }
                if(current==null) Text(label("Esperando fuentes actuales del servidor. No se modificarán hasta recibirlas.","Waiting for current server sources. No changes allowed until received."),style=MaterialTheme.typography.bodySmall) else {
                    Text(label("Activas en el servidor: ","Active on server: ")+current.sorted().joinToString { traceSourceName(it,es) }.ifBlank { label("ninguna","none") },style=MaterialTheme.typography.bodySmall)
                    if(state.pending=="trace") Text(label("Cambio enviado; esperando el estado del servidor. Las casillas muestran el último estado recibido.","Change sent; waiting for server state. Checkboxes show the last received state."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if(state.pending.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(traceView) {
        TextButton(onClick={ channel?.clear(true) }) { Text(label("Limpiar solo la salida local","Clear local output only")) }
        val output=remember(state.trace) { state.trace.asReversed() }
        Text(label("Más recientes primero","Newest received first"),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            SelectionContainer {
                Column(Modifier.fillMaxWidth().heightIn(max=320.dp).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(output.isEmpty()) Text(label("Sin salida recibida.","No output received."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    output.forEachIndexed { index,entry->
                        Text(entry,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                        if(index<output.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
