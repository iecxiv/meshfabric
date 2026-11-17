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
import iecxiv.meshfabric.ui.management.ActionFeedbackDialogs
import iecxiv.meshfabric.ui.management.commandInterpreters
import iecxiv.meshfabric.ui.management.defaultInterpreterKey

@Composable internal fun DeviceGeneralRunAction(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val inventory=source?.groupedDevices?.collectAsState()?.value.orEmpty()
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,identity,actor,node.id) {
        var opened by remember {mutableStateOf(false)}
        OutlinedButton(enabled=connected && actor.isNotBlank() && source?.isUserLogout==false,onClick={opened=true},modifier=Modifier.testTag("general_run")) {Text(t("Ejecutar","Run"))}
        if(opened) {
            var retry by remember {mutableIntStateOf(0)}
            var command by remember {mutableStateOf("")}
            var interpreter by remember {mutableStateOf("")}
            var review by remember {mutableStateOf<GeneralRunDraft?>(null)}
            var submitted by remember {mutableStateOf(false)}
            var discard by remember {mutableStateOf(false)}
            val editor=remember(retry) {DeviceGeneralRunEditor(source,node.id,t("Ejecutar · ","Run · ")+node.name)}
            val state by editor.state.collectAsState()
            val feedback by editor.feedback.state.collectAsState()
            val owner=LocalLifecycleOwner.current
            DisposableEffect(editor,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) editor.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) editor.start() else editor.close()
                onDispose {owner.lifecycle.removeObserver(observer);editor.close()}
            }
            val meta=state.meta
            LaunchedEffect(meta?.os) {if(meta!=null && interpreter.isBlank()) interpreter=defaultInterpreterKey(meta.os)}
            val allowed=state.phase=="ready" && meta!=null && generalRunAvailable(node.id,meta,inventory)
            val choices=meta?.let {commandInterpreters(it.os)}.orEmpty()
            val valid=generalRunTextValid(command) && choices.any {it.key==interpreter}
            val close:()->Unit={if(command.isNotBlank() || submitted) discard=true else opened=false}
            val status=when {
                state.phase=="loading"->t("Verificando dispositivo, agente y permiso de ejecutar comandos…","Verifying device, agent and run-command permission…")
                state.phase=="checking"->t("Reverificando antes del envío…","Rechecking before send…")
                state.phase=="sent"->t("Solicitud enviada. Consulta la ventana de salida; no confirma por sí sola una finalización correcta.","Request sent. Check the output window; this does not by itself confirm successful completion.")
                state.code=="changed"->t("El contexto revisado cambió. No se ha enviado el comando. Cierra y revisa de nuevo.","The reviewed context changed. Command not sent. Close and review again.")
                state.code=="unavailable"->t("Se requiere un grupo con agente, agente conectado y permiso específico de ejecutar comandos.","An agent-based group, connected agent and specific run-command permission are required.")
                state.code=="unconfirmed"->t("Resultado sin confirmar. El comando puede estar ejecutándose; no se reenviará automáticamente.","Outcome unconfirmed. The command may be running; no automatic resend.")
                state.phase=="error"->t("Conexión o verificación no disponible. No se confirma ejecución.","Connection or verification unavailable. Execution is not confirmed.")
                else->""
            }
            DeviceGeneralFrame(node,close,body={
                Text(t("El comando se envía solo a este equipo, en el contexto del servicio del agente, no como una terminal interactiva. Puede modificar o eliminar datos. No introduzcas contraseñas ni tokens.","The command is sent only to this device in the agent service context, not as an interactive terminal. It can modify or delete data. Do not enter passwords or tokens."),style=MaterialTheme.typography.bodySmall)
                if(review==null && !submitted) {
                    if(meta!=null) {
                        Text(t("Intérprete","Interpreter"),style=MaterialTheme.typography.titleSmall)
                        choices.forEach {choice->
                            Row {RadioButton(selected=interpreter==choice.key,onClick={interpreter=choice.key},enabled=allowed);Text(choice.label)}
                        }
                        if(defaultInterpreterKey(meta.os).isBlank()) Text(t("Sistema no identificado: elige explícitamente el intérprete del equipo remoto.","Unidentified system: explicitly select the remote interpreter."),style=MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(command,{command=it},enabled=allowed,label={Text(t("Comando o script","Command or script"))},minLines=4,maxLines=10,modifier=Modifier.fillMaxWidth().testTag("general_run_text"))
                    Text(t("Máximo 64000 caracteres.","Up to 64000 characters."),style=MaterialTheme.typography.bodySmall)
                } else review?.let {draft->
                    Text(t("Intérprete: ","Interpreter: ")+draft.interpreter)
                    Text(draft.command,modifier=Modifier.testTag("general_run_review"))
                }
                if(state.phase in setOf("loading","checking")) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(status.isNotEmpty()) Text(status,style=MaterialTheme.typography.bodySmall)
                feedback.commands.firstOrNull()?.let {result->
                    Text(result.message,style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={editor.feedback.openCommand(result.id)}) {Text(t("Ver salida y estado","View output and status"))}
                }
                Text(t("Solo un intento por apertura. Cerrar no detiene el proceso remoto y finaliza la recepción local de salida. No hay reintentos automáticos.","One attempt per opening. Closing does not stop the remote process and ends local output reception. No automatic retries."),style=MaterialTheme.typography.bodySmall)
                if(!submitted && state.phase=="error") TextButton(onClick={retry++}) {Text(t("Reverificar","Recheck"))}
            },footer={
                if(!submitted) TextButton(enabled=allowed && (review!=null || valid),onClick={
                    if(review==null) review=GeneralRunDraft(command,interpreter,requireNotNull(meta).os)
                    else submitted=editor.submit(requireNotNull(review))
                },modifier=Modifier.testTag("general_run_submit")) {Text(if(review==null) t("Revisar comando","Review command") else t("Enviar una vez","Send once"))}
                if(review!=null && !submitted) TextButton(onClick={review=null}) {Text(t("Volver","Back"))}
                TextButton(onClick=close) {Text(t("Cerrar","Close"))}
            },subtitle=t("Ejecutar","Run"),tagPrefix="device_run")
            ActionFeedbackDialogs(editor.feedback)
            if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={discard=false},title={Text(t("Cerrar Ejecutar","Close Run"))},text={Text(t("Se perderá el borrador y se cerrará el seguimiento local de salida. Un comando ya enviado no se detiene ni se revierte.","The draft will be lost and local output monitoring will close. An already sent command is not stopped or undone."))},confirmButton={TextButton(onClick={opened=false}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
        }
    }
}
