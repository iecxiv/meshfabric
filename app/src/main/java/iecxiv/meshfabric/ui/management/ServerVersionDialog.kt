package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

private data class VersionIdentity(val url:String,val cookie:String,val ignoreSsl:Boolean) {
    fun matches(source:MeshCentralClient)=!source.isUserLogout && url==source.serverUrl && cookie==source.authCookie && ignoreSsl==source.ignoreSsl
    val display:String get()=url.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.query(null)?.fragment(null)?.build()?.toString() ?: "—"
    override fun toString()="VersionIdentity"
}
private data class VersionProposal(val base:ServerVersionTags,val channel:String,val version:String)
private data class VersionState(val ready:Boolean=false,val allowed:Boolean=false,val tags:ServerVersionTags?=null,val pending:String="",val sequence:Long=0,val attempted:Boolean=false,val notice:String="loading")
private class ServerVersionChannel(private val source:MeshCentralClient) {
    private val identity=VersionIdentity(source.serverUrl,source.authCookie,source.ignoreSsl)
    val serverLabel get()=identity.display
    val insecureCertificate get()=identity.ignoreSsl
    val state=MutableStateFlow(VersionState())
    private var socket:WebSocket?=null
    private var generation=0L
    private var sequence=0L
    private var reviewed:VersionProposal?=null
    private var attempted=false
    private fun available()=identity.matches(source) && state.value.ready && state.value.allowed && state.value.pending.isEmpty() && !attempted
    private fun pending(kind:String,notice:String) { state.update { it.copy(pending=kind,sequence=++sequence,notice=notice) } }
    @Synchronized fun connect() {
        if(attempted) return
        close()
        if(!identity.matches(source) || identity.cookie.isBlank()) { state.value=VersionState(notice="session"); return }
        if(identity.url.toHttpUrlOrNull()?.scheme!="https") { state.value=VersionState(notice="https"); return }
        val epoch=generation
        pending("auth","loading")
        try {
            socket=OkHttpHelper.createClient(identity.ignoreSsl).newWebSocket(Request.Builder()
                .url(OkHttpHelper.toWebSocketUrl(identity.url,"/control.ashx?moreargs=1"))
                .header("Cookie",identity.cookie).build(),object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) { synchronized(this@ServerVersionChannel) {
                    if(epoch!=generation || !identity.matches(source)) { webSocket.cancel(); return }
                    webSocket.send(JSONObject().put("action","urlargs").put("args",JSONObject().put("auth",source.extractRawToken(identity.cookie))).toString())
                    webSocket.send("{\"action\":\"userinfo\"}")
                } }
                override fun onMessage(webSocket:WebSocket,text:String) { synchronized(this@ServerVersionChannel) {
                    if(epoch!=generation) return
                    if(text.length>1000000) { fail(epoch,"invalid"); return }
                    val packet=runCatching { JSONObject(text) }.getOrNull() ?: return
                    when(packet.optString("action")) {
                        "ping"->webSocket.send("{\"action\":\"pong\"}")
                        "userinfo"->{
                            val user=packet.optJSONObject("userinfo") ?: return
                            if(user.optString("_id").isBlank() || !identity.matches(source)) { fail(epoch,"session"); return }
                            val allowed=serverUpdateAllowed(user.optLong("siteadmin",0L))
                            if(!allowed) { fail(epoch,"denied"); return }
                            val wasReady=state.value.ready
                            state.update { it.copy(ready=true,allowed=true) }
                            if(state.value.pending=="permission") sendReviewed(webSocket,epoch)
                            else if(!wasReady) query(webSocket,epoch)
                        }
                        "serverversion"->receiveVersions(text,epoch)
                        "serverupdate"->if(attempted) {
                            val result=packet.optString("result")
                            val rejected=packet.optString("error").isNotBlank() || (result.isNotBlank() && !result.equals("OK",true))
                            state.update { it.copy(pending="",notice=if(rejected) "rejected" else "reply") }
                        }
                        "close","stopped"->fail(epoch,"connection")
                        else->if(packet.has("tags") && packet.optString("result").equals("OK",true)) receiveVersions(text,epoch)
                    }
                } }
                override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,"") }
                override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { fail(epoch,"connection") }
                override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { fail(epoch,"connection") }
            })
        } catch(_:Exception) { fail(epoch,"connection") }
    }
    private fun query(webSocket:WebSocket,epoch:Long) {
        pending("query","loading")
        if(!webSocket.send("{\"action\":\"serverversion\"}")) fail(epoch,"connection")
    }
    private fun receiveVersions(raw:String,epoch:Long) {
        if(state.value.pending!="query" || attempted || !state.value.allowed) return
        val tags=serverVersionTags(raw)
        if(tags==null) { fail(epoch,"invalid"); return }
        state.update { it.copy(tags=tags,pending="",notice="versions") }
    }
    @Synchronized fun refresh() { if(available()) socket?.let { query(it,generation) } }
    @Synchronized fun requestUpdate(proposal:VersionProposal):Boolean {
        if(!available() || state.value.tags!=proposal.base || !mayUpdateServer(proposal.base,proposal.version)) return false
        reviewed=proposal
        pending("permission","checking")
        if(socket?.send("{\"action\":\"userinfo\"}")!=true) fail(generation,"connection")
        return true
    }
    private fun sendReviewed(webSocket:WebSocket,epoch:Long) {
        val proposal=reviewed
        reviewed=null
        if(proposal==null || attempted || !identity.matches(source) || state.value.tags!=proposal.base || !mayUpdateServer(proposal.base,proposal.version)) { fail(epoch,"changed"); return }
        attempted=true
        state.update { it.copy(attempted=true) }
        pending("update","sent")
        if(!webSocket.send(serverUpdateRequest(proposal.version))) fail(epoch,"connection")
    }
    @Synchronized fun timeout(token:Long) { if(state.value.pending.isNotEmpty() && state.value.sequence==token) fail(generation,"timeout") }
    @Synchronized private fun fail(epoch:Long,notice:String) {
        if(epoch!=generation) return
        generation++; val old=socket; socket=null; reviewed=null
        state.update { it.copy(ready=false,allowed=false,pending="",notice=if(attempted && it.notice!="rejected") "unknown" else notice) }
        old?.cancel()
    }
    @Synchronized fun close() {
        generation++; val old=socket; socket=null; reviewed=null
        state.value=if(attempted) state.value.copy(ready=false,allowed=false,pending="",notice=if(state.value.notice=="rejected") "rejected" else "unknown") else VersionState()
        old?.cancel()
    }
}

@Composable internal fun ServerVersionDialog(source:MeshCentralClient?,onClose:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun label(a:String,b:String)=if(es) a else b
    val channel=remember(source) { source?.let { ServerVersionChannel(it) } }
    val state=channel?.state?.collectAsState()?.value ?: VersionState(notice="session")
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var retry by remember { mutableIntStateOf(0) }
    var proposal by remember { mutableStateOf<VersionProposal?>(null) }
    LaunchedEffect(channel,lifecycle,retry) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        try { channel?.connect(); awaitCancellation() } finally { channel?.close() }
    } }
    DisposableEffect(channel) { onDispose { channel?.close() } }
    LaunchedEffect(state.pending,state.sequence) { if(state.pending.isNotBlank()) { delay(25000); channel?.timeout(state.sequence) } }
    LaunchedEffect(state.ready,state.tags,state.attempted) { if(!state.ready || state.attempted || proposal?.base!=state.tags) proposal=null }
    val enabled=state.ready && state.allowed && state.pending.isEmpty() && !state.attempted
    val notice=when(state.notice) {
        "loading"->label("Consultando versiones y permisos…","Checking versions and permission…")
        "checking"->label("Comprobando nuevamente el permiso antes del envío…","Checking permission again before sending…")
        "sent"->label("Solicitud puesta en cola. Instalación y reinicio SIN confirmar.","Request queued. Installation and restart NOT confirmed.")
        "reply"->label("Respuesta recibida. No confirma que la instalación ni el reinicio hayan finalizado correctamente.","Reply received. It does not confirm installation or restart completed successfully.")
        "unknown"->label("Resultado desconocido. La solicitud pudo llegar al servidor. No se reenviará. Comprueba el servidor antes de repetirla.","Result unknown. The request may have reached the server. It will not be resent. Check the server before repeating it.")
        "rejected"->label("El servidor ha rechazado la solicitud. No se reenviará.","Server rejected the request. It will not be resent.")
        "denied"->label("Se requiere permiso de actualización del servidor.","Server update permission required.")
        "session"->label("Sesión no disponible o cambiada. Cierra y vuelve a abrir el diálogo.","Session unavailable or changed. Close and reopen this dialog.")
        "https"->label("La actualización requiere una conexión HTTPS.","Updating requires an HTTPS connection.")
        "invalid"->label("No se recibió una respuesta de versiones válida.","No valid version response received.")
        "changed"->label("La sesión o las versiones cambiaron; no se envió la actualización.","Session or versions changed; update was not sent.")
        "timeout"->label("Sin respuesta durante la consulta. No se envió ninguna actualización.","No response to the query. No update was sent.")
        "connection"->label("No se pudo consultar el servidor. No se envió ninguna actualización.","Could not query the server. No update was sent.")
        else->""
    }
    iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=onClose,title={ ServerGeneralActionTitle(stringResource(R.string.my_server_version)) },text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(channel?.serverLabel ?: "—",style=MaterialTheme.typography.bodySmall)
            if(notice.isNotEmpty()) Text(notice,style=MaterialTheme.typography.bodySmall,color=if(state.notice in setOf("unknown","rejected","denied","session","https","invalid","changed","connection","timeout")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.pending.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(label("Versión actual","Current version"),style=MaterialTheme.typography.labelLarge)
                    Text(state.tags?.current ?: label("No disponible","Unavailable"),style=MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    listOf("stable" to state.tags?.stable,"latest" to state.tags?.latest).forEach { (tag,version)->
                        Text(if(tag=="stable") label("Versión estable","Stable version") else label("Última versión","Latest version"),style=MaterialTheme.typography.labelLarge)
                        Text(version ?: label("No disponible","Unavailable"),style=MaterialTheme.typography.bodyMedium)
                        val tags=state.tags
                        if(version!=null && tags!=null && version==tags.current) Text(label("Ya instalada","Already installed"),style=MaterialTheme.typography.bodySmall)
                        TextButton(enabled=enabled && version!=null && tags!=null && mayUpdateServer(tags,version),onClick={ if(version!=null && tags!=null) proposal=VersionProposal(tags,tag,version) }) {
                            Text(if(tag=="stable") label("Actualizar a estable…","Update to stable…") else label("Actualizar a última…","Update to latest…"))
                        }
                    }
                }
            }
            if(channel?.insecureCertificate==true) Text(label("La validación del certificado TLS está desactivada en tu conexión.","TLS certificate validation is disabled for your connection."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(label("Actualizar puede instalar una versión anterior, reiniciar el servidor e interrumpir sesiones. Comprueba tu copia de seguridad. Cerrar este diálogo no revierte una solicitud enviada.","Updating may install an older version, restart the server and interrupt sessions. Check your backup. Closing this dialog cannot undo a sent request."),style=MaterialTheme.typography.bodySmall)
            TextButton(enabled=enabled,onClick={ channel?.refresh() }) { Text(label("Volver a consultar","Check again")) }
            if(!state.ready && !state.attempted && state.pending.isEmpty() && state.notice in setOf("connection","timeout","invalid")) TextButton(onClick={ retry++ }) { Text(label("Reconectar para consultar","Reconnect to check")) }
        }
    },confirmButton={ TextButton(onClick=onClose) { Text(stringResource(R.string.my_server_close)) } })
    proposal?.let { exact->iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ proposal=null },title={ Text(label("Confirmar actualización del servidor","Confirm server update")) },text={
        Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(channel?.serverLabel ?: "—")
            Text(label("Actual: ","Current: ")+exact.base.current.orEmpty())
            Text((if(exact.channel=="stable") label("Estable: ","Stable: ") else label("Última: ","Latest: "))+exact.version)
            Text(label("Se solicitará exactamente esta versión, no un alias móvil. Puede ser anterior a la instalada. El servidor puede reiniciarse y cerrar sesiones. No habrá reintento automático.","Exactly this version will be requested, not a moving alias. It may be older than the installed version. The server may restart and close sessions. No automatic retry."),color=MaterialTheme.colorScheme.error)
            SelectionContainer { Text(serverUpdateRequest(exact.version),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall) }
        }
    },confirmButton={ TextButton(enabled=enabled && state.tags==exact.base,onClick={ proposal=null; channel?.requestUpdate(exact) }) { Text(label("Actualizar a ","Update to ")+exact.version) } },dismissButton={ TextButton(onClick={ proposal=null }) { Text(label("Cancelar","Cancel")) } }) }
}
