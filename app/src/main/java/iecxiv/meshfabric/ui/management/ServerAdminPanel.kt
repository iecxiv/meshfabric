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
    @Synchronized fun command(exact:String):Boolean {
        if(!available() || exact.isBlank() || exact.length>1000) return false
        state.update { it.copy(pending="console",token=it.token+1,notice="sent") }
        if(socket?.send(JSONObject().put("action","serverconsole").put("value",exact).toString())!=true) fail(generation)
        return true
    }
    @Synchronized fun apply(proposal:TraceProposal):Boolean {
        if(!available()) return false
        if(state.value.sources!=proposal.base || !validTraceSelection(proposal.base,proposal.desired)) { state.update { it.copy(notice="changed") }; return false }
        state.update { it.copy(pending="trace",token=it.token+1,desired=proposal.desired,notice="sent") }
        if(socket?.send(JSONObject().put("action","traceinfo").put("traceSources",JSONArray(proposal.desired.sorted())).toString())!=true) fail(generation)
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
    var command by remember { mutableStateOf("") }
    var commandProposal by remember { mutableStateOf<String?>(null) }
    var traceProposal by remember { mutableStateOf<TraceProposal?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var traceMenu by remember { mutableStateOf(false) }
    LaunchedEffect(channel,lifecycle,retry) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        try { channel?.connect(); awaitCancellation() } finally { channel?.close() }
    } }
    DisposableEffect(channel) { onDispose { channel?.close() } }
    LaunchedEffect(state.ready,retry) { if(!state.ready) { commandProposal=null; traceProposal=null; traceMenu=false; delay(20000); channel?.connectionTimeout() } }
    LaunchedEffect(state.token,state.pending) { if(state.pending.isNotEmpty()) { traceMenu=false; delay(25000); channel?.timeout(state.token) } }
    LaunchedEffect(state.sources) { selected=state.sources.orEmpty(); traceProposal=null; traceMenu=false }
    val enabled=state.ready && state.admin && state.pending.isEmpty()
    Text(label("Canal de administración independiente", "Independent administration channel"),style=MaterialTheme.typography.titleSmall)
    Text(when { state.failed->label("Sin conexión. Reconecta manualmente.","Disconnected. Reconnect manually."); !state.ready->label("Conectando…","Connecting…"); !state.admin->label("Se requiere administrador completo del servidor.","Full server administrator required."); else->label("Administrador conectado. El servidor valida también sus restricciones.","Administrator connected. Server restrictions still apply.") })
    if(state.failed) TextButton(onClick={ retry++ }) { Text(label("Reconectar","Reconnect")) }
    Text(label("Salida local limitada a 200 entradas y 64 000 caracteres. No se guarda en disco ni en diagnóstico; se borra al salir o pasar a segundo plano. Puede contener datos sensibles.","Local output limited to 200 entries and 64,000 characters. Not saved to disk or diagnostic logs; cleared on exit or background. May contain sensitive data."),style=MaterialTheme.typography.bodySmall)
    val notice=when(state.notice) {
        "sent"->label("Solicitud enviada; resultado pendiente.","Request sent; result pending.")
        "reply"->label("Respuesta recibida del servidor. Revisa su contenido.","Server reply received. Review its contents.")
        "confirmed"->label("El estado global recibido coincide con las fuentes solicitadas.","Received global state matches the requested sources.")
        "changed"->label("Las fuentes han cambiado. Revisa el estado actual antes de aplicar.","Sources changed. Review current state before applying.")
        "unknown"->label("Conexión interrumpida o sin respuesta. Resultado desconocido; no se reenviará la operación.","Interrupted or no response. Result unknown; the operation will not be resent.")
        else->""
    }
    if(notice.isNotBlank()) Text(notice,color=if(state.notice=="unknown" || state.notice=="changed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    if(!traceView) {
        OutlinedTextField(value=command,onValueChange={ if(it.length<=1000) command=it },label={ Text(label("Comando del servidor (no shell del dispositivo)","Server command (not device shell)")) },modifier=Modifier.fillMaxWidth(),maxLines=4)
        TextButton(onClick={ commandProposal=command },enabled=enabled && command.isNotBlank()) { Text(label("Revisar envío","Review send")) }
    } else {
        Text(label("Las fuentes de seguimiento son globales: afectan a otros administradores. Cerrar esta vista no envía una orden de desactivación.","Trace sources are global and affect other administrators. Closing this view sends no disable command."),style=MaterialTheme.typography.bodySmall)
        val current=state.sources
        Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label("Fuentes de rastreo","Trace sources"),style=MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick={ traceMenu=true },enabled=enabled && current!=null,modifier=Modifier.fillMaxWidth()) {
                        Text(label("Seleccionar fuentes (${selected.size})","Select sources (${selected.size})"),Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown,null,Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded=traceMenu && enabled && current!=null,onDismissRequest={ traceMenu=false },modifier=Modifier.heightIn(max=320.dp).widthIn(min=240.dp,max=360.dp),
                        shape=MaterialTheme.shapes.medium,containerColor=MaterialTheme.colorScheme.surfaceVariant,tonalElevation=2.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                        availableTraceOptions(current.orEmpty()).forEach { name->
                            DropdownMenuItem(text={ Column {
                                Text(traceSourceName(name,es),style=MaterialTheme.typography.bodyMedium)
                                if(traceSourceName(name,es)!=name) Text(name,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            } },leadingIcon={ Checkbox(checked=name in selected,onCheckedChange=null) },onClick={ selected=if(name in selected) selected-name else selected+name })
                        }
                    }
                }
                if(current==null) Text(label("Esperando fuentes actuales del servidor. No se modificarán hasta recibirlas.","Waiting for current server sources. No changes allowed until received."),style=MaterialTheme.typography.bodySmall) else {
                    Text(label("Activas en el servidor: ","Active on server: ")+current.sorted().joinToString { traceSourceName(it,es) }.ifBlank { label("ninguna","none") },style=MaterialTheme.typography.bodySmall)
                    if(selected!=current) Text(label("Selección sin aplicar: ","Unapplied selection: ")+selected.sorted().joinToString { traceSourceName(it,es) }.ifBlank { label("ninguna","none") },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                    TextButton(onClick={ traceMenu=false; traceProposal=TraceProposal(current,selected.toSet()) },enabled=enabled && selected!=current) { Text(label("Revisar cambios globales","Review global changes")) }
                    TextButton(onClick={ traceMenu=false; traceProposal=TraceProposal(current,emptySet()) },enabled=enabled && current.isNotEmpty()) { Text(label("Desactivar todas…","Disable all…")) }
                }
            }
        }
    }
    if(state.pending.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
    TextButton(onClick={ channel?.clear(traceView) }) { Text(label("Limpiar solo la salida local","Clear local output only")) }
    val output=if(traceView) state.trace else state.console
    if(traceView) Surface(modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        SelectionContainer {
            Column(Modifier.fillMaxWidth().heightIn(max=320.dp).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(output.isEmpty()) Text(label("Sin salida recibida.","No output received."),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                output.forEachIndexed { index,entry->
                    Text(entry,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                    if(index<output.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    } else SelectionContainer { Text(output.joinToString("\n").ifBlank { label("Sin salida recibida.","No output received.") },Modifier.fillMaxWidth().heightIn(max=320.dp).verticalScroll(rememberScrollState()),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall) }
    commandProposal?.let { exact->AlertDialog(onDismissRequest={ commandProposal=null },title={ Text(label("Confirmar comando del servidor","Confirm server command")) },
        text={ Column { Text(source?.serverUrl.orEmpty()); Text(label("Puede modificar el servidor o reiniciarlo. No se reintentará automáticamente.","May modify or restart the server. No automatic retry.")); SelectionContainer { Text(exact,fontFamily=FontFamily.Monospace) } } },
        confirmButton={ TextButton(enabled=enabled,onClick={ commandProposal=null; if(channel?.command(exact)==true) command="" }) { Text(label("Enviar","Send")) } },dismissButton={ TextButton(onClick={ commandProposal=null }) { Text(label("Cancelar","Cancel")) } }) }
    traceProposal?.let { proposal->AlertDialog(onDismissRequest={ traceProposal=null },title={ Text(label("Cambiar seguimiento global","Change global tracing")) },
        text={ Column { Text(source?.serverUrl.orEmpty()); Text(label("Esto reemplaza las fuentes para todo el servidor.","This replaces sources for the entire server.")); Text(label("Actuales: ","Current: ")+proposal.base.sorted().joinToString { traceSourceName(it,es)+" ("+it+")" }.ifBlank { "—" }); Text(label("Nuevas: ","New: ")+proposal.desired.sorted().joinToString { traceSourceName(it,es)+" ("+it+")" }.ifBlank { label("ninguna: desactivar todas","none: disable all") }) } },
        confirmButton={ TextButton(enabled=enabled,onClick={ traceProposal=null; channel?.apply(proposal) }) { Text(label("Aplicar globalmente","Apply globally")) } },dismissButton={ TextButton(onClick={ traceProposal=null }) { Text(label("Cancelar","Cancel")) } }) }
}
