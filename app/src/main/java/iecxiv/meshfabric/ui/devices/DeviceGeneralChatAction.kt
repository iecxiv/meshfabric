package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.MeshCentralClient

private class HostedDeviceChat(val source:MeshCentralClient,val node:MeshNode,initialY:Float) {
    val session=DeviceGeneralChatSession(source,node.id)
    val bubblePosition=DeviceChatBubblePosition(initialY)
    var draft by mutableStateOf("")
    var minimized by mutableStateOf(false)
    var dismissGeneral:()->Unit={}
}
private val LocalDeviceChatOpen=staticCompositionLocalOf<((MeshNode,()->Unit)->Unit)?> {null}

@Composable internal fun DeviceChatHost(source:MeshCentralClient,content:@Composable ()->Unit) {
    val user by source.userInfo.collectAsState()
    val connected by source.connectionState.collectAsState()
    val identity=Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
    key(source,identity,user?.id,source.isUserLogout) {
        val chats=remember {mutableStateListOf<HostedDeviceChat>()}
        val open:(MeshNode,()->Unit)->Unit={node,dismiss->
            if(connected && !source.isUserLogout && !user?.id.isNullOrBlank()) {
                chats.forEach {it.minimized=true}
                val chat=chats.firstOrNull {it.node.id==node.id} ?: HostedDeviceChat(source,node,(0.25f+chats.size*0.12f).coerceAtMost(0.85f)).also {chats.add(it)}
                chat.dismissGeneral=dismiss
                chat.minimized=false
            }
        }
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalDeviceChatOpen provides open) {content()}
            val owner=LocalLifecycleOwner.current
            chats.forEach {chat->key(chat.node.id) {
                DisposableEffect(chat,owner) {
                    val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) chat.session.close()}
                    owner.lifecycle.addObserver(observer)
                    if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) chat.session.start() else chat.session.close()
                    onDispose {owner.lifecycle.removeObserver(observer);chat.session.close()}
                }
                if(!chat.minimized) DeviceChatWindow(chat) {chats.remove(chat)}
            }}
            if(chats.any {it.minimized}) DeviceChatBubbles(
                chats.filter {it.minimized}.map {DeviceChatBubble(it.node.id,it.node.name,it.bubblePosition)},
                onRestore={id->chats.forEach {it.minimized=it.node.id!=id}},
                onClose={id->chats.firstOrNull {it.node.id==id}?.let {it.session.close();chats.remove(it)}})
        }
    }
}

@Composable internal fun DeviceGeneralChatAction(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val open=LocalDeviceChatOpen.current
    val dismissGeneral=LocalDeviceGeneralClose.current
    DeviceGeneralActionButton(label="Chat",enabled=open!=null && connected && actor.isNotBlank() && source?.isUserLogout==false,onClick={open?.invoke(node,dismissGeneral ?: {})},modifier=Modifier.testTag("general_chat"))
}

@Composable private fun DeviceChatWindow(chat:HostedDeviceChat,onClosed:()->Unit) {
    val node=chat.node
    val session=chat.session
    val state by session.state.collectAsState()
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    var discard by remember {mutableStateOf(false)}
    val close:()->Unit={if(chat.draft.isNotEmpty() || state.phase !in setOf("loading","ready","closed") || state.entries.isNotEmpty()) discard=true else onClosed()}
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
        Text(t("Solo texto: sin archivos, audio ni vídeo. Sin confirmaciones de entrega o lectura. Historial local limitado a 100 mensajes y 64000 caracteres, no guardado en disco; el servidor puede registrar la sesión según su política. Minimizar conserva el chat y el borrador dentro de la aplicación. Cerrar o pasar la app a segundo plano desconecta este chat; no retira mensajes ni cierra el navegador remoto.","Text only: no files, audio or video. No delivery or read receipts. Local history limited to 100 messages and 64000 characters, not saved to disk; the server may record the session according to policy. Minimizing keeps the chat and draft within the app. Closing or backgrounding the app disconnects this chat; it does not retract messages or close the remote browser."),style=MaterialTheme.typography.bodySmall)
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
        if(state.phase in setOf("connected","checking_send")) OutlinedTextField(chat.draft,{chat.draft=it},enabled=state.phase=="connected",label={Text(t("Mensaje, máximo 4096 caracteres","Message, up to 4096 characters"))},minLines=2,maxLines=5,
            trailingIcon={IconButton(enabled=generalChatCanSend(state.phase,chat.draft),onClick={if(session.send(chat.draft)) chat.draft=""},modifier=Modifier.testTag("general_chat_send")) {Icon(Icons.AutoMirrored.Filled.Send,contentDescription=t("Enviar","Send"))}},
            modifier=Modifier.fillMaxWidth().testTag("general_chat_text"))
    },footer={
        if(state.phase=="ready") TextButton(onClick={session.connect(requireNotNull(state.meta).mesh)},modifier=Modifier.testTag("general_chat_start")) {Text(t("Iniciar chat","Start chat"))}
        TextButton(onClick=close) {Text(t("Cerrar","Close"))}
    },subtitle="Chat",tagPrefix="device_chat",onMinimize={
        val dismiss=chat.dismissGeneral
        chat.dismissGeneral={}
        chat.minimized=true
        dismiss()
    })
    if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={discard=false},title={Text(t("Cerrar Chat","Close Chat"))},text={Text(t("Se desconectará el chat y se perderán el borrador y el historial locales. Los mensajes enviados no se retiran; el navegador remoto puede seguir abierto.","Chat will disconnect and the local draft and history will be lost. Sent messages are not retracted; the remote browser may remain open."))},confirmButton={TextButton(onClick={session.close();onClosed()}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
}
