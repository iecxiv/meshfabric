package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.model.MeshNode

@Composable internal fun DeviceGeneralChatAction(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,identity,actor,node.id) {
        var opened by remember {mutableStateOf(false)}
        OutlinedButton(enabled=connected && actor.isNotBlank() && source?.isUserLogout==false,onClick={opened=true},modifier=Modifier.testTag("general_chat")) {Text("Chat")}
        if(opened) {
            val session=remember {DeviceGeneralChatSession(source,node.id)}
            val state by session.state.collectAsState()
            var draft by remember {mutableStateOf("")}
            var discard by remember {mutableStateOf(false)}
            val owner=LocalLifecycleOwner.current
            DisposableEffect(session,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) session.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.start() else session.close()
                onDispose {owner.lifecycle.removeObserver(observer);session.close()}
            }
            val close:()->Unit={if(draft.isNotEmpty() || state.phase !in setOf("loading","ready","closed") || state.entries.isNotEmpty()) discard=true else opened=false}
            val status=when(state.phase) {
                "loading"->t("Verificando dispositivo, agente y permisos…","Verifying device, agent and permissions…")
                "ready"->t("Revisa el equipo y pulsa Iniciar chat. Todavía no se ha solicitado abrir el navegador remoto.","Review the device and press Start chat. Remote browser opening has not yet been requested.")
                "checking"->t("Reverificando antes de iniciar…","Rechecking before starting…")
                "connecting"->t("Conectando con el relay…","Connecting to the relay…")
                "waiting"->t("Apertura solicitada. Esperando el otro extremo; no confirma presencia humana.","Opening requested. Waiting for the other endpoint; human presence is not confirmed.")
                "connected"->t("Ambos extremos conectados; no confirma que alguien esté atendiendo o leyendo.","Both endpoints connected; this does not confirm anyone is attending or reading.")
                "checking_send"->t("Reverificando permisos antes de enviar…","Rechecking permissions before sending…")
                else->when(state.code) {
                    "waiting_timeout"->t("Espera agotada. No se ha confirmado conexión con el otro extremo.","Wait timed out. Connection to the other endpoint was not confirmed.")
                    "unavailable","denied","unverified"->t("Chat detenido: agente, grupo o permisos no disponibles o no verificables.","Chat stopped: agent, group or permissions unavailable or unverified.")
                    "invitation_unknown"->t("Apertura remota sin confirmar. No se repetirá automáticamente.","Remote opening unconfirmed. No automatic retry.")
                    else->t("Chat desconectado. No se reabrirá ni reenviará mensajes automáticamente.","Chat disconnected. No automatic reopening or message replay.")
                }
            }
            DeviceGeneralFrame(node,close,body={
                Text(status,modifier=Modifier.testTag("general_chat_status"))
                Text(t("Iniciar solicita al agente abrir el chat en el navegador del equipo seleccionado. Requiere agente conectado, permisos de control remoto y chat/notificaciones y un navegador remoto compatible. Otra sesión de chat con esta misma cuenta y equipo puede entrar en conflicto.","Starting asks the agent to open chat in the selected device's browser. Requires a connected agent, remote-control and chat/notification permissions and a compatible remote browser. Another chat with this same account and device may conflict."),style=MaterialTheme.typography.bodySmall)
                if(state.recorded) Text(t("El servidor indica que esta sesión está siendo grabada.","The server indicates that this session is being recorded."),color=MaterialTheme.colorScheme.error)
                Text(t("Solo texto: sin archivos, audio ni vídeo. Sin confirmaciones de entrega o lectura. Historial local limitado a 100 mensajes y 64000 caracteres, no guardado en disco; el servidor puede registrar la sesión según su política. Cerrar o pasar la app a segundo plano desconecta este chat; no retira mensajes ni cierra el navegador remoto.","Text only: no files, audio or video. No delivery or read receipts. Local history limited to 100 messages and 64000 characters, not saved to disk; the server may record the session according to policy. Closing or backgrounding the app disconnects this chat; it does not retract messages or close the remote browser."),style=MaterialTheme.typography.bodySmall)
                if(state.phase in setOf("loading","checking","connecting","checking_send")) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.entries.forEach {entry->
                    HorizontalDivider()
                    Text(if(entry.local) t("Tú","You") else t("Remoto","Remote"),style=MaterialTheme.typography.labelMedium)
                    SelectionContainer {Text(entry.text)}
                    if(entry.local) Text(when(entry.status) {
                        "checking"->t("Pendiente de verificación; aún no enviado.","Awaiting verification; not sent yet.")
                        "queued"->t("Encolado para envío; entrega y lectura sin confirmar.","Queued for sending; delivery and reading unconfirmed.")
                        "not_sent"->t("No enviado. No se reintentará automáticamente.","Not sent. No automatic retry.")
                        else->t("Resultado de envío desconocido; comprueba antes de repetir.","Send outcome unknown; check before repeating.")
                    },style=MaterialTheme.typography.bodySmall)
                }
                if(state.phase in setOf("connected","checking_send")) OutlinedTextField(draft,{draft=it},enabled=state.phase=="connected",label={Text(t("Mensaje, máximo 4096 caracteres","Message, up to 4096 characters"))},minLines=2,maxLines=5,modifier=Modifier.fillMaxWidth().testTag("general_chat_text"))
            },footer={
                if(state.phase=="ready") TextButton(onClick={session.connect(requireNotNull(state.meta).mesh)},modifier=Modifier.testTag("general_chat_start")) {Text(t("Iniciar chat","Start chat"))}
                if(state.phase in setOf("connected","checking_send")) TextButton(enabled=generalChatCanSend(state.phase,draft),onClick={if(session.send(draft)) draft=""},modifier=Modifier.testTag("general_chat_send")) {Text(t("Enviar","Send"))}
                TextButton(onClick=close) {Text(t("Cerrar","Close"))}
            },subtitle="Chat",tagPrefix="device_chat")
            if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={discard=false},title={Text(t("Cerrar Chat","Close Chat"))},text={Text(t("Se desconectará el chat y se perderán el borrador y el historial locales. Los mensajes enviados no se retiran; el navegador remoto puede seguir abierto.","Chat will disconnect and the local draft and history will be lost. Sent messages are not retracted; the remote browser may remain open."))},confirmButton={TextButton(onClick={session.close();opened=false}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
        }
    }
}
