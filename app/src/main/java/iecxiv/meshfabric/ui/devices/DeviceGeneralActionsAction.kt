package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.model.MeshNode

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DeviceGeneralActionsAction(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val inventory=source?.groupedDevices?.collectAsState()?.value.orEmpty()
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    fun label(action:GeneralDeviceAction)=when(action) {GeneralDeviceAction.WAKE->t("Despertar (WoL)","Wake up (WoL)");GeneralDeviceAction.RESTART->t("Reiniciar","Restart");GeneralDeviceAction.SHUTDOWN->t("Apagar","Shut down");GeneralDeviceAction.SLEEP->t("Suspender","Sleep")}
    key(source,identity,actor,node.id) {
        var opened by remember {mutableStateOf(false)}
        DeviceGeneralActionButton(t("Acciones","Actions"),connected && actor.isNotBlank() && source?.isUserLogout==false,{opened=true},Modifier.testTag("general_actions"))
        if(opened) {
            val editor=remember {DeviceGeneralActionsEditor(source,node.id)}
            val state by editor.state.collectAsState()
            var review by remember {mutableStateOf<GeneralDeviceActionDraft?>(null)}
            var submitted by remember {mutableStateOf(false)}
            var discard by remember {mutableStateOf(false)}
            val owner=LocalLifecycleOwner.current
            DisposableEffect(editor,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) editor.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) editor.start() else editor.close()
                onDispose {owner.lifecycle.removeObserver(observer);editor.close()}
            }
            val close:()->Unit={if(state.phase in setOf("checking","sent")) discard=true else opened=false}
            val meta=state.meta
            DeviceGeneralFrame(node,close,body={
                Text(t("Acciones sobre este único dispositivo. Reiniciar, apagar o suspender pueden interrumpir sesiones y provocar pérdida de trabajo no guardado. No modifican el borrador de General.","Actions for this device only. Restarting, shutting down or sleeping may interrupt sessions and lose unsaved work. The General draft is not modified."),style=MaterialTheme.typography.bodySmall)
                Text(t("Despertar requiere permiso WoL. Las otras acciones requieren control remoto, permiso de reinicio/apagado y agente conectado. La disponibilidad efectiva depende del equipo.","Wake requires WoL permission. Other actions require remote-control and reset/power-off permissions and an online agent. Actual availability depends on the device."),style=MaterialTheme.typography.bodySmall)
                if(review==null && !submitted && meta!=null && state.phase=="ready") {
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally)) {
                        GeneralDeviceAction.values().forEach {action->DeviceGeneralActionButton(label(action),generalDeviceActionAvailable(node.id,meta,inventory,action),{review=GeneralDeviceActionDraft(action,meta.mesh,meta.os)},Modifier.testTag("general_action_${action.name.lowercase()}"))}
                    }
                    if(GeneralDeviceAction.values().none {generalDeviceActionAvailable(node.id,meta,inventory,it)}) Text(t("No hay acciones disponibles con el dispositivo y los permisos verificados.","No actions are available with the verified device and permissions."))
                }
                review?.let {draft->
                    Text(t("Revisión: ","Review: ")+label(draft.action),style=MaterialTheme.typography.titleSmall)
                    Text(t("Dispositivo: ","Device: ")+node.name);Text(node.id)
                    Text(t("Grupo: ","Group: ")+draft.mesh)
                    Text(t("Sistema revisado: ","Reviewed OS: ")+draft.os.ifBlank {t("No identificado","Unidentified")})
                    Text(t("Antes de enviar se comprobarán de nuevo identidad, dispositivo, grupo, sistema y permisos. Si han cambiado, no se enviará.","Identity, device, group, OS and permissions will be checked again before sending. If they changed, nothing will be sent."),style=MaterialTheme.typography.bodySmall)
                }
                if(state.phase in setOf("loading","checking")) LinearProgressIndicator(Modifier.fillMaxWidth())
                val status=when(state.phase) {
                    "loading"->t("Verificando datos y permisos…","Verifying data and permissions…")
                    "checking"->t("Reverificando antes del envío…","Rechecking before sending…")
                    "sent"->t("Solicitud enviada; resultado en el equipo sin confirmar.","Request sent; device outcome unconfirmed.")
                    "accepted"->t("El servidor aceptó la solicitud; no confirma que el equipo haya realizado la acción.","The server accepted the request; this does not confirm the device performed the action.")
                    "rejected"->t("El servidor rechazó la solicitud. No se repetirá automáticamente.","The server rejected the request. No automatic retry.")
                    "error"->if(state.code=="unconfirmed") t("Resultado desconocido; la acción puede haberse aplicado. Comprueba el equipo antes de repetir.","Outcome unknown; the action may have taken effect. Check the device before repeating.") else t("No se ha enviado la acción: contexto cambiado o verificación no disponible.","Action not sent: context changed or verification unavailable.")
                    else->""
                }
                if(status.isNotEmpty()) Text(status,modifier=Modifier.testTag("general_actions_status"))
                Text(t("Un intento por apertura, sin reenvíos automáticos. Cerrar no revierte una solicitud enviada. Un cambio de conexión no demuestra éxito.","One attempt per opening, no automatic resend. Closing does not undo a sent request. A connection change does not establish success."),style=MaterialTheme.typography.bodySmall)
            },footer={
                if(review!=null && !submitted) TextButton(enabled=state.phase=="ready",onClick={submitted=editor.submit(requireNotNull(review))},modifier=Modifier.testTag("general_actions_confirm")) {Text(t("Confirmar y enviar una vez","Confirm and send once"))}
                if(review!=null && !submitted && state.phase=="ready") TextButton(onClick={review=null}) {Text(t("Volver","Back"))}
                TextButton(onClick=close) {Text(t("Cerrar","Close"))}
            },subtitle=t("Acciones","Actions"),tagPrefix="device_actions")
            if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={discard=false},title={Text(t("Cerrar Acciones","Close Actions"))},text={Text(t("Se cerrará el seguimiento. Una acción ya enviada puede ejecutarse y no se revierte al cerrar.","Monitoring will close. An already sent action may execute and closing does not undo it."))},confirmButton={TextButton(onClick={editor.close();opened=false}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
        }
    }
}
