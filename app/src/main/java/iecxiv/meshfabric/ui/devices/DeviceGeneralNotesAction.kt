package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.model.MeshNode

@Composable internal fun DeviceGeneralNotesAction(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,identity,actor,node.id) {
        var opened by remember {mutableStateOf(false)}
        DeviceGeneralActionButton(label=t("Notas","Notes"),enabled=connected && actor.isNotBlank() && source?.isUserLogout==false,onClick={opened=true},modifier=Modifier.testTag("general_notes"))
        if(opened) {
            var retry by remember {mutableIntStateOf(0)}
            var draft by remember {mutableStateOf("")}
            var baseline by remember {mutableStateOf("")}
            var initialized by remember {mutableStateOf(false)}
            var editing by remember {mutableStateOf(false)}
            var review by remember {mutableStateOf(false)}
            var discard by remember {mutableStateOf(false)}
            var replace by remember {mutableStateOf(false)}
            val editor=remember(retry) {DeviceGeneralNotesEditor(source,node.id)}
            val state by editor.state.collectAsState()
            val owner=LocalLifecycleOwner.current
            val dirty=initialized && draft!=baseline
            LaunchedEffect(state.original,state.phase) {
                val current=state.original
                if(current!=null && state.phase in setOf("ready","confirmed") && (!initialized || draft==baseline || state.phase=="confirmed")) {
                    baseline=current;draft=current;initialized=true
                    if(state.phase=="confirmed") editing=false
                }
            }
            DisposableEffect(editor,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) editor.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) editor.start() else editor.close()
                onDispose {owner.lifecycle.removeObserver(observer);editor.close()}
            }
            val close:()->Unit={if(dirty || state.phase=="saving") discard=true else opened=false}
            val conflict=initialized && state.original!=null && state.original!=baseline && dirty
            val canSave=editing && dirty && state.canEdit && !conflict && generalNotesValid(draft)
            val status=when {
                state.phase=="loading"->t("Cargando notas y verificando acceso…","Loading notes and verifying access…")
                state.phase=="saving"->t("Reverificando y comprobando el guardado…","Rechecking and verifying save…")
                state.phase=="confirmed"->t("Guardado confirmado por relectura exacta.","Save confirmed by exact re-reading.")
                state.code=="conflict" || conflict->t("Las notas cambiaron. No se ha enviado este guardado. El borrador local se conserva.","Notes changed. This save was not sent. The local draft is preserved.")
                state.code=="unconfirmed"->t("Guardado sin confirmar; puede haberse aplicado. El borrador se conserva. Verifica antes de repetir.","Save unconfirmed; it may have been applied. Draft preserved. Verify before repeating.")
                state.code=="denied"->t("Sin permiso verificado para editar notas. No se ha enviado el guardado.","No verified permission to edit notes. Save was not sent.")
                state.phase=="error"->t("Conexión o lectura no disponible. El borrador local se conserva.","Connection or read unavailable. The local draft is preserved.")
                else->""
            }
            DeviceGeneralFrame(node,close,body={
                Text(t("Notas del dispositivo; guardado independiente del borrador de General.","Device notes; saved independently of the General draft."),style=MaterialTheme.typography.bodySmall)
                if(initialized) {
                    if(editing) OutlinedTextField(draft,{draft=it},readOnly=!state.canEdit,minLines=4,maxLines=10,modifier=Modifier.fillMaxWidth().testTag("general_notes_text"))
                    else Text(draft.ifEmpty {t("Este dispositivo no tiene notas.","This device has no notes.")})
                }
                if(state.phase in setOf("loading","saving")) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(status.isNotEmpty()) Text(status,modifier=Modifier.testTag("general_notes_status"),style=MaterialTheme.typography.bodySmall)
                if(state.phase=="ready" && !state.canEdit) Text(t("Solo consulta: sin permiso específico de editar notas.","Read-only: no specific edit-notes permission."),style=MaterialTheme.typography.bodySmall)
                if(editing) Text(t("Máximo 64000 caracteres. Guardar vacío elimina las notas existentes. No hay reenvíos automáticos; las comprobaciones no son atómicas con el servidor.","Up to 64000 characters. Saving empty clears existing notes. No automatic resends; checks are not atomic with the server."),style=MaterialTheme.typography.bodySmall)
                TextButton(enabled=state.phase!="saving",onClick={retry++}) {Text(t("Reverificar conservando borrador","Recheck and keep draft"))}
                if(dirty && state.original!=null && state.phase!="saving") TextButton(onClick={replace=true}) {Text(t("Descartar borrador y cargar lectura actual","Discard draft and load current read"))}
            },footer={
                if(!editing) TextButton(enabled=state.canEdit && initialized,onClick={editing=true},modifier=Modifier.testTag("general_notes_edit")) {Text(t("Editar","Edit"))}
                else TextButton(enabled=canSave,onClick={review=true},modifier=Modifier.testTag("general_notes_save")) {Text(t("Revisar guardado","Review save"))}
                TextButton(onClick=close) {Text(t("Cerrar","Close"))}
            },subtitle=t("Notas","Notes"),tagPrefix="device_notes")
            if(review) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={review=false},title={Text(if(draft.isEmpty()) t("Eliminar contenido de notas","Clear notes content") else t("Guardar notas","Save notes"))},
                text={Column {Text(node.name);Text(if(draft.isEmpty()) t("Se enviará un texto vacío para eliminar las notas existentes.","Empty text will be sent to clear existing notes.") else t("Se guardará el texto revisado en el editor.","The text reviewed in the editor will be saved."));Text(t("Se comprobarán de nuevo los permisos y el contenido original antes de enviar.","Permissions and original content will be checked again before sending."))}},
                confirmButton={TextButton(enabled=canSave,onClick={review=false;editor.save(baseline,draft)}) {Text(t("Guardar","Save"))}},dismissButton={TextButton(onClick={review=false}) {Text(t("Cancelar","Cancel"))}})
            if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={discard=false},title={Text(t("Cerrar Notas","Close Notes"))},text={Text(t("Se descartará el borrador local. Cerrar no revierte un guardado ya enviado.","The local draft will be discarded. Closing does not undo an already sent save."))},confirmButton={TextButton(onClick={opened=false}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
            if(replace) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={replace=false},title={Text(t("Descartar borrador","Discard draft"))},text={Text(t("Solo se sustituirá el borrador local por la última lectura recibida. No se enviará un guardado.","Only the local draft will be replaced with the latest received read. No save will be sent."))},confirmButton={TextButton(enabled=state.original!=null,onClick={state.original?.let {baseline=it;draft=it};replace=false}) {Text(t("Descartar borrador","Discard draft"))}},dismissButton={TextButton(onClick={replace=false}) {Text(t("Cancelar","Cancel"))}})
        }
    }
}
