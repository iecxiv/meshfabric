package iecxiv.meshfabric.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import iecxiv.meshfabric.data.model.MeshNode
import java.text.DateFormat
import java.util.Date

@Composable internal fun DeviceGeneralShareCreateAction(node:MeshNode,enabled:Boolean,onCreated:()->Unit) {
    val source=LocalDeviceDetailsClient.current
    val context=LocalContext.current
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    var opened by remember {mutableStateOf(false)}
    DeviceGeneralActionButton(t("Crear enlace temporal","Create temporary link"),enabled,{opened=true},Modifier.testTag("general_share_create"))
    if(opened) {
        var guest by remember {mutableStateOf("")}
        var minutes by remember {mutableIntStateOf(15)}
        var review by remember {mutableStateOf<GeneralShareCreateDraft?>(null)}
        var writer by remember {mutableStateOf<DeviceGeneralShareCreator?>(null)}
        var discard by remember {mutableStateOf(false)}
        var reveal by remember {mutableStateOf(false)}
        var copied by remember {mutableStateOf(false)}
        var notified by remember {mutableStateOf(false)}
        val state=writer?.state?.collectAsState()?.value
        val busy=state?.phase in setOf("checking","creating","verifying")
        val owner=LocalLifecycleOwner.current
        writer?.let {creator->DisposableEffect(creator,owner) {
            val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) creator.close()}
            owner.lifecycle.addObserver(observer)
            if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) creator.start() else creator.close()
            onDispose {owner.lifecycle.removeObserver(observer);creator.close()}
        }}
        LaunchedEffect(state?.phase) {if(state?.phase=="confirmed" && !notified) {notified=true;onCreated()}}
        val close:()->Unit={if(busy || writer==null && (guest.isNotEmpty() || review!=null)) discard=true else opened=false}
        DeviceGeneralFrame(node,close,body={
            Text(t("Enlace de acceso por posesión: la etiqueta del invitado no autentica al destinatario. Solo escritorio en modo consulta, sin terminal ni archivos. Se solicitan aviso y consentimiento remoto.","Bearer access link: the guest label does not authenticate the recipient. View-only desktop, no terminal or files. Remote notification and consent are requested."),style=MaterialTheme.typography.bodySmall)
            Text(t("Duración limitada, sin recurrencia. Revisa antes de crear. No se envía a nadie ni se copia automáticamente. Un intento por apertura; si el resultado es incierto, recarga Compartir antes de repetir.","Time limited, non-recurring. Review before creating. Not sent to anyone or copied automatically. One attempt per opening; if the outcome is uncertain, reload Share before repeating."),style=MaterialTheme.typography.bodySmall)
            if(writer==null && review==null) {
                OutlinedTextField(guest,{guest=it},label={Text(t("Etiqueta del invitado, máximo 128 caracteres","Guest label, up to 128 characters"))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("general_share_guest"))
                Row(horizontalArrangement=Arrangement.spacedBy(androidx.compose.ui.unit.Dp(6f))) {listOf(15,30,60).forEach {n->FilterChip(selected=minutes==n,onClick={minutes=n},label={Text("$n min")})}}
            }
            review?.let {d->
                Text(t("Invitado: ","Guest: ")+d.guest)
                Text(t("Grupo revisado: ","Reviewed group: ")+d.mesh)
                Text(t("Inicio: ","Start: ")+DateFormat.getDateTimeInstance().format(Date(d.start*1000L)))
                Text(t("Vencimiento: ","Expiry: ")+DateFormat.getDateTimeInstance().format(Date(d.end*1000L)))
                Text(t("El horario queda fijado. Si pasan más de dos minutos antes del envío, debes volver a revisar. La utilidad del enlace depende del agente y de las políticas del servidor.","Times are frozen. If more than two minutes pass before sending, review again. Link usability depends on the agent and server policies."),style=MaterialTheme.typography.bodySmall)
            }
            state?.let {s->
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(when(s.phase) {
                    "checking"->t("Verificando identidad, dispositivo, permisos y accesos existentes; aún no se ha creado.","Verifying identity, device, permissions and existing entries; not created yet.")
                    "creating","verifying"->t("Solicitud enviada; creación y alcance aún sin confirmar. El enlace no se revela hasta releerlo.","Request sent; creation and scope are still unconfirmed. The link is not revealed until readback.")
                    "confirmed"->t("Creación confirmada por respuesta correlacionada y relectura de propietario, alcance, consentimiento y fechas. No acredita conexión remota.","Creation confirmed by correlated response and readback of owner, scope, consent and dates. Does not establish remote connection.")
                    else->if(s.code=="expired") t("Vencimiento alcanzado según el reloj local. Enlace oculto.","Expiry reached according to the local clock. Link hidden.") else if(s.attempted) t("Resultado sin confirmar. El acceso puede existir. Recarga Compartir antes de crear otro.","Outcome unconfirmed. The entry may exist. Reload Share before creating another.") else t("No se ha enviado la creación. Revisa datos, antigüedad de la revisión, permisos y conexión HTTPS.","Creation was not sent. Check data, review age, permissions and HTTPS connection.")
                },modifier=Modifier.testTag("general_share_create_status"))
            }
            if(state?.phase=="confirmed") {
                TextButton(onClick={reveal=!reveal}) {Text(if(reveal) t("Ocultar enlace","Hide link") else t("Mostrar enlace","Show link"))}
                if(reveal) writer?.linkForUserAction()?.let {url->SelectionContainer {Text(url)}}
                TextButton(onClick={
                    copied=runCatching {
                        val url=requireNotNull(writer?.linkForUserAction());val manager=context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: error("Clipboard unavailable")
                        val clip=ClipData.newPlainText(t("Acceso temporal","Temporary access"),url)
                        if(Build.VERSION.SDK_INT>=24) clip.description.extras=PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
                        manager.setPrimaryClip(clip)
                    }.isSuccess
                },modifier=Modifier.testTag("general_share_copy")) {Text(t("Copiar enlace","Copy link"))}
                if(copied) Text(t("Enlace copiado. El portapapeles contiene una credencial de acceso.","Link copied. The clipboard contains an access credential."),style=MaterialTheme.typography.bodySmall)
            }
        },footer={
            if(writer==null) TextButton(enabled=generalShareGuestValid(guest),onClick={
                if(review==null) review=generalShareCreateDraft(guest,node.meshid,minutes,System.currentTimeMillis()/1000L)
                else writer=DeviceGeneralShareCreator(source,node.id,requireNotNull(review))
            },modifier=Modifier.testTag("general_share_create_confirm")) {Text(if(review==null) t("Revisar enlace","Review link") else t("Crear una vez","Create once"))}
            if(writer==null && review!=null) TextButton(onClick={review=null}) {Text(t("Volver","Back"))}
            TextButton(onClick=close) {Text(t("Cerrar","Close"))}
        },subtitle=t("Crear enlace temporal","Create temporary link"),tagPrefix="device_share_create")
        if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={discard=false},title={Text(t("Cerrar creación","Close creation"))},text={Text(t("Se perderá el borrador o se cerrará el seguimiento. Cerrar no revoca un acceso que ya se haya creado ni borra una copia del portapapeles.","The draft will be lost or monitoring will close. Closing does not revoke an already created entry or erase a clipboard copy."))},confirmButton={TextButton(onClick={writer?.close();opened=false}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
    }
}
