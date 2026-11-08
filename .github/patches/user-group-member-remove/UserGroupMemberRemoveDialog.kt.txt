package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONObject

@Composable internal fun UserGroupMemberRemoveDialog(session:ProfileSession,s:ProfileState,group:JSONObject,id:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val actor=remember {s.id}
    val server=session.broadcastServerLabel()
    val beforeServer=remember {server}
    var review by remember {mutableStateOf(false)}
    var approved by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val packet=profileUserGroupMemberRemovePacket(actor,beforeServer,group,id,approved)
    val context=profileUserGroupMemberRemoveContext(s,packet,server)
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Retirar usuario del grupo","Remove user from group"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(group.optString("name"),style=MaterialTheme.typography.titleSmall)
            Text(t("Grupo: ","Group: ")+group.getString("_id"),style=MaterialTheme.typography.bodySmall)
            Text(t("Usuario: ","User: ")+id,style=MaterialTheme.typography.bodySmall)
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Solo se retira esta membresía. No se borra la cuenta ni el grupo y no se modifican permisos directos. Puede cambiar el acceso o las políticas heredadas; el usuario puede conservar acceso por otras vías.","Only this membership is removed. The account and group are not deleted and direct rights are not edited. Inherited access or policies may change; the user may retain access through other paths."),style=MaterialTheme.typography.bodySmall)
            Text(t("La consulta previa y la comparación no son atómicas. El servidor decide el resultado al procesar la solicitud. Se comprueba la respuesta y después la ausencia del enlace en una consulta del grupo; no se calculan todos los permisos efectivos.","The preflight query and comparison are not atomic. The server determines the result when processing the request. The response is checked, followed by a group read without this link; all effective rights are not calculated."),style=MaterialTheme.typography.bodySmall)
            if(review) Row {Checkbox(approved,{approved=it},enabled=context);Text(t("Confirmo retirar a este usuario de este grupo, sin borrar su cuenta.","I confirm removing this user from this group without deleting the account."),Modifier.weight(1f))}
            if(!context || error) Text(t("Espera la consulta previa. Si los datos, permisos o conexión han cambiado, cierra y actualiza antes de revisar otra vez.","Wait for the preflight query. If data, rights or the connection changed, close and refresh before reviewing again."),color=MaterialTheme.colorScheme.error)
            Text(t("Si el resultado queda sin confirmar, consulta el estado antes de repetir. No hay reintentos automáticos.","If the result is unconfirmed, check the state before repeating. There are no automatic retries."),style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={Button(enabled=if(review) profileUserGroupMemberRemoveAllowed(s,packet,server) else context,onClick={if(!review) {review=true;approved=false} else if(session.submit(packet)) onDismiss() else error=true}) {Text(if(review) t("Retirar del grupo","Remove from group") else t("Revisar","Review"))}},dismissButton={Row {if(review) TextButton(onClick={review=false;approved=false}) {Text(t("Volver","Back"))};TextButton(onClick=onDismiss) {Text(t("Cancelar","Cancel"))}}})
}
