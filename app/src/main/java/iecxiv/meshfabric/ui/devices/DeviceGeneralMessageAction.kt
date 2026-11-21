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

@Composable internal fun DeviceGeneralMessageAction(node:MeshNode,embedded:Boolean=false,onBack:(()->Unit)?=null) {
    val openAction=LocalDeviceGeneralActionOpen.current
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val inventory=source?.groupedDevices?.collectAsState()?.value.orEmpty()
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,identity,actor,node.id) {
        var opened by remember {mutableStateOf(embedded)}
        if(!embedded) DeviceGeneralActionButton(label=t("Mensaje","Message"),enabled=connected && actor.isNotBlank() && source?.isUserLogout==false,onClick={if(openAction!=null) openAction(node,DeviceGeneralActionKind.MESSAGE) else opened=true},modifier=Modifier.testTag("general_message"))
        fun exit() {if(embedded) onBack?.invoke() else opened=false}
        if(opened) {
            val defaultTitle=remember {t("Mensaje remoto","Remote message")}
            var title by remember {mutableStateOf(defaultTitle)}
            var body by remember {mutableStateOf("")}
            var review by remember {mutableStateOf<GeneralMessageDraft?>(null)}
            var submitted by remember {mutableStateOf(false)}
            var discard by remember {mutableStateOf(false)}
            var retry by remember {mutableIntStateOf(0)}
            val editor=remember(retry) {DeviceGeneralMessageEditor(source,node.id)}
            val state by editor.state.collectAsState()
            val owner=LocalLifecycleOwner.current
            DisposableEffect(editor,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) editor.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) editor.start() else editor.close()
                onDispose {owner.lifecycle.removeObserver(observer);editor.close()}
            }
            val meta=state.meta
            val allowed=state.phase=="ready" && meta!=null && generalMessageAvailable(node.id,meta,inventory)
            val valid=generalMessageTextValid(title,body)
            val close:()->Unit={if(review!=null && !submitted) review=null else if(body.isNotEmpty() || title!=defaultTitle || submitted) discard=true else exit()}
            val status=when {
                state.phase=="loading"->t("Verificando agente, dispositivo y permisos…","Verifying agent, device and permissions…")
                state.phase=="checking"->t("Reverificando antes del envío…","Rechecking before send…")
                state.phase=="sent"->t("Solicitud enviada; recepción, visualización y lectura sin confirmar.","Request sent; receipt, display and reading unconfirmed.")
                state.phase=="accepted"->t("El servidor aceptó la solicitud; no confirma que el mensaje se haya mostrado o leído.","The server accepted the request; this does not confirm that the message was displayed or read.")
                state.phase=="rejected"->t("El servidor rechazó la solicitud. No se reenviará automáticamente.","The server rejected the request. No automatic resend.")
                state.code=="unavailable"->t("Se requiere agente conectado y permisos de control remoto y chat/notificaciones.","A connected agent and remote-control plus chat/notification permissions are required.")
                state.code=="changed"->t("El contexto revisado cambió. Este mensaje no se ha enviado.","The reviewed context changed. This message was not sent.")
                state.code=="unconfirmed"->t("Resultado sin confirmar. El mensaje puede haberse enviado; comprueba antes de repetir.","Outcome unconfirmed. The message may have been sent; check before repeating.")
                state.phase=="error"->t("Conexión o verificación no disponible. No se confirma entrega.","Connection or verification unavailable. Delivery is not confirmed.")
                else->""
            }
            if(!embedded || !discard) DeviceGeneralFrame(node,close,inline=embedded,body={
                Text(t("Mensaje al dispositivo seleccionado mediante el agente. No abre un chat y no utiliza avisos toast ni push móvil. Requiere soporte del agente y de la sesión gráfica remota.","Message to the selected device through its agent. Does not open chat or use toast notifications or mobile push. Requires agent and remote graphical-session support."),style=MaterialTheme.typography.bodySmall)
                if(review==null && !submitted) {
                    OutlinedTextField(title,{title=it},enabled=allowed,singleLine=true,label={Text(t("Título","Title"))},modifier=Modifier.fillMaxWidth().testTag("general_message_title"))
                    OutlinedTextField(body,{body=it},enabled=allowed,minLines=3,maxLines=8,label={Text(t("Mensaje","Message"))},modifier=Modifier.fillMaxWidth().testTag("general_message_body"))
                    Text(t("Título: hasta 128 caracteres. Mensaje: hasta 1024. No introduzcas contraseñas ni tokens.","Title: up to 128 characters. Message: up to 1024. Do not enter passwords or tokens."),style=MaterialTheme.typography.bodySmall)
                } else review?.let {draft->Text(draft.title,style=MaterialTheme.typography.titleSmall);Text(draft.body,modifier=Modifier.testTag("general_message_review"))}
                Text(t("Se solicita cierre automático a los 120 segundos; depende del soporte remoto. Enviar no acredita recepción ni lectura. Un intento por apertura, sin reenvíos automáticos. Cerrar no retira un mensaje enviado.","Automatic closing is requested after 120 seconds; remote support determines behavior. Sending does not establish receipt or reading. One attempt per opening, no automatic resends. Closing does not retract a sent message."),style=MaterialTheme.typography.bodySmall)
                if(state.phase in setOf("loading","checking")) iecxiv.meshfabric.ui.components.RoutedViewLoadingIndicator(Modifier.fillMaxWidth())
                if(status.isNotEmpty()) Text(status,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("general_message_status"))
                if(!submitted && state.phase=="error") TextButton(onClick={retry++}) {Text(t("Reverificar","Recheck"))}
            },footer={
                if(!submitted) TextButton(enabled=allowed && (review!=null || valid),onClick={
                    if(review==null) review=GeneralMessageDraft(title,body,requireNotNull(meta).mesh)
                    else submitted=editor.submit(requireNotNull(review))
                },modifier=Modifier.testTag("general_message_submit")) {Text(if(review==null) t("Revisar mensaje","Review message") else t("Enviar una vez","Send once"))}
                if(review!=null && !submitted) TextButton(onClick={review=null}) {Text(t("Volver","Back"))}
                TextButton(onClick=close) {Text(t("Cerrar","Close"))}
            },subtitle=t("Mensaje","Message"),tagPrefix="device_message")
            if(discard) DeviceGeneralActionSurface(embedded=embedded,onDismissRequest={discard=false},title={Text(t("Cerrar Mensaje","Close Message"))},text={Text(t("Se perderá el borrador local. Un mensaje ya enviado no se retira ni se revierte.","The local draft will be lost. An already sent message is not retracted or undone."))},confirmButton={TextButton(onClick={exit()}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
        }
    }
}
