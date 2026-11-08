package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

@Composable internal fun ProfileBroadcastDialog(session:ProfileSession,s:ProfileState,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val server=session.broadcastServerLabel()
    val scope=remember(s.id) { runCatching { profileBroadcastScope(requireNotNull(s.user),server) }.getOrNull() }
    var message by remember(s.id) { mutableStateOf("") }
    var review by remember(s.id) { mutableStateOf(false) }
    var approved by remember(s.id) { mutableStateOf(false) }
    var feedback by remember(s.id) { mutableStateOf("") }
    val scopeAllowed=scope!=null && profileBroadcastScopeAllowed(s,scope,server)
    val packet=scope?.let {profileBroadcastPacket(it,message,approved)}
    val canSend=packet!=null && profileBroadcastAllowed(s,packet,server)
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(if(review) t("Revisar Broadcast","Review Broadcast") else "Broadcast")},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(t("Sesiones conectadas del dominio actual","Connected sessions in the current domain"),style=MaterialTheme.typography.titleSmall)
            Text(scope?.optString("server").orEmpty(),style=MaterialTheme.typography.bodySmall)
            val domain=scope?.let {userDomain(it.getString("actor"))}
            Text(t("Dominio: ","Domain: ")+(domain?.ifBlank {t("Predeterminado","Default")} ?: t("No disponible","Unavailable")),style=MaterialTheme.typography.bodySmall)
            val realms=scope?.let {userRealmValues(it.opt("realms"))}.orEmpty()
            if(realms.isEmpty()) Text(t("Sin filtro por reinos administrativos.","No administrative-realm filter."),style=MaterialTheme.typography.bodySmall) else ProfileDisclosure(t("Reinos administrativos","Administrative realms"),"${realms.size} "+t("reinos; no es un recuento de destinatarios","realms; not a recipient count")) {for(realm in realms) Text(realm,style=MaterialTheme.typography.bodySmall)}
            Text(t("Solo el nodo que procese la solicitud. No incluye usuarios desconectados ni difusión a otros nodos. El servidor decide los destinatarios al procesarla.","Only the node processing the request. Offline users and other nodes are not included. The server determines recipients when processing it."),style=MaterialTheme.typography.bodySmall)
            if(review) {
                ManagementCard { SelectionContainer { Text(message,style=MaterialTheme.typography.bodyMedium) } }
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(approved,{approved=it},enabled=scopeAllowed);Text(t("Confirmo difundir este mensaje con el alcance indicado.","I confirm broadcasting this message with the stated scope."),Modifier.weight(1f)) }
            } else OutlinedTextField(message,{if(it.length<=4096) {message=it;approved=false;feedback=""}},label={Text(t("Mensaje","Message"))},supportingText={Text("${message.length}/512")},isError=message.isNotEmpty() && !profileBroadcastMessageValid(message),enabled=scopeAllowed,modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=6)
            if(!scopeAllowed) Text(t("La conexión, los permisos o el alcance han cambiado o no permiten enviar. Espera o cierra y vuelve a abrir para revisar.","The connection, rights or scope changed or do not allow sending. Wait or close and reopen to review."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(t("La aceptación del servidor no acredita recepción ni lectura. Ante un resultado sin confirmar, no se reenvía automáticamente.","Server acceptance does not prove receipt or reading. Unconfirmed requests are not automatically retried."),style=MaterialTheme.typography.bodySmall)
            if(feedback.isNotBlank()) Text(feedback,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=if(review) canSend else scopeAllowed && profileBroadcastMessageValid(message),onClick={if(!review) {review=true;approved=false} else if(packet!=null && session.submit(packet)) onDismiss() else feedback=t("No se ha enviado. Comprueba la conexión, el alcance y los permisos.","Not sent. Check the connection, scope and rights.")}) {Text(if(review) t("Enviar Broadcast","Send Broadcast") else t("Revisar","Review"))}},dismissButton={Row {if(review) TextButton(onClick={review=false;approved=false}) {Text(t("Volver","Back"))};TextButton(onClick=onDismiss){Text(t("Cancelar","Cancel"))}}})
}
