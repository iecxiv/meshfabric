package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.model.MeshNode

@Composable internal fun DeviceGeneralDialog(node:MeshNode,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun text(a:String,b:String)=if(es) a else b
    val source=LocalDeviceDetailsClient.current
    val scope=rememberCoroutineScope()
    val owner=LocalLifecycleOwner.current
    var retry by remember { mutableIntStateOf(0) }
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val editor=remember(source,identity,node.id,retry) { DeviceGeneralEditor(source,node,scope) }
    val state by editor.state.collectAsState()
    var name by remember(source,identity,node.id) { mutableStateOf(node.name) }
    var tags by remember(source,identity,node.id) { mutableStateOf("") }
    var initialized by remember(source,identity,node.id) { mutableStateOf(false) }
    var review by remember(source,identity,node.id) { mutableStateOf<GeneralDraft?>(null) }
    var invalid by remember(source,identity,node.id) { mutableStateOf(false) }
    var discard by remember(source,identity,node.id) { mutableStateOf(false) }
    val meta=state.meta
    val dirty=initialized && meta!=null && (name!=meta.name || runCatching { generalDraft(name,tags).tags.toSet()!=meta.tags.toSet() }.getOrDefault(true))
    LaunchedEffect(meta,state.phase) {
        if(meta!=null && (!initialized || state.phase=="confirmed")) {
            name=meta.name;tags=meta.tags.joinToString(", ");initialized=true
        }
    }
    DisposableEffect(editor,owner) {
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_STOP) editor.close() }
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) editor.start() else editor.close()
        onDispose { owner.lifecycle.removeObserver(observer);editor.close() }
    }
    val close:()->Unit={ if(dirty || state.phase=="saving") discard=true else onDismiss() }
    val code=when(state.code) {
        "unverified"->text("No se han podido verificar datos y permisos. Edición bloqueada.","Device data and permissions could not be verified. Editing is disabled.")
        "conflict"->text("El dispositivo cambió desde la lectura. No se ha enviado el guardado; recarga antes de revisar el borrador.","The device changed since it was read. No save was sent; reload before reviewing the draft.")
        "unconfirmed"->text("Guardado sin confirmar. Comprueba el contenido antes de reintentar; no se repetirá automáticamente.","Save unconfirmed. Check the content before retrying; it will not be replayed automatically.")
        "denied"->text("Operación rechazada o permisos insuficientes.","Operation rejected or insufficient permissions.")
        "timeout"->text("Tiempo de espera agotado.","Request timed out.")
        ""->""
        else->text("Conexión o respuesta no disponible. El borrador local se conserva.","Connection or response unavailable. The local draft is preserved.")
    }
    iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest=close,
        title={ Text(node.name,fontSize=18.sp,fontWeight=FontWeight.Bold) },
        text={ Column(Modifier.fillMaxWidth().heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("General",style=MaterialTheme.typography.titleSmall)
            Text(text("Sistema: ","OS: ")+(meta?.os ?: node.osdesc.orEmpty()).ifBlank { text("Sin datos","No data") })
            Text(if(node.isOnline) text("Conectado","Connected") else text("Desconectado","Disconnected"))
            if(meta==null) Text(text("Nombre: ","Name: ")+node.name)
            if(meta!=null) {
                val group=source?.groupedDevices?.value?.firstOrNull { it.first.id==meta.mesh }?.first?.name
                if(!group.isNullOrBlank()) Text(text("Grupo: ","Group: ")+group)
                OutlinedTextField(name,{ name=it;invalid=false },label={ Text(text("Nombre","Name")) },readOnly=!state.canEdit,
                    singleLine=true,modifier=Modifier.fillMaxWidth().testTag("general_name"))
                OutlinedTextField(tags,{ tags=it;invalid=false },label={ Text(text("Etiquetas, separadas por comas","Comma-separated tags")) },readOnly=!state.canEdit,
                    modifier=Modifier.fillMaxWidth().testTag("general_tags"))
                if(meta.rights and 4L==0L) Text(text("Solo consulta: sin permiso para administrar dispositivos.","Read-only: no permission to manage devices."))
            }
            DevicePowerSection(node.id)
            if(state.phase in setOf("loading","saving")) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if(state.phase=="saving") text("Verificando guardado…","Verifying save…") else text("Cargando datos y permisos…","Loading device data and permissions…"))
            }
            if(state.phase=="confirmed") Text(text("Guardado confirmado por relectura.","Save confirmed by re-reading."),modifier=Modifier.testTag("general_saved"))
            if(code.isNotEmpty()) Text(code,color=MaterialTheme.colorScheme.error)
            if(invalid) Text(text("Nombre válido de hasta 256 caracteres; cada etiqueta debe tener menos de 64 caracteres.","Use a valid name up to 256 characters; each tag must contain fewer than 64 characters."),color=MaterialTheme.colorScheme.error)
            TextButton(onClick={ retry++ },enabled=state.phase!="saving") { Text(text("Recargar datos y permisos","Reload device data and permissions")) }
        } },
        confirmButton={ TextButton(enabled=state.canEdit && dirty,onClick={
            runCatching { generalDraft(name,tags) }.onSuccess { review=it }.onFailure { invalid=true }
        },modifier=Modifier.testTag("general_save")) { Text(text("Revisar guardado","Review save")) } },
        dismissButton={ TextButton(onClick=close) { Text(text("Cerrar","Close")) } })
    review?.let { draft->iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ review=null },
        title={ Text(text("Confirmar cambios","Confirm changes"),fontSize=18.sp,fontWeight=FontWeight.Bold) },
        text={ Column { Text(text("Dispositivo: ","Device: ")+node.name);Text(text("Nombre: ","Name: ")+draft.name);Text(text("Etiquetas: ","Tags: ")+draft.tags.joinToString(", ")) } },
        confirmButton={ TextButton(enabled=state.canEdit,onClick={ review=null;editor.save(draft) }) { Text(text("Guardar","Save")) } },
        dismissButton={ TextButton(onClick={ review=null }) { Text(text("Cancelar","Cancel")) } }) }
    if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ discard=false },
        title={ Text(text("Cerrar General","Close General"),fontSize=18.sp,fontWeight=FontWeight.Bold) },
        text={ Text(text("Se descartará el borrador local. Cerrar no revierte un guardado que ya se haya enviado.","The local draft will be discarded. Closing does not undo a save that has already been sent.")) },
        confirmButton={ TextButton(onClick=onDismiss) { Text(text("Cerrar","Close")) } },
        dismissButton={ TextButton(onClick={ discard=false }) { Text(text("Volver","Back")) } })
}
