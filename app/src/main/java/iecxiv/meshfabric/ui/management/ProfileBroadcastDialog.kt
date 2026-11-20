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

@Composable internal fun ProfileBroadcastPane(session:ProfileSession,s:ProfileState,modifier:Modifier=Modifier,embedded:Boolean=false,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val server=session.broadcastServerLabel()
    val scope=remember(s.id) { runCatching { profileBroadcastScope(requireNotNull(s.user),server) }.getOrNull() }
    var message by remember(s.id) { mutableStateOf("") }
    var review by remember(s.id) { mutableStateOf(false) }
    var approved by remember(s.id) { mutableStateOf(false) }
    var feedback by remember(s.id) { mutableStateOf("") }
    var submitted by remember(s.id) {mutableStateOf(false)}
    val scopeAllowed=scope!=null && profileBroadcastScopeAllowed(s,scope,server)
    val packet=scope?.let {profileBroadcastPacket(it,message,approved)}
    val canSend=!submitted && packet!=null && profileBroadcastAllowed(s,packet,server)
    ProfileBroadcastInlineLayout(modifier=modifier,flushBottom=embedded,title={Text(if(review) t("Revisar difusión","Review Broadcast") else t("Difusión a sesiones conectadas","Broadcast to connected sessions"))},text={
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(t("Sesiones conectadas del dominio actual","Connected sessions in the current domain"),style=MaterialTheme.typography.titleSmall)
            Text(scope?.optString("server").orEmpty(),style=MaterialTheme.typography.bodySmall)
            val domain=scope?.let {userDomain(it.getString("actor"))}
            Text(t("Dominio: ","Domain: ")+(domain?.ifBlank {t("Predeterminado","Default")} ?: t("No disponible","Unavailable")),style=MaterialTheme.typography.bodySmall)
            val realms=scope?.let {userRealmValues(it.opt("realms"))}.orEmpty()
            if(realms.isEmpty()) Text(t("Sin filtro por reinos administrativos.","No administrative-realm filter."),style=MaterialTheme.typography.bodySmall) else ProfileDisclosure(t("Reinos administrativos","Administrative realms"),"${realms.size} "+t("reinos; no es un recuento de destinatarios","realms; not a recipient count")) {for(realm in realms) Text(realm,style=MaterialTheme.typography.bodySmall)}
            Text(t("Solo el nodo que procese la solicitud. No incluye usuarios desconectados ni difusión a otros nodos. El servidor decide los destinatarios al procesarla.","Only the node processing the request. Offline users and other nodes are not included. The server determines recipients when processing it."),style=MaterialTheme.typography.bodySmall)
            if(review) {
                ManagementCard { SelectionContainer { Text(message,style=MaterialTheme.typography.bodyMedium) } }
                if(!submitted) Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(approved,{approved=it},enabled=scopeAllowed);Text(t("Confirmo difundir este mensaje con el alcance indicado.","I confirm broadcasting this message with the stated scope."),Modifier.weight(1f)) }
            } else OutlinedTextField(message,{if(it.length<=4096) {message=it;approved=false;feedback=""}},label={Text(t("Mensaje","Message"))},supportingText={Text("${message.length}/512")},isError=message.isNotEmpty() && !profileBroadcastMessageValid(message),enabled=scopeAllowed,modifier=Modifier.fillMaxWidth(),minLines=6,maxLines=12)
            if(!scopeAllowed && !submitted) Text(t("La conexión, los permisos o el alcance han cambiado o no permiten enviar. Espera o cierra y vuelve a abrir para revisar.","The connection, rights or scope changed or do not allow sending. Wait or close and reopen to review."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            Text(t("La aceptación del servidor no acredita recepción ni lectura. Ante un resultado sin confirmar, no se reenvía automáticamente.","Server acceptance does not prove receipt or reading. Unconfirmed requests are not automatically retried."),style=MaterialTheme.typography.bodySmall)
            if(feedback.isNotBlank()) Text(feedback,color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=if(review) canSend else !submitted && scopeAllowed && profileBroadcastMessageValid(message),onClick={if(!review) {review=true;approved=false} else if(packet!=null && session.submit(packet)) {if(embedded) {submitted=true;approved=false} else onDismiss()} else feedback=t("No se ha enviado. Comprueba la conexión, el alcance y los permisos.","Not sent. Check the connection, scope and rights.")}) {Text(if(review) t("Enviar difusión","Send Broadcast") else t("Revisar","Review"))}},dismissButton={Row {if(review && !submitted) TextButton(onClick={review=false;approved=false}) {Text(t("Volver","Back"))};if(embedded) TextButton(enabled=!s.busy,onClick={message="";review=false;approved=false;feedback="";submitted=false}) {Text(if(submitted) t("Nueva difusión","New Broadcast") else t("Limpiar","Clear"))} else TextButton(onClick=onDismiss){Text(t("Cancelar","Cancel"))}}})
}

@Composable private fun ProfileBroadcastInlineLayout(modifier:Modifier,flushBottom:Boolean=false,title:@Composable ()->Unit,text:@Composable ()->Unit,confirmButton:@Composable ()->Unit,dismissButton:@Composable ()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val window=remember(context) {
        var owner:android.content.Context=context
        while(owner is android.content.ContextWrapper && owner !is android.app.Activity) owner=owner.baseContext
        requireNotNull((owner as? android.app.Activity)?.window)
    }
    DisposableEffect(window) {
        val flag=android.view.WindowManager.LayoutParams.FLAG_SECURE
        val previouslySecure=window.attributes.flags and flag != 0
        window.addFlags(flag)
        onDispose {if(!previouslySecure) window.clearFlags(flag)}
    }
    Column(modifier.imePadding().padding(if(flushBottom) managementBottomFlushPadding else managementConnectionContentPadding),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {title()}
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {text()}
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            dismissButton()
            confirmButton()
        }
    }
}
