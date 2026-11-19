package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.*
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private data class ManagedRow(val group:MeshGroup,val node:MeshNode,val draft:ManagedBatchDraft?=null,val phase:String="loading",val code:String="")
private data class ManagedRequest(val action:ManagedBatchAction,val rows:List<ManagedRow>,val identity:DeviceBatchPowerIdentity)
private val managedTerminal=setOf("queued","accepted","removed","rejected","skipped","error")
private fun managedLabel(a:ManagedBatchAction,es:Boolean)=when(a) {
    ManagedBatchAction.CLEAR_CORE->if(es) "Eliminar el núcleo del agente" else "Clear agent core"
    ManagedBatchAction.DEFAULT_CORE->if(es) "Cargar núcleo predeterminado" else "Load default agent core"
    ManagedBatchAction.UNINSTALL->if(es) "Desinstalar el agente" else "Uninstall agent"
    ManagedBatchAction.REMOVE->if(es) "Eliminar dispositivos de MeshCentral" else "Remove devices from MeshCentral"
}
private fun managedToken(a:ManagedBatchAction,es:Boolean)=when(a) {
    ManagedBatchAction.CLEAR_CORE->if(es) "BORRAR" else "CLEAR"
    ManagedBatchAction.DEFAULT_CORE->if(es) "CARGAR" else "LOAD"
    ManagedBatchAction.UNINSTALL->if(es) "DESINSTALAR" else "UNINSTALL"
    ManagedBatchAction.REMOVE->if(es) "ELIMINAR" else "REMOVE"
}
@Composable internal fun deviceManagedBatchTitle(action:ManagedBatchAction)=managedLabel(action,LocalConfiguration.current.locales[0].language=="es")
private suspend fun managedCheck(source:MeshCentralClient,request:ManagedRequest,row:ManagedRow):ManagedRow {
    if(!deviceBatchPowerSame(source,request.identity) || row.node.meshid!=row.group.id) return row.copy(phase="skipped")
    val editor=DeviceManagedBatchEditor(source,row.node,request.action)
    try {
        editor.start()
        val state=withTimeoutOrNull(30000L) {editor.state.first {it.phase=="ready" || it.phase in managedTerminal}}
        return if(state?.phase=="ready" && state.draft!=null && deviceBatchPowerSame(source,request.identity)) row.copy(draft=state.draft,phase="ready") else row.copy(phase="skipped")
    } finally {editor.close()}
}
private suspend fun managedSend(source:MeshCentralClient,request:ManagedRequest,row:ManagedRow):ManagedRow {
    val draft=row.draft ?: return row.copy(phase="not_sent")
    if(!deviceBatchPowerSame(source,request.identity)) return row.copy(phase="not_sent")
    val editor=DeviceManagedBatchEditor(source,row.node,request.action)
    try {
        editor.start()
        val ready=withTimeoutOrNull(30000L) {editor.state.first {it.phase=="ready" || it.phase in managedTerminal}}
        if(ready?.phase!="ready" || ready.draft!=draft || !deviceBatchPowerSame(source,request.identity)) return row.copy(phase="not_sent")
        currentCoroutineContext().ensureActive()
        if(!editor.submit(draft)) return row.copy(phase="not_sent")
        val result=withTimeoutOrNull(40000L) {editor.state.first {it.phase in managedTerminal}}
        return if(result==null) row.copy(phase="unknown") else row.copy(phase=result.phase,code=result.code)
    } finally {editor.close()}
}
@Composable internal fun rememberDeviceManagedBatchAction(source:MeshCentralClient,entries:List<Pair<MeshGroup,MeshNode>>):(ManagedBatchAction)->Unit {
    var request by remember(source) {mutableStateOf<ManagedRequest?>(null)}
    request?.let {current->ManagedDialog(source,current) {request=null}}
    return {action->
        val rows=deviceBatchPowerTargets(entries).map {ManagedRow(it.group,it.node)}
        if(rows.isNotEmpty() && source.connectionState.value && !source.isUserLogout) request=ManagedRequest(action,rows,deviceBatchPowerIdentity(source))
    }
}
@Composable private fun ManagedDialog(source:MeshCentralClient,request:ManagedRequest,onClose:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val scope=rememberCoroutineScope()
    var rows by remember(request) {mutableStateOf(request.rows)}
    var checking by remember(request) {mutableStateOf(true)}
    var running by remember(request) {mutableStateOf(false)}
    var finished by remember(request) {mutableStateOf(false)}
    var acknowledged by remember(request) {mutableStateOf(false)}
    var typed by remember(request) {mutableStateOf("")}
    var job by remember(request) {mutableStateOf<Job?>(null)}
    fun update(row:ManagedRow) {rows=rows.map {if(it.node.id==row.node.id) row else it}}
    LaunchedEffect(request) {
        try {val gate=Semaphore(4);coroutineScope {request.rows.map {row->async {gate.withPermit {update(managedCheck(source,request,row))}}}.awaitAll()}}
        finally {checking=false}
    }
    LaunchedEffect(request) {while(isActive) {if(!deviceBatchPowerSame(source,request.identity)) {job?.cancel();onClose();break};delay(100L)}}
    val eligible=rows.count {it.phase=="ready"}
    val token=managedToken(request.action,es)
    iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={if(!running) onClose()},title={DeviceBatchDialogTitle(managedLabel(request.action,es)+" (${rows.size})")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val detail=when(request.action) {
                ManagedBatchAction.CLEAR_CORE->agentActionDetail(AgentConsoleAction.CLEAR_CORE,es)
                ManagedBatchAction.DEFAULT_CORE->agentActionDetail(AgentConsoleAction.DEFAULT_CORE,es)
                ManagedBatchAction.UNINSTALL->t("Solicita desinstalar el agente. Puede perderse el acceso y requerir reinstalación. Una desconexión no confirma desinstalación.","Requests agent uninstallation. Access may be lost and reinstallation may be required. Disconnection does not confirm uninstallation.")
                ManagedBatchAction.REMOVE->t("Elimina dispositivos mediante MeshCentral. Puede perderse el acceso y aplicarse el comportamiento asociado del servidor/agente. No equivale a verificar la desinstalación del software.","Removes devices through MeshCentral. Access may be lost and associated server/agent behavior may apply. This does not verify software uninstallation.")
            }
            Text(detail)
            Text(t("Esta integración exige derechos completos. Núcleos y desinstalación requieren un agente conectado. Un intento por equipo, sin reenvío automático. Detener no deshace lo enviado.","This integration requires full rights. Core operations and uninstallation require a connected agent. One attempt per device, no automatic resend. Stopping does not undo requests already sent."))
            if(checking || running) LinearProgressIndicator(Modifier.fillMaxWidth())
            rows.forEach {row->
                HorizontalDivider();Text(row.node.name.ifBlank {row.node.id},style=MaterialTheme.typography.titleSmall);Text(row.group.name)
                val label=when(row.phase) {
                    "loading"->t("Comprobando","Checking")
                    "ready"->t("Elegible","Eligible")
                    "sending"->t("Revalidando y solicitando","Rechecking and requesting")
                    "queued"->t("Encolada localmente; aceptación y efecto no verificados","Queued locally; acceptance and effect not verified")
                    "accepted"->t("Solicitud aceptada; efecto no verificado","Request accepted; effect not verified")
                    "removed"->t("Servidor aceptó y se observó el evento de eliminación del registro","Server accepted and a record-removal event was observed")
                    "rejected"->t("Rechazada","Rejected")
                    "skipped"->t("Omitida: requisitos no verificados o contexto cambiado","Skipped: requirements not verified or context changed")
                    "not_sent"->t("No enviada","Not sent")
                    "error"->if(row.code=="unconfirmed") t("Sin confirmar; puede haberse ejecutado","Unconfirmed; may have executed") else t("No enviada","Not sent")
                    else->t("Sin confirmar; puede haberse ejecutado","Unconfirmed; may have executed")
                };Text(label,style=MaterialTheme.typography.bodySmall)
            }
            if(!finished && !running) {
                Row {Checkbox(checked=acknowledged,enabled=!checking,onCheckedChange={acknowledged=it});Text(t("He revisado los equipos y entiendo el riesgo de perder acceso.","I reviewed the devices and understand the risk of losing access."),Modifier.weight(1f))}
                OutlinedTextField(value=typed,onValueChange={typed=it},enabled=!checking,singleLine=true,label={Text(t("Escribe ","Type ")+token)},modifier=Modifier.fillMaxWidth())
            }
            if(finished) Text(t("Registros con eliminación observada: ","Records with observed removal: ")+rows.count {it.phase=="removed"}+t(" · Aceptadas (efecto no verificado): "," · Accepted (effect not verified): ")+rows.count {it.phase=="accepted"}+t(" · Encoladas localmente: "," · Locally queued: ")+rows.count {it.phase=="queued"})
        }
    },confirmButton={
        if(!finished) TextButton(enabled=!checking && !running && eligible>0 && acknowledged && typed.trim().equals(token,true),onClick={
            if(!checking && !running && !finished && acknowledged && typed.trim().equals(token,true) && deviceBatchPowerSame(source,request.identity)) {
                val approved=rows.filter {it.phase=="ready"};running=true
                job=scope.launch {try {
                    val gate=Semaphore(4)
                    coroutineScope {approved.map {row->async {gate.withPermit {
                        currentCoroutineContext().ensureActive();update(row.copy(phase="sending"));update(managedSend(source,request,row))
                    }}}.awaitAll()};finished=true
                } finally {running=false}}
            }
        }) {Text(t("Solicitar una vez: ","Request once: ")+eligible)}
    },dismissButton={TextButton(onClick={
        if(running) {job?.cancel();rows=rows.map {when(it.phase) {"sending"->it.copy(phase="unknown");"ready"->it.copy(phase="not_sent");else->it}};running=false;finished=true}
        else onClose()
    }) {Text(if(running) t("Detener pendientes","Stop pending requests") else t("Cerrar","Close"))}})
}
