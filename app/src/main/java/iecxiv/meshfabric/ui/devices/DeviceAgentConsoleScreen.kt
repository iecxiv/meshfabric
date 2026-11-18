package iecxiv.meshfabric.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient

internal val LocalDeviceAgentConsoleOpen=staticCompositionLocalOf<((MeshNode)->Unit)?> {null}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DeviceAgentConsoleScreen(source:MeshCentralClient,nodeId:String,nodeName:String,onBack:()->Unit) {
    val user=source.userInfo.collectAsState().value
    val actor=user?.id.orEmpty()
    val siteadmin=user?.siteadmin ?: 0
    val connected by source.connectionState.collectAsState()
    val groups by source.groupedDevices.collectAsState()
    val cleanNodeId=nodeId.removePrefix("node//").removePrefix("node/").trim()
    val agentVersion=groups.asSequence().flatMap {it.second.asSequence()}.singleOrNull {it.id.removePrefix("node//").removePrefix("node/").trim()==cleanNodeId}
        ?.agent?.ver?.takeIf {it>0}?.toString()
    val identity=Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val context=LocalContext.current
    key(source,identity,actor,nodeId) {
        val session=remember {DeviceAgentConsoleSession(source,nodeId)}
        val state by session.state.collectAsState()
        val owner=LocalLifecycleOwner.current
        var versionRequested by remember {mutableStateOf(false)}
        var reportedVersion by remember {mutableStateOf<String?>(null)}
        LaunchedEffect(session,state.phase) {
            if(state.phase=="closed") versionRequested=false
            else if(state.phase=="ready" && !versionRequested) {
                versionRequested=true
                session.submit("versions")
            }
        }
        LaunchedEffect(state.lines) {
            state.lines.asReversed().firstNotNullOfOrNull {line->if(line.kind=="output") agentConsoleBuildVersion(line.text) else null}?.let {reportedVersion=it}
        }
        var draft by remember {mutableStateOf("")}
        var query by remember {mutableStateOf("")}
        var searching by remember {mutableStateOf(false)}
        var menu by remember {mutableStateOf(false)}
        var history by remember {mutableStateOf(false)}
        var clear by remember {mutableStateOf(false)}
        var leave by remember {mutableStateOf(false)}
        var actions by remember {mutableStateOf(false)}
        var review by remember {mutableStateOf<AgentConsoleAction?>(null)}
        var follow by remember {mutableStateOf(true)}
        var copyStatus by remember {mutableStateOf("")}
        val scroll=rememberScrollState()
        val rights=state.rights
        fun actionEnabled(a:AgentConsoleAction)=connected && state.phase=="ready" && rights!=null && agentActionAllowed(a,rights,siteadmin)
        DisposableEffect(session,owner) {
            var initialStarted=false
            fun initial() {if(!initialStarted) {initialStarted=true;session.connect()}}
            val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME) initial()}
            owner.lifecycle.addObserver(observer)
            if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) initial()
            onDispose {owner.lifecycle.removeObserver(observer);session.close()}
        }
        val allText=remember(state.lines,es) {state.lines.joinToString("\n") {line->when(line.kind) {
            "queued"->"> ${line.text}\n"+t("[local] Encolado; ejecución sin confirmar.","[local] Queued; execution unconfirmed.")
            "unknown"->"> ${line.text}\n"+t("[local] Envío incierto; no se repetirá automáticamente.","[local] Send uncertain; no automatic replay.")
            "not_sent"->"> ${line.text}\n"+t("[local] No enviado.","[local] Not sent.")
            else->line.text
        }}}
        val visible=remember(allText,query) {if(query.isBlank()) allText else allText.lineSequence().filter {it.contains(query,ignoreCase=true)}.joinToString("\n")}
        LaunchedEffect(state.lines,query,follow) {if(follow && query.isBlank()) {withFrameNanos {};scroll.scrollTo(scroll.maxValue)}}
        LaunchedEffect(scroll.value,scroll.isScrollInProgress) {if(scroll.isScrollInProgress && scroll.value<scroll.maxValue) follow=false}
        val exit:()->Unit={if(draft.isNotEmpty() || state.phase=="checking_send") leave=true else {session.disconnect();onBack()}}
        BackHandler(onBack=exit)
        val canSend=state.phase=="ready" && connected && agentConsoleCommandValid(draft)
        val submit:()->Unit={if(canSend && session.submit(draft)) {draft="";follow=true}}
        Scaffold(modifier=Modifier.fillMaxSize().safeDrawingPadding().imePadding(),contentWindowInsets=WindowInsets(0,0,0,0),topBar={TopAppBar(
            title={Column {Text(nodeName,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t("Consola del agente","Agent console"),style=MaterialTheme.typography.labelSmall)}},
            navigationIcon={IconButton(onClick=exit) {Icon(Icons.AutoMirrored.Filled.ArrowBack,t("Volver","Back"))}},actions={Box {
                IconButton(onClick={menu=true},modifier=Modifier.testTag("agent_console_menu")) {Icon(Icons.Default.MoreVert,t("Herramientas de consola","Console tools"))}
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text(t("Acciones del agente…","Agent actions…"))},enabled=state.phase=="ready" && connected,onClick={actions=true;menu=false},modifier=Modifier.testTag("agent_console_actions"))
                    HorizontalDivider()
                    DropdownMenuItem(text={Text(t("Filtrar salida local","Filter local output"))},onClick={searching=!searching;if(!searching) query="";menu=false})
                    DropdownMenuItem(text={Text(t("Copiar salida visible","Copy visible output"))},enabled=visible.isNotEmpty(),onClick={
                        menu=false;copyStatus=if(runCatching {val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: error("Clipboard unavailable");clipboard.setPrimaryClip(ClipData.newPlainText("Agent console",visible))}.isSuccess) t("Salida copiada; puede contener datos sensibles.","Output copied; it may contain sensitive data.") else t("No se pudo copiar.","Copy failed.")
                    })
                    DropdownMenuItem(text={Text(t("Limpiar salida local","Clear local output"))},onClick={clear=true;menu=false})
                    HorizontalDivider()
                    DropdownMenuItem(text={Text(t("Reconectar consola","Reconnect console"))},enabled=connected && state.phase=="closed",onClick={session.connect();menu=false})
                    DropdownMenuItem(text={Text(t("Desconectar consola local","Disconnect local console"))},enabled=state.phase!="closed",onClick={session.disconnect();menu=false})
                }
            }})}) {padding->Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(t("Versión del agente: ","Agent version: ")+(reportedVersion ?: agentVersion ?: if(state.phase in setOf("connecting","checking","checking_send")) t("Consultando…","Querying…") else t("No disponible","Not available")),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("agent_console_version"))
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(when(state.phase) {
                    "connecting","checking"->t("Verificando canal, agente y permiso de consola…","Verifying channel, agent and console permission…")
                    "checking_send"->t("Reverificando antes de enviar…","Rechecking before sending…")
                    "ready"->t("Canal verificado","Channel verified")
                    else->t("Consola desconectada","Console disconnected")
                },style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f).testTag("agent_console_status"))
                if(state.phase=="closed") TextButton(enabled=connected,onClick={session.connect()},modifier=Modifier.testTag("agent_console_reconnect")) {Text(t("Reconectar","Reconnect"))}
            }
            if(state.phase=="closed" && state.code.isNotBlank()) Text(t("Motivo: ","Reason: ")+state.code,style=MaterialTheme.typography.labelSmall)
            if(searching) OutlinedTextField(query,{query=it.take(128)},singleLine=true,label={Text(t("Filtrar líneas de salida","Filter output lines"))},modifier=Modifier.fillMaxWidth())
            SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {Text(visible.ifEmpty {t("Sin salida recibida.","No output received.")},fontFamily=FontFamily.Monospace,fontSize=12.sp,modifier=Modifier.fillMaxWidth().testTag("agent_console_output"))}
            if(!follow) TextButton(onClick={query="";follow=true}) {Text(t("Ir al final y seguir salida","Go to end and follow output"))}
            if(copyStatus.isNotEmpty()) Text(copyStatus,style=MaterialTheme.typography.labelSmall)
            Text(t("Salida sin prueba de finalización. Búfer: 1000 entradas / 256000 caracteres; historial: 50 comandos, solo en memoria. Al conectar se consulta «versions» una vez para mostrar la compilación del agente.","Output does not prove completion. Buffer: 1000 entries / 256000 characters; history: 50 commands, memory only. On connection, versions is queried once to display the agent build."),style=MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box {TextButton(onClick={history=true},enabled=state.history.isNotEmpty()) {Text(t("Historial","History"))};DropdownMenu(history,{history=false},modifier=Modifier.heightIn(max=260.dp)) {state.history.asReversed().forEach {command->DropdownMenuItem(text={Text(command,maxLines=2,overflow=TextOverflow.Ellipsis)},onClick={draft=command;history=false})}}}
                OutlinedTextField(draft,{draft=it},singleLine=true,label={Text(t("Comando del agente","Agent command"))},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={submit()}),modifier=Modifier.weight(1f).testTag("agent_console_input"))
                TextButton(enabled=canSend,onClick=submit,modifier=Modifier.testTag("agent_console_send")) {Text(t("Enviar","Send"))}
            }
            Text(t("Una línea, máximo 4096 caracteres. No se evalúa HTML/ANSI ni se guardan registros en disco. No introduzcas secretos.","One line, up to 4096 characters. HTML/ANSI is not evaluated and logs are not saved to disk. Do not enter secrets."),style=MaterialTheme.typography.labelSmall)
        }}
        if(actions) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={actions=false},title={Text(t("Acciones del agente","Agent actions"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(t("Actúan sobre el agente de este equipo, no sobre la salida local. Cada acción se revisa y se envía una sola vez.","These act on this device's agent, not on local output. Each action is reviewed and sent once."),style=MaterialTheme.typography.bodySmall)
            AgentConsoleAction.values().forEach {action->TextButton(enabled=actionEnabled(action),onClick={review=action;actions=false},modifier=Modifier.testTag("agent_action_"+action.name.lowercase())) {Text(agentActionLabel(action,es))}}
            DeviceAgentCoreUploadAction(source,nodeId,nodeName,connected && state.phase=="ready" && rights!=null && agentActionFullRights(rights) && agentCoreMainDomain(nodeId,actor),es)
            Text(t("Las acciones pueden requerir derechos completos, administración del servidor o el dominio principal. Su disponibilidad final depende del servidor y del agente.","Actions may require full device rights, server administration or the main domain. Final availability depends on the server and agent."),style=MaterialTheme.typography.labelSmall)
        }},confirmButton={TextButton(onClick={actions=false}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={actions=false}) {Text(t("Volver","Back"))}})
        review?.let {action->iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={review=null},title={Text(agentActionLabel(action,es))},text={Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(t("Dispositivo: ","Device: ")+nodeName);Text(nodeId,style=MaterialTheme.typography.labelSmall)
            Text(agentActionDetail(action,es))
            Text(t("Se reverificará identidad, permisos y agente conectado antes de enviar una sola solicitud. No hay confirmación de finalización ni reenvío automático; una acción ya enviada no se puede cancelar desde aquí.","Identity, permissions and the connected agent are rechecked before one request is sent. Completion is not confirmed and nothing is resent automatically; a sent action cannot be canceled from here."),style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(enabled=actionEnabled(action),onClick={if(session.perform(action)) follow=true;review=null},modifier=Modifier.testTag("agent_action_confirm")) {Text(t("Enviar una vez","Send once"))}},dismissButton={TextButton(onClick={review=null}) {Text(t("Cancelar","Cancel"))}})}
        if(clear) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={clear=false},title={Text(t("Limpiar salida local","Clear local output"))},text={Text(t("Solo borra el búfer visible de esta consola. No borra el núcleo ni modifica el agente; el historial de comandos se conserva.","Only clears this console's local output buffer. Does not clear the core or modify the agent; command history is retained."))},confirmButton={TextButton(onClick={session.clearOutput();clear=false}) {Text(t("Limpiar","Clear"))}},dismissButton={TextButton(onClick={clear=false}) {Text(t("Cancelar","Cancel"))}})
        if(leave) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={leave=false},title={Text(t("Cerrar consola","Close console"))},text={Text(t("Se perderán borrador, salida e historial locales. Un comando ya enviado no se cancela ni se revierte.","Local draft, output and history will be lost. An already sent command is not canceled or undone."))},confirmButton={TextButton(onClick={session.disconnect();onBack()}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={leave=false}) {Text(t("Volver","Back"))}})
    }
}

internal fun agentConsoleBuildVersion(text:String):String?=runCatching {
    val json=JsonParser.parseString(text.trim()).asJsonObject
    fun field(key:String)=json.get(key)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString?.takeIf {it.isNotBlank() && it.length<=128 && it.none {c->c.isISOControl()}}
    val compiled=field("compileTime") ?: field("commitDate")
    val hash=field("commitHash")?.take(12)
    listOfNotNull(compiled,hash).joinToString(" · ").takeIf {it.isNotBlank()}
}.getOrNull()
