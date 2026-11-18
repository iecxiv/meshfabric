package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.model.MeshNode

@Composable internal fun DeviceManualEventAction(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,identity,actor,node.id) {
        var opened by remember {mutableStateOf(false)}
        var history by remember {mutableStateOf(false)}
        DeviceGeneralActionButton(label=t("Registrar evento","Log event"),enabled=connected && actor.isNotBlank() && source?.isUserLogout==false,onClick={opened=true},modifier=Modifier.testTag("general_log_event"))
        if(opened) {
            var retry by remember {mutableIntStateOf(0)}
            var text by remember {mutableStateOf("")}
            var review by remember {mutableStateOf<String?>(null)}
            var submitted by remember {mutableStateOf(false)}
            val editor=remember(retry) {DeviceManualEventEditor(source,node.id)}
            val state by editor.state.collectAsState()
            val owner=LocalLifecycleOwner.current
            DisposableEffect(editor,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) editor.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) editor.start() else editor.close()
                onDispose {owner.lifecycle.removeObserver(observer);editor.close()}
            }
            val valid=runCatching {manualEventEncoded(text)}.isSuccess
            val status=when {
                state.phase=="loading"->t("Verificando identidad, dispositivo y permiso de administración…","Verifying identity, device and management permission…")
                state.phase=="checking"->t("Reverificando antes del envío…","Rechecking before send…")
                state.phase=="sent"->t("Solicitud enviada; registro sin confirmar. Comprueba Eventos antes de repetir.","Request sent; event recording unconfirmed. Check Events before repeating.")
                state.code=="unconfirmed"->t("Resultado sin confirmar. Puede haberse registrado. Comprueba Eventos; no se reenviará automáticamente.","Outcome unconfirmed. It may have been recorded. Check Events; no automatic resend.")
                state.code=="denied"->t("No se ha enviado: se requiere el permiso verificado de administrar el dispositivo.","Not sent: verified device management permission is required.")
                state.phase=="error"->t("Conexión o verificación interrumpida. No se confirma ningún registro.","Connection or verification interrupted. No event recording is confirmed.")
                else->""
            }
            iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={opened=false},title={Text(t("Registrar evento","Log event"))},
                text={Column(Modifier.fillMaxWidth().heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(node.name,style=MaterialTheme.typography.titleSmall)
                    Text(t("Añade una entrada manual al historial de este dispositivo. Es independiente del borrador de General.","Adds a manual entry to this device history. Independent of the General draft."),style=MaterialTheme.typography.bodySmall)
                    if(review==null && !submitted) {
                        OutlinedTextField(text,{text=it},label={Text(t("Texto del evento","Event text"))},minLines=2,maxLines=6,enabled=state.canSubmit,modifier=Modifier.fillMaxWidth().testTag("general_event_text"))
                        Text(t("Hasta 1024 caracteres; el texto codificado para el envío también tiene límite. Si no permite revisar, reduce la longitud o elimina caracteres de control.","Up to 1024 characters; encoded wire text is also limited. If review is disabled, shorten the text or remove control characters."),style=MaterialTheme.typography.bodySmall)
                    } else Text(review.orEmpty(),modifier=Modifier.testTag("general_event_review"))
                    if(state.phase in setOf("loading","checking")) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(status.isNotEmpty()) Text(status,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("general_event_status"))
                    Text(t("El servidor puede no devolver acuse. Un envío no confirma el registro. Solo un intento por apertura; cerrar no revierte un envío. No introduzcas contraseñas ni tokens.","The server may not acknowledge. Sending does not confirm recording. One attempt per opening; closing does not undo a send. Do not enter passwords or tokens."),style=MaterialTheme.typography.bodySmall)
                    if(submitted) TextButton(onClick={history=true}) {Text(t("Comprobar en Eventos","Check in Events"))}
                    if(!submitted && state.phase=="error") TextButton(onClick={retry++}) {Text(t("Recargar permisos","Reload permissions"))}
                }},
                confirmButton={if(!submitted) TextButton(enabled=state.canSubmit && (review!=null || valid),onClick={
                    if(review==null) review=text else submitted=editor.submit(requireNotNull(review))
                },modifier=Modifier.testTag("general_event_submit")) {Text(if(review==null) t("Revisar evento","Review event") else t("Enviar evento","Send event"))}},
                dismissButton={Row {
                    if(review!=null && !submitted) TextButton(onClick={review=null}) {Text(t("Volver","Back"))}
                    TextButton(onClick={opened=false}) {Text(t("Cerrar","Close"))}
                }})
        }
        if(history) DeviceEventsDialog(node) {history=false}
    }
}
