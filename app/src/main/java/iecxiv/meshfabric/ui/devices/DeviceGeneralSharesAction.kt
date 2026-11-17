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

private fun generalSharesDate(value:Long?,es:Boolean):String=if(value==null || value==0L) {if(es) "No informado" else "Not reported"} else DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,if(es) Locale("es") else Locale.ENGLISH).format(Date(value))
@Composable internal fun DeviceGeneralSharesAction(node:MeshNode) {
    val source=LocalDeviceDetailsClient.current
    val actor=source?.userInfo?.collectAsState()?.value?.id.orEmpty()
    val connected=source?.connectionState?.collectAsState()?.value==true
    val identity=Triple(source?.serverUrl,source?.authCookie,source?.ignoreSsl)
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    key(source,identity,actor,node.id) {
        var opened by remember {mutableStateOf(false)}
        OutlinedButton(enabled=connected && actor.isNotBlank() && source?.isUserLogout==false,onClick={opened=true},modifier=Modifier.testTag("general_shares")) {Text(t("Compartir","Share"))}
        if(opened) {
            var retry by remember {mutableIntStateOf(0)}
            val reader=remember(retry) {DeviceGeneralSharesReader(source,node.id)}
            val state by reader.state.collectAsState()
            val owner=LocalLifecycleOwner.current
            DisposableEffect(reader,owner) {
                val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) reader.close()}
                owner.lifecycle.addObserver(observer)
                if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) reader.start() else reader.close()
                onDispose {owner.lifecycle.removeObserver(observer);reader.close()}
            }
            DeviceGeneralFrame(node,{opened=false},body={
                Text(t("Solo consulta de accesos compartidos devueltos por el servidor para este equipo. En esta fase no se crean ni revocan enlaces, ni se muestran o copian sus credenciales.","Read-only inspection of sharing entries returned by the server for this device. This phase does not create or revoke links or display or copy their credentials."),style=MaterialTheme.typography.bodySmall)
                Text(t("Es una instantánea, no una lista de sesiones activas ni prueba de que un enlace siga siendo utilizable. No requiere que el agente esté conectado.","This is a snapshot, not a list of active sessions or proof that a link remains usable. The agent does not need to be online."),style=MaterialTheme.typography.bodySmall)
                when(state.phase) {
                    "loading"->{LinearProgressIndicator(Modifier.fillMaxWidth());Text(t("Verificando permisos y consultando accesos…","Verifying permissions and reading sharing entries…"))}
                    "ready"->{
                        Text(t("Consultado: ","Read at: ")+generalSharesDate(state.loadedAt,es),style=MaterialTheme.typography.bodySmall)
                        if(state.shares.isEmpty()) Text(t("El servidor no ha devuelto accesos compartidos para este equipo.","The server returned no sharing entries for this device."),modifier=Modifier.testTag("general_shares_empty"))
                        else Text(t("Accesos devueltos: ","Entries returned: ")+state.shares.size)
                        state.shares.forEachIndexed {index,share->
                            HorizontalDivider()
                            Text(t("Acceso ","Entry ")+(index+1),style=MaterialTheme.typography.titleSmall)
                            Text(t("Invitado: ","Guest: ")+share.guest.ifBlank {t("No informado","Not reported")})
                            Text(t("Usuario propietario informado: ","Reported owner: ")+share.owner.ifBlank {t("No informado","Not reported")})
                            val protocols=share.protocols?.let {p->buildList {if(p and 1L!=0L) add(t("Terminal","Terminal"));if(p and 2L!=0L) add(t("Escritorio","Desktop"));if(p and 4L!=0L) add(t("Archivos","Files"));if(p and 7L.inv()!=0L) add(t("Otros bits: ","Other bits: ")+(p and 7L.inv()))}.joinToString(", ")}
                            Text(t("Protocolos informados: ","Reported protocols: ")+protocols.orEmpty().ifBlank {t("No informado","Not reported")})
                            Text(t("Escritorio solo consulta: ","View-only desktop: ")+when(share.viewOnly) {true->t("Sí","Yes");false->t("No","No");null->t("No informado","Not reported")})
                            Text(t("Inicio informado: ","Reported start: ")+generalSharesDate(share.start,es))
                            Text(t("Vencimiento informado: ","Reported expiry: ")+generalSharesDate(share.expires,es))
                        }
                    }
                    else->Text(when(state.code) {
                        "denied"->t("Consulta no autorizada: se requieren permisos de control remoto y de compartir con invitados.","Inspection not authorized: remote-control and guest-sharing permissions are required.")
                        "timeout"->t("Tiempo agotado. No se ha confirmado la lista de accesos.","Timed out. The sharing list was not confirmed.")
                        else->t("Datos, identidad o conexión no verificables. No se muestra una lista vacía como si estuviera confirmada.","Data, identity or connection could not be verified. An empty list is not presented as confirmed.")
                    },color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("general_shares_error"))
                }
            },footer={
                TextButton(enabled=state.phase!="loading",onClick={retry++},modifier=Modifier.testTag("general_shares_reload")) {Text(t("Recargar","Reload"))}
                TextButton(onClick={opened=false}) {Text(t("Cerrar","Close"))}
            },subtitle=t("Compartir · Consulta","Share · Read-only"),tagPrefix="device_shares")
        }
    }
}
