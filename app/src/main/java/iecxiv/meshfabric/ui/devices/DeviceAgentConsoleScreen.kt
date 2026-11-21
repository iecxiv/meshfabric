package iecxiv.meshfabric.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlinx.coroutines.flow.first
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
    val consoleNode=remember(groups,cleanNodeId) {groups.asSequence().flatMap {it.second.asSequence()}.singleOrNull {it.id.removePrefix("node//").removePrefix("node/").trim()==cleanNodeId}}
    val identity=Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val context=LocalContext.current
    key(source,identity,actor,nodeId) {
        val session=remember {DeviceAgentConsoleSession(source,nodeId)}
        val state by session.state.collectAsState()
        val owner=LocalLifecycleOwner.current
        var reportedVersion by remember {mutableStateOf<String?>(null)}
        var queryingVersion by remember {mutableStateOf(false)}
        LaunchedEffect(session,state.phase!="closed") {
            reportedVersion=null
            queryingVersion=false
            if(state.phase!="closed") {
                queryingVersion=true
                try {
                    val ready=session.state.first {it.phase=="ready" || it.phase=="closed"}
                    if(ready.phase=="ready") reportedVersion=readAgentCoreInventoryVersion(source,nodeId)
                } finally {queryingVersion=false}
            }
        }
        var draft by remember {mutableStateOf("")}
        var query by remember {mutableStateOf("")}
        var searching by remember {mutableStateOf(false)}
        var menu by remember {mutableStateOf(false)}
        var history by remember {mutableStateOf(false)}
        var clear by remember {mutableStateOf(false)}
        var leave by remember {mutableStateOf(false)}
        var actions by remember {mutableStateOf(false)}
        var coreUpload by remember {mutableStateOf(false)}
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
                IconButton(onClick={actions=false;menu=true},modifier=Modifier.testTag("agent_console_menu")) {Icon(Icons.Default.MoreVert,t("Herramientas de consola","Console tools"))}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false;actions=false},
            modifier=Modifier.heightIn(max=(LocalConfiguration.current.screenHeightDp.dp-96.dp).coerceAtLeast(48.dp)).testTag("agent_console_tools_popup"),
            shape=MaterialTheme.shapes.medium,containerColor=MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation=2.dp,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            if(actions) {
                DropdownMenuItem(text={Text(t("Volver","Back"))},leadingIcon={Icon(Icons.AutoMirrored.Filled.ArrowBack,null)},onClick={actions=false},modifier=Modifier.testTag("agent_console_actions_back"))
                AgentConsoleAction.values().forEach {action->
                    val icon=when(action) {
                        AgentConsoleAction.DEFAULT_CORE->Icons.Default.CloudUpload
                        AgentConsoleAction.CLEAR_CORE->Icons.Default.Delete
                        AgentConsoleAction.RECOVERY_CORE->Icons.Default.Build
                        AgentConsoleAction.TINY_CORE->Icons.Default.Memory
                        AgentConsoleAction.SOFT_DISCONNECT,AgentConsoleAction.HARD_DISCONNECT->Icons.Default.LinkOff
                        AgentConsoleAction.RESTART_SERVICE->Icons.Default.Refresh
                        AgentConsoleAction.FORCE_UPDATE->Icons.Default.SystemUpdateAlt
                    }
                    DropdownMenuItem(text={Text(agentActionLabel(action,es))},leadingIcon={Icon(icon,null,Modifier.size(20.dp))},enabled=actionEnabled(action),onClick={review=action;actions=false;menu=false},modifier=Modifier.testTag("agent_action_"+action.name.lowercase()))
                }
                DropdownMenuItem(text={Text(t("Cargar archivo de núcleo…","Upload a core file…"))},leadingIcon={Icon(Icons.Default.UploadFile,null,Modifier.size(20.dp))},enabled=connected && state.phase=="ready" && rights!=null && agentActionFullRights(rights) && agentCoreMainDomain(nodeId,actor),onClick={coreUpload=true;actions=false;menu=false},modifier=Modifier.testTag("agent_action_upload_core_file"))
            } else {

                    DropdownMenuItem(text={Text(t("Acciones del agente…","Agent actions…"))},leadingIcon={Icon(Icons.Default.Settings,null,Modifier.size(20.dp))},trailingIcon={Icon(Icons.Default.ChevronRight,null,Modifier.size(20.dp))},enabled=state.phase=="ready" && connected,onClick={actions=true},modifier=Modifier.testTag("agent_console_actions"))
                    HorizontalDivider()
                    DropdownMenuItem(text={Text(t("Filtrar salida local","Filter local output"))},leadingIcon={Icon(Icons.Default.FilterList,null,Modifier.size(20.dp))},onClick={searching=!searching;if(!searching) query="";menu=false})
                    DropdownMenuItem(text={Text(t("Copiar salida visible","Copy visible output"))},leadingIcon={Icon(Icons.Default.ContentCopy,null,Modifier.size(20.dp))},enabled=visible.isNotEmpty(),onClick={
                        menu=false;copyStatus=if(runCatching {val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: error("Clipboard unavailable");clipboard.setPrimaryClip(ClipData.newPlainText("Agent console",visible))}.isSuccess) t("Salida copiada; puede contener datos sensibles.","Output copied; it may contain sensitive data.") else t("No se pudo copiar.","Copy failed.")
                    })
                    DropdownMenuItem(text={Text(t("Limpiar salida local","Clear local output"))},leadingIcon={Icon(Icons.Default.DeleteSweep,null,Modifier.size(20.dp))},onClick={clear=true;menu=false})
                    HorizontalDivider()
                    DropdownMenuItem(text={Text(t("Reconectar consola","Reconnect console"))},leadingIcon={Icon(Icons.Default.Refresh,null,Modifier.size(20.dp))},enabled=connected && state.phase=="closed",onClick={session.connect();menu=false})
                    DropdownMenuItem(text={Text(t("Desconectar consola local","Disconnect local console"))},leadingIcon={Icon(Icons.Default.LinkOff,null,Modifier.size(20.dp))},enabled=state.phase!="closed",onClick={session.disconnect();menu=false})
                    }
                }
            }})}) {padding->Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            DeviceStatusDecoration(consoleNode,Modifier.fillMaxWidth().padding(horizontal=4.dp),"agent_console_device")
            Text(t("Versión del agente: ","Agent version: ")+(reportedVersion ?: if(queryingVersion || state.phase in setOf("connecting","checking")) t("Consultando…","Querying…") else t("No disponible","Not available")),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("agent_console_version"))
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("agent_console_status_row"),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(when(state.phase) {
                    "connecting","checking"->t("Verificando canal, agente y permiso de consola…","Verifying channel, agent and console permission…")
                    "checking_send"->t("Reverificando antes de enviar…","Rechecking before sending…")
                    "ready"->t("Canal verificado","Channel verified")
                    else->t("Consola desconectada","Console disconnected")
                },style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f).testTag("agent_console_status"))
                TextButton(enabled=connected && state.phase=="closed",onClick={if(state.phase=="closed") session.connect()},modifier=if(state.phase=="closed") Modifier.testTag("agent_console_reconnect") else Modifier.alpha(0f).clearAndSetSemantics {}) {Text(t("Reconectar","Reconnect"))}
            }
            if(state.phase=="closed" && state.code.isNotBlank()) {
                Text(t("Motivo: ","Reason: ")+agentConsoleReasonText(state.code,es),style=MaterialTheme.typography.labelSmall)
                Text(t("Código técnico: ","Technical code: ")+state.code,style=MaterialTheme.typography.labelSmall)
            }
            if(searching) OutlinedTextField(query,{query=it.take(128)},singleLine=true,label={Text(t("Filtrar líneas de salida","Filter output lines"))},modifier=Modifier.fillMaxWidth())
            SelectionContainer(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium).background(androidx.compose.ui.graphics.Color.Black).verticalScroll(scroll).padding(8.dp)) {Text(visible.ifEmpty {t("Sin salida recibida.","No output received.")},fontFamily=FontFamily.Monospace,fontSize=12.sp,color=androidx.compose.ui.graphics.Color.White,modifier=Modifier.fillMaxWidth().testTag("agent_console_output"))}
            if(!follow) TextButton(onClick={query="";follow=true}) {Text(t("Ir al final y seguir salida","Go to end and follow output"))}
            if(copyStatus.isNotEmpty()) Text(copyStatus,style=MaterialTheme.typography.labelSmall)
            Text(t("Salida sin prueba de finalización. Búfer: 1000 entradas / 256000 caracteres; historial: 50 comandos, solo en memoria.","Output does not prove completion. Buffer: 1000 entries / 256000 characters; history: 50 commands, memory only."),style=MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box {TextButton(onClick={history=true},enabled=state.history.isNotEmpty(),modifier=Modifier.heightIn(min=48.dp).testTag("agent_console_history")) {Text(t("Historial","History"))};DropdownMenu(history,{history=false},modifier=Modifier.heightIn(max=260.dp)) {state.history.asReversed().forEach {command->DropdownMenuItem(text={Text(command,maxLines=2,overflow=TextOverflow.Ellipsis)},onClick={draft=command;history=false})}}}
                OutlinedTextField(draft,{draft=it},singleLine=true,label={Text(t("Comando del agente","Agent command"))},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={submit()}),modifier=Modifier.weight(1f).testTag("agent_console_input"))
                TextButton(enabled=canSend,onClick=submit,modifier=Modifier.heightIn(min=48.dp).testTag("agent_console_send")) {Text(t("Enviar","Send"))}
            }
            Text(t("Una línea, máximo 4096 caracteres. No se evalúa HTML/ANSI ni se guardan registros en disco. No introduzcas secretos.","One line, up to 4096 characters. HTML/ANSI is not evaluated and logs are not saved to disk. Do not enter secrets."),style=MaterialTheme.typography.labelSmall)
        }}
        if(coreUpload) DeviceAgentCoreUploadDialog(source,nodeId,nodeName,es) {coreUpload=false}
        review?.let {action->iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={review=null},title={Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Settings,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary);Box(Modifier.weight(1f)) {DeviceBatchDialogTitle(agentActionLabel(action,es))} }},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            iecxiv.meshfabric.ui.components.AppDialogCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(t("Dispositivo: ","Device: ")+nodeName);Text(nodeId,style=MaterialTheme.typography.labelSmall) } }
            iecxiv.meshfabric.ui.components.AppDialogCard(Modifier.fillMaxWidth()) {Text(agentActionDetail(action,es),modifier=Modifier.padding(12.dp),style=MaterialTheme.typography.bodyMedium)}
            Text(t("Se reverificará identidad, permisos y agente conectado antes de enviar una sola solicitud. No hay confirmación de finalización ni reenvío automático; una acción ya enviada no se puede cancelar desde aquí.","Identity, permissions and the connected agent are rechecked before one request is sent. Completion is not confirmed and nothing is resent automatically; a sent action cannot be canceled from here."),style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(enabled=actionEnabled(action),onClick={if(session.perform(action)) follow=true;review=null},modifier=Modifier.testTag("agent_action_confirm")) {Text(t("Enviar una vez","Send once"))}},dismissButton={TextButton(onClick={review=null}) {Text(t("Cancelar","Cancel"))}})}
        if(clear) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={clear=false},title={Text(t("Limpiar salida local","Clear local output"))},text={Text(t("Solo borra el búfer visible de esta consola. No borra el núcleo ni modifica el agente; el historial de comandos se conserva.","Only clears this console's local output buffer. Does not clear the core or modify the agent; command history is retained."))},confirmButton={TextButton(onClick={session.clearOutput();clear=false}) {Text(t("Limpiar","Clear"))}},dismissButton={TextButton(onClick={clear=false}) {Text(t("Cancelar","Cancel"))}})
        if(leave) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={leave=false},title={Text(t("Cerrar consola","Close console"))},text={Text(t("Se perderán borrador, salida e historial locales. Un comando ya enviado no se cancela ni se revierte.","Local draft, output and history will be lost. An already sent command is not canceled or undone."))},confirmButton={TextButton(onClick={session.disconnect();onBack()}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={leave=false}) {Text(t("Volver","Back"))}})
    }
}
