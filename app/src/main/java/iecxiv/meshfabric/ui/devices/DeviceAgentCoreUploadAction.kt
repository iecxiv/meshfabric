package iecxiv.meshfabric.ui.devices

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.*

@Composable internal fun DeviceAgentCoreUploadAction(source:MeshCentralClient,nodeId:String,nodeName:String,enabled:Boolean,es:Boolean) {
    var opened by remember {mutableStateOf(false)}
    fun t(a:String,b:String)=if(es) a else b
    TextButton(enabled=enabled,onClick={opened=true},modifier=Modifier.testTag("agent_action_upload_core_file")) {Text(t("Cargar archivo de núcleo…","Upload a core file…"))}
    if(opened) DeviceAgentCoreUploadDialog(source,nodeId,nodeName,es) {opened=false}
}
@Composable internal fun DeviceAgentCoreUploadDialog(source:MeshCentralClient,nodeId:String,nodeName:String,es:Boolean,onClose:()->Unit) {
    fun t(a:String,b:String)=if(es) a else b
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope()
    var retry by remember {mutableIntStateOf(0)}
    val writer=remember(retry) {DeviceAgentCoreUploader(source,nodeId)}
    val state by writer.state.collectAsState()
    var file by remember {mutableStateOf<AgentCoreFile?>(null)}
    var reading by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    var reader by remember {mutableStateOf<Job?>(null)}
    DisposableEffect(writer,owner) {
        var started=false
        fun start() {if(!started) {started=true;writer.start()}}
        val observer=LifecycleEventObserver {_,event->when(event) {
            Lifecycle.Event.ON_RESUME->start()
            Lifecycle.Event.ON_STOP->{reader?.cancel();reading=false;file=null;writer.close()}
            else->Unit
        }}
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {owner.lifecycle.removeObserver(observer);writer.close();reader?.cancel()}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null) {
            reader?.cancel();file=null;reading=true;error=false;retry++
            reader=scope.launch {
                val result=withContext(Dispatchers.IO) {runCatching {
                    val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->if(cursor.moveToFirst()) cursor.getString(0) else null} ?: error("Missing name")
                    requireNotNull(context.contentResolver.openInputStream(uri)).use {readAgentCoreFile(name,it)}
                }}
                file=result.getOrNull();error=result.isFailure;reading=false
            }
        }
    }
    val busy=state.phase in setOf("checking","cookie","uploading")
    val close:()->Unit={if(busy) discard=true else {reader?.cancel();file=null;writer.close();onClose()}}
    iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=close,title={Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {Icon(Icons.Default.UploadFile,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary);Box(Modifier.weight(1f)) {DeviceBatchDialogTitle(t("Cargar archivo de núcleo","Upload a core file"))} }},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        iecxiv.meshfabric.ui.components.AppDialogCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(t("Dispositivo: ","Device: ")+nodeName);Text(nodeId,style=MaterialTheme.typography.labelSmall) } }
        Text(t("Reemplaza el núcleo del agente por código JavaScript de tu archivo. Usa solo código de confianza: puede interrumpir el acceso remoto. No es una transferencia de archivos ordinaria.","Replaces the agent core with JavaScript from your file. Use only trusted code: it may interrupt remote access. This is not an ordinary file transfer."))
        Text(t("Solo dominio principal, HTTPS y derechos completos sobre el dispositivo. Archivo .js UTF-8, sin NUL, no vacío y de hasta 1 MiB. Se conservan los bytes revisados en memoria; no se vuelve a leer el archivo al enviar.","Main domain, HTTPS and full device rights only. Non-empty UTF-8 .js file, without NUL, up to 1 MiB. Reviewed bytes stay in memory; the file is not reread when sending."),style=MaterialTheme.typography.bodySmall)
        TextButton(enabled=state.phase=="ready" && !reading,onClick={file=null;error=false;picker.launch(arrayOf("*/*"))},modifier=Modifier.testTag("agent_core_choose_file")) {Text(t("Seleccionar archivo local","Choose local file"))}
        if(reading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(error) Text(t("No se pudo leer el archivo o no cumple el formato/límite. No se ha enviado.","The file could not be read or does not meet the format/size limit. Nothing was sent."),color=MaterialTheme.colorScheme.error)
        file?.let {Text(t("Archivo: ","File: ")+it.name);Text(t("Bytes: ","Bytes: ")+it.size);Text("SHA-256: "+it.sha256,style=MaterialTheme.typography.labelSmall,modifier=Modifier.testTag("agent_core_sha256"))}
        Text(when(state.phase) {
            "loading"->t("Verificando identidad, permisos y agente…","Verifying identity, rights and agent…")
            "ready"->t("Revisa el equipo y el archivo antes de confirmar. Seleccionar no envía nada.","Review the device and file before confirming. Selecting sends nothing.")
            "checking","cookie"->t("Reverificando antes de la única carga…","Rechecking before the single upload…")
            "uploading"->t("Carga iniciada. Aplicación del núcleo sin confirmar.","Upload started. Core application is unconfirmed.")
            "received"->t("El servidor respondió HTTP 200. Esto no confirma que el núcleo se haya instalado o ejecutado.","The server returned HTTP 200. This does not confirm that the core was installed or executed.")
            else->if(state.attempted) t("Resultado sin confirmar. No repitas sin comprobar el agente; el núcleo puede haberse aplicado.","Outcome unconfirmed. Check the agent before repeating; the core may have been applied.") else t("No se ha enviado: identidad, conexión, dominio o permisos no verificables. Cierra y vuelve a abrir para revisar de nuevo.","Not sent: identity, connection, domain or rights could not be verified. Close and reopen to review again.")
        },style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("agent_core_upload_status"))
        Text(t("Una sola solicitud, sin reintentos ni reenvíos al reconectar. Cerrar no revierte una carga que haya empezado.","One request, with no retries or replay on reconnection. Closing does not undo an upload that has started."),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=state.phase=="ready" && file!=null && !reading,onClick={file?.let {writer.submit(it)}},modifier=Modifier.testTag("agent_core_upload_confirm")) {Text(t("Cargar una vez","Upload once"))}},dismissButton={TextButton(onClick=close) {Text(t("Cerrar","Close"))}})
    if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={discard=false},title={DeviceBatchDialogTitle(t("Cerrar carga","Close upload"))},text={Text(t("Se cancelará el seguimiento y la transferencia local si sigue en curso. No deshace lo que el servidor ya haya recibido; el resultado puede quedar sin confirmar.","Local transfer and monitoring will be canceled if still running. This does not undo what the server has received; the outcome may remain unconfirmed."))},confirmButton={TextButton(onClick={file=null;writer.close();onClose()}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
}
