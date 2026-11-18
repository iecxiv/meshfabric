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
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private fun generalSharesDate(value:Long?,es:Boolean):String=if(value==null || value==0L) {if(es) "No informado" else "Not reported"} else DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,if(es) Locale.forLanguageTag("es") else Locale.ENGLISH).format(Date(value))
@Composable internal fun DeviceGeneralSharesAction(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,identity,actor,node.id) {
        var opened by remember {mutableStateOf(false)}
        DeviceGeneralActionButton(label=t("Compartir","Share"),enabled=connected && actor.isNotBlank() && source?.isUserLogout==false,onClick={opened=true},modifier=Modifier.testTag("general_shares"))
        if(opened) {
            var retry by remember {mutableIntStateOf(0)}
            var review by remember {mutableStateOf<GeneralShare?>(null)}
            var revoker by remember {mutableStateOf<DeviceGeneralShareRevoker?>(null)}
            var discard by remember {mutableStateOf(false)}
            var refreshed by remember {mutableStateOf(false)}
            val reader=remember(retry) {DeviceGeneralSharesReader(source,node.id)}
            val state by reader.state.collectAsState()
            val result=revoker?.state?.collectAsState()?.value
            val busy=result?.phase in setOf("checking","verifying")
            val owner=LocalLifecycleOwner.current
            DisposableEffect(reader,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) reader.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) reader.start() else reader.close()
                onDispose {owner.lifecycle.removeObserver(observer);reader.close()}
            }
            revoker?.let {writer->DisposableEffect(writer,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) writer.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) writer.start() else writer.close()
                onDispose {owner.lifecycle.removeObserver(observer);writer.close()}
            }}
            LaunchedEffect(result?.phase) {if(result?.phase=="absent" && !refreshed) {refreshed=true;retry++}}
            val close:()->Unit={if(busy) discard=true else opened=false}
            DeviceGeneralFrame(node,close,body={
                Text(t("Consulta de accesos compartidos y creación de enlaces temporales de escritorio solo consulta. Puedes revisar y solicitar la revocación de un acceso propio por apertura. Las credenciales de accesos existentes no se muestran ni copian.","Inspect sharing entries and create temporary view-only desktop links. You may review and request revocation of one own entry per opening. Existing entry credentials are not displayed or copied."),style=MaterialTheme.typography.bodySmall)
                DeviceGeneralShareCreateAction(node,enabled=connected && state.phase=="ready" && revoker==null && review==null,onCreated={retry++})
                Text(t("Es una instantánea, no una lista de sesiones activas. No requiere agente conectado. La ausencia tras releer no demuestra que se hayan cerrado sesiones ya establecidas. No hay reenvíos automáticos.","This is a snapshot, not a list of active sessions. No online agent is required. Absence on readback does not establish that existing sessions were closed. No automatic resends."),style=MaterialTheme.typography.bodySmall)
                result?.let {r->Text(when(r.phase) {
                    "checking"->t("Reverificando identidad, permisos y el acceso revisado; aún no se ha enviado la revocación.","Rechecking identity, permissions and reviewed entry; revocation has not been sent yet.")
                    "verifying"->t("Solicitud enviada; verificando la lista. Revocación sin confirmar.","Request sent; checking the list. Revocation unconfirmed.")
                    "absent"->t("El acceso ya no figura en la lista releída del servidor. No acredita cierre de sesiones activas.","The entry is no longer present in the server's reread list. This does not establish closure of active sessions.")
                    else->if(r.attempted) t("Resultado sin confirmar. La revocación puede haberse aplicado; recarga antes de repetir.","Outcome unconfirmed. Revocation may have taken effect; reload before repeating.") else t("No se ha enviado la revocación: datos cambiados o verificación no disponible.","Revocation was not sent: changed data or verification unavailable.")
                },style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("general_share_revoke_status"))}
                when(state.phase) {
                    "loading"->{LinearProgressIndicator(Modifier.fillMaxWidth());Text(t("Verificando permisos y consultando accesos…","Verifying permissions and reading sharing entries…"))}
                    "ready"->{
                        Text(t("Consultado: ","Read at: ")+generalSharesDate(state.loadedAt,es),style=MaterialTheme.typography.bodySmall)
                        if(state.shares.isEmpty()) Text(t("El servidor no ha devuelto accesos compartidos para este equipo.","The server returned no sharing entries for this device."),modifier=Modifier.testTag("general_shares_empty"))
                        else Text(t("Accesos devueltos: ","Entries returned: ")+state.shares.size)
                        state.shares.forEachIndexed {index,share->
                            HorizontalDivider()
                            Text(t("Acceso ","Entry ")+(index+1),style=MaterialTheme.typography.titleSmall)
                            Text(t("Huella: ","Fingerprint: ")+generalShareFingerprint(share.id),style=MaterialTheme.typography.bodySmall)
                            Text(t("Invitado: ","Guest: ")+share.guest.ifBlank {t("No informado","Not reported")})
                            Text(t("Usuario propietario informado: ","Reported owner: ")+share.owner.ifBlank {t("No informado","Not reported")})
                            val protocols=share.protocols?.let {p->buildList {if(p and 1L!=0L) add(t("Terminal","Terminal"));if(p and 2L!=0L) add(t("Escritorio","Desktop"));if(p and 4L!=0L) add(t("Archivos","Files"));if(p and 7L.inv()!=0L) add(t("Otros bits: ","Other bits: ")+(p and 7L.inv()))}.joinToString(", ")}
                            Text(t("Protocolos informados: ","Reported protocols: ")+protocols.orEmpty().ifBlank {t("No informado","Not reported")})
                            Text(t("Escritorio solo consulta: ","View-only desktop: ")+when(share.viewOnly) {true->t("Sí","Yes");false->t("No","No");null->t("No informado","Not reported")})
                            Text(t("Inicio informado: ","Reported start: ")+generalSharesDate(share.start,es))
                            Text(t("Vencimiento informado: ","Reported expiry: ")+generalSharesDate(share.expires,es))
                            if(share.owner==actor && actor.isNotBlank()) TextButton(enabled=connected && revoker==null && review==null,onClick={review=share},modifier=Modifier.testTag("general_share_revoke_review")) {Text(t("Revisar revocación","Review revocation"))}
                        }
                    }
                    else->Text(when(state.code) {
                        "denied"->t("Consulta no autorizada: se requieren permisos de control remoto y de compartir con invitados.","Inspection not authorized: remote-control and guest-sharing permissions are required.")
                        "timeout"->t("Tiempo agotado. No se ha confirmado la lista de accesos.","Timed out. The sharing list was not confirmed.")
                        else->t("Datos, identidad o conexión no verificables. No se muestra una lista vacía como si estuviera confirmada.","Data, identity or connection could not be verified. An empty list is not presented as confirmed.")
                    },color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("general_shares_error"))
                }
            },footer={
                TextButton(enabled=state.phase!="loading" && !busy && review==null,onClick={retry++},modifier=Modifier.testTag("general_shares_reload")) {Text(t("Recargar","Reload"))}
                TextButton(onClick=close) {Text(t("Cerrar","Close"))}
            },subtitle=t("Compartir","Share"),tagPrefix="device_shares")
            review?.let {share->iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={review=null},title={Text(t("Revocar acceso propio","Revoke own sharing entry"))},text={Column {
                Text(t("Dispositivo: ","Device: ")+node.name);Text(node.id)
                Text(t("Invitado: ","Guest: ")+share.guest);Text(t("Propietario: ","Owner: ")+share.owner)
                Text(t("Huella: ","Fingerprint: ")+generalShareFingerprint(share.id))
                Text(t("Máscara de protocolos: ","Protocol mask: ")+(share.protocols?.toString() ?: t("No informado","Not reported")))
                Text(t("Vencimiento: ","Expiry: ")+generalSharesDate(share.expires,es))
                Text(t("Se enviará una sola solicitud para este acceso tras comprobar que no ha cambiado. No se puede deshacer desde este diálogo.","One request will be sent for this entry after checking it has not changed. This cannot be undone from this dialog."))
            }},confirmButton={TextButton(enabled=connected && revoker==null,onClick={revoker=DeviceGeneralShareRevoker(source,node.id,share);review=null},modifier=Modifier.testTag("general_share_revoke_confirm")) {Text(t("Revocar una vez","Revoke once"))}},dismissButton={TextButton(onClick={review=null}) {Text(t("Cancelar","Cancel"))}})}
            if(discard) iecxiv.meshfabric.ui.components.AppAlertDialog(onDismissRequest={discard=false},title={Text(t("Cerrar Compartir","Close Share"))},text={Text(t("Se cerrará el seguimiento local. Cerrar no revierte una revocación que ya se haya enviado y su resultado puede quedar sin confirmar.","Local monitoring will close. Closing does not undo an already sent revocation and its outcome may remain unconfirmed."))},confirmButton={TextButton(onClick={revoker?.close();opened=false}) {Text(t("Cerrar","Close"))}},dismissButton={TextButton(onClick={discard=false}) {Text(t("Volver","Back"))}})
        }
    }
}
