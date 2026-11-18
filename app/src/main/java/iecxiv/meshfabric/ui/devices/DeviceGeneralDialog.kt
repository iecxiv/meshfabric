package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

@OptIn(ExperimentalLayoutApi::class)
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
    var description by remember(source,identity,node.id) { mutableStateOf("") }
    var consent by remember(source,identity,node.id) { mutableLongStateOf(0L) }
    var initialized by remember(source,identity,node.id) { mutableStateOf(false) }
    var review by remember(source,identity,node.id) { mutableStateOf<GeneralDraft?>(null) }
    var invalid by remember(source,identity,node.id) { mutableStateOf(false) }
    var discard by remember(source,identity,node.id) { mutableStateOf(false) }
    var editing by remember(source,identity,node.id) { mutableStateOf<String?>(null) }
    val meta=state.meta
    val dirty=initialized && meta!=null && runCatching {!generalMatches(meta,generalDraft(name,tags,description,consent))}.getOrDefault(true)
    LaunchedEffect(meta,state.phase) {
        if(meta!=null && (!initialized || state.phase=="confirmed")) {
            name=meta.name;tags=meta.tags.joinToString(", " );description=meta.description;consent=meta.consent;initialized=true
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
    DeviceGeneralFrame(node.copy(osdesc=meta?.os ?: node.osdesc),close,
        body={
            if(meta==null) Text(text("Nombre: ","Name: ")+node.name)
            if(meta!=null) {
                DeviceGeneralGroupLink(meta.mesh)
                GeneralValueRow(text("Nombre","Name"),name,state.canEdit,"general_name",text("Editar nombre","Edit name")) {editing="name"}
                GeneralValueRow(text("Descripción","Description"),description.ifBlank {text("Sin descripción","No description")},state.canEdit,"general_description",text("Editar descripción","Edit description")) {editing="description"}
                Text(text("Descripción: máximo 1024 caracteres.","Description: up to 1024 characters."),style=MaterialTheme.typography.bodySmall)
                GeneralValueRow(text("Consentimiento del usuario","User consent"),generalConsentLabel(consent,es),state.canEdit,"general_consent",text("Editar consentimiento","Edit consent")) {editing="consent"}
                DeviceNotificationSection(node)
                GeneralValueRow(text("Etiquetas","Tags"),tags.ifBlank {text("Sin etiquetas","No tags")},state.canEdit,"general_tags",text("Editar etiquetas","Edit tags")) {editing="tags"}
                if(dirty) Text(text("Borrador local pendiente de revisar y guardar.","Local draft awaiting review and save."),style=MaterialTheme.typography.bodySmall)
                if(meta.rights and 4L==0L) Text(text("Solo consulta: sin permiso para administrar dispositivos.","Read-only: no permission to manage devices."))
            }
            FlowRow(Modifier.fillMaxWidth().testTag("general_action_rows"),horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                DeviceGeneralActionsAction(node)
                DeviceGeneralNotesAction(node)
                DeviceManualEventAction(node)
                DeviceGeneralRunAction(node)
                DeviceGeneralMessageAction(node)
                DeviceGeneralChatAction(node)
                // Temporarily hidden for 1.3.9; restore when sharing implementation is complete.
                // DeviceGeneralSharesAction(node)
            }
            DevicePowerSection(node.id)
            DeviceAuthorizationSection(node.id,meta?.mesh.orEmpty())
            if(state.phase in setOf("loading","saving")) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if(state.phase=="saving") text("Verificando guardado…","Verifying save…") else text("Cargando datos y permisos…","Loading device data and permissions…"))
            }
            if(state.phase=="confirmed") Text(text("Guardado confirmado por relectura.","Save confirmed by re-reading."),modifier=Modifier.testTag("general_saved"))
            if(code.isNotEmpty()) Text(code,color=MaterialTheme.colorScheme.error)
            if(invalid) Text(text("Revisa el nombre, las etiquetas, la descripción y el consentimiento.","Check the name, tags, description and consent."),color=MaterialTheme.colorScheme.error)
            TextButton(onClick={ retry++ },enabled=state.phase!="saving") { Text(text("Recargar datos y permisos","Reload device data and permissions")) }
        },
        footer={ TextButton(enabled=state.canEdit && dirty,onClick={
            runCatching { generalDraft(name,tags,description,consent) }.onSuccess { review=it }.onFailure { invalid=true }
        },modifier=Modifier.testTag("general_save")) { Text(text("Revisar guardado","Review save")) }
            TextButton(onClick=close) { Text(text("Cerrar","Close")) }
        })
    editing?.let { field->key(source,identity,node.id,field) {
        if(field=="consent") GeneralConsentDialog(consent,state.canEdit,es,{editing=null}) {value->
            if(!state.canEdit) false else runCatching {generalDraft(name,tags,description,value)}
                .onSuccess {consent=requireNotNull(it.consent);invalid=false;editing=null}.isSuccess
        } else {
            val label=when(field) {
                "name"->text("Nombre","Name")
                "description"->text("Descripción","Description")
                else->text("Etiquetas, separadas por comas","Comma-separated tags")
            }
            GeneralFieldDialog(label=label,initialValue=when(field) {"name"->name;"description"->description;else->tags},
                multiline=field!="name",enabled=state.canEdit,es=es,onDismiss={editing=null},
                onApply={value->
                    if(!state.canEdit) false else runCatching {generalDraft(if(field=="name") value else name,if(field=="tags") value else tags,if(field=="description") value else description,consent)}
                        .onSuccess {draft->name=draft.name;tags=draft.tags.joinToString(", ");description=requireNotNull(draft.description);invalid=false;editing=null}.isSuccess
                })
        }
    }}
    review?.let { draft->iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ review=null },
        title={ Text(text("Confirmar cambios","Confirm changes"),fontSize=18.sp,fontWeight=FontWeight.Bold) },
        text={ Column {
            Text(text("Dispositivo: ","Device: ")+node.name)
            Text(text("Nombre: ","Name: ")+draft.name)
            Text(text("Descripción: ","Description: ")+draft.description.orEmpty())
            Text(text("Consentimiento propio del dispositivo: ","Device-specific consent: ")+generalConsentLabel(draft.consent ?: consent,es))
            Text(text("Etiquetas: ","Tags: ")+draft.tags.joinToString(", "))
        } },
        confirmButton={ TextButton(enabled=state.canEdit,onClick={ review=null;editor.save(draft) }) { Text(text("Guardar","Save")) } },
        dismissButton={ TextButton(onClick={ review=null }) { Text(text("Cancelar","Cancel")) } }) }
    if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={ discard=false },
        title={ Text(text("Cerrar General","Close General"),fontSize=18.sp,fontWeight=FontWeight.Bold) },
        text={ Text(text("Se descartará el borrador local. Cerrar no revierte un guardado que ya se haya enviado.","The local draft will be discarded. Closing does not undo a save that has already been sent.")) },
        confirmButton={ TextButton(onClick=onDismiss) { Text(text("Cerrar","Close")) } },
        dismissButton={ TextButton(onClick={ discard=false }) { Text(text("Volver","Back")) } })
}
