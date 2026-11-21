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

@Composable internal fun DeviceSharingCreateScreen(node:MeshNode,rights:Long,onBack:()->Unit,onCreated:()->Unit) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val context=LocalContext.current
    val connected=source?.connectionState?.collectAsState()?.value==true && source?.isUserLogout==false
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,source?.serverUrl,source?.authCookie,actor,node.id) {
        val zone=remember {java.time.ZoneId.systemDefault()}
        val initial=remember {shareInitialForm(zone)}
        var form by remember {mutableStateOf(initial)}
        val guest=form.guest
        var formError by remember {mutableStateOf(false)}
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
        val close:()->Unit={if(writer==null && review!=null) review=null else if(busy || writer==null && form!=initial) discard=true else onBack()}
        if(!discard) DeviceGeneralFrame(node,close,inline=true,body={
            if(writer==null && review==null) DeviceSharingForm(form,rights,zone,es) {form=it;formError=false}
            review?.let {DeviceSharingReview(it,form.consent,zone,es)}
            if(formError) Text(t("Revisa nombre, permisos y fechas (aaaa-MM-dd HH:mm). No se admiten horas pasadas, invertidas, ambiguas o inexistentes. Si la revisión caducó, revisa de nuevo.","Check name, permissions and dates (yyyy-MM-dd HH:mm). Past, reversed, ambiguous or nonexistent times are not accepted. If the review expired, review again."),color=MaterialTheme.colorScheme.error)
            state?.let {s->
                if(busy) iecxiv.meshfabric.ui.components.RoutedViewLoadingIndicator(Modifier.fillMaxWidth())
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
                        val clip=ClipData.newPlainText(t("Acceso compartido","Shared access"),url)
                        if(Build.VERSION.SDK_INT>=24) clip.description.extras=PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
                        manager.setPrimaryClip(clip)
                    }.isSuccess
                },modifier=Modifier.testTag("general_share_copy")) {Text(t("Copiar enlace","Copy link"))}
                if(copied) Text(t("Enlace copiado. El portapapeles contiene una credencial de acceso.","Link copied. The clipboard contains an access credential."),style=MaterialTheme.typography.bodySmall)
            }
        },footer={
            if(writer==null) TextButton(enabled=(review==null || connected) && generalShareGuestValid(guest.trim()) && generalShareAccessAllowed(rights,form.access),onClick={
                if(review==null) runCatching {shareFormDraft(form,node.meshid,System.currentTimeMillis()/1000L,zone)}.onSuccess {review=it;formError=false}.onFailure {formError=true}
                else runCatching {requireNotNull(review).also {generalShareValidateOptions(it,System.currentTimeMillis()/1000L)}}.onSuccess {writer=DeviceGeneralShareCreator(source,node.id,it);formError=false}.onFailure {review=null;formError=true}
            },modifier=Modifier.testTag("general_share_create_confirm")) {Text(if(review==null) t("Revisar enlace","Review link") else t("Crear una vez","Create once"))}
            if(writer==null && review!=null) TextButton(onClick={review=null}) {Text(t("Volver","Back"))}
            TextButton(onClick=close) {Text(t("Volver","Back"))}
        },subtitle=t("Crear enlace","Create link"),tagPrefix="device_share_create")
        if(discard) iecxiv.meshfabric.ui.management.UserActionSurface(inline=true,onDismissRequest={discard=false},title={Text(t("Cerrar creación","Close creation"))},text={Text(t("Se perderá el borrador o se cerrará el seguimiento. Cerrar no revoca un acceso que ya se haya creado ni borra una copia del portapapeles.","The draft will be lost or monitoring will close. Closing does not revoke an already created entry or erase a clipboard copy."))},confirmButton={TextButton(onClick={writer?.close();onBack()}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
    }
}
