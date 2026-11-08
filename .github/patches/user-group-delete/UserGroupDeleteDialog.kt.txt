package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable internal fun UserGroupDeleteDialog(session:ProfileSession,s:ProfileState,original:JSONObject,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val actor=remember {s.id}
    val server=session.broadcastServerLabel()
    val beforeServer=remember {server}
    var typed by remember {mutableStateOf("")}
    var review by remember {mutableStateOf(false)}
    var approved by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val packet=profileUserGroupDeletePacket(actor,beforeServer,original,typed,approved)
    val context=profileUserGroupDeleteContext(s,packet,server)
    val canReview=profileUserGroupDeleteAllowed(s,JSONObject(packet.toString()).put("_deleteConfirmed",true),server)
    val ids=remember {original.getJSONObject("links").keys().asSequence().sorted().toList()}
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Eliminar grupo de usuarios","Delete user group"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(original.getString("name"),style=MaterialTheme.typography.titleSmall)
            Text(original.getString("_id"),style=MaterialTheme.typography.bodySmall)
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Se elimina el grupo y se retiran sus membresías y asignaciones. Puede cambiar el acceso y las políticas heredadas. No se borran las cuentas de usuarios ni los grupos de dispositivos.","The group and its memberships and assignments are removed. Inherited access and policies may change. User accounts and device groups are not deleted."),style=MaterialTheme.typography.bodySmall)
            Text(t("Enlaces recibidos: ","Received links: ")+"${ids.count {it.startsWith("user/")}} "+t("usuarios; ","users; ")+"${ids.count {it.startsWith("mesh/")}} "+t("grupos de dispositivos.","device groups."),style=MaterialTheme.typography.bodySmall)
            if(ids.isNotEmpty()) ProfileDisclosure(t("Ver todos los enlaces recibidos","View all received links")) {Box(Modifier.fillMaxWidth().height(160.dp)) {LazyColumn {items(ids,key={it}) {Text(it,style=MaterialTheme.typography.bodySmall)}}}}
            if(!original.isNull("membershipType")) Text(t("Grupo externo sin usuarios recibidos: su proveedor podría volver a crearlo.","External group with no received users: its provider could recreate it."),style=MaterialTheme.typography.bodySmall)
            if(review) {
                Text(t("Nombre confirmado: ","Confirmed name: ")+typed)
                Row {Checkbox(approved,{approved=it},enabled=context);Text(t("Entiendo que se eliminará este grupo y se retirarán sus enlaces.","I understand this group and its links will be removed."),Modifier.weight(1f))}
            } else OutlinedTextField(typed,{if(it.length<=4096) {typed=it;approved=false;error=false}},label={Text(t("Escribe el nombre exacto","Type the exact name"))},enabled=context,singleLine=true,modifier=Modifier.fillMaxWidth())
            Text(t("Se vuelve a consultar el listado al abrir este diálogo. La comprobación no es atómica: se retiran los enlaces que existan al procesar el borrado, aunque hayan cambiado después de la consulta. Si el resultado queda sin confirmar, no se reintenta el borrado automáticamente.","The list is queried again when opening this dialog. The check is not atomic: links present when deletion is processed are removed, even if they changed after the query. Unconfirmed deletions are not automatically retried."),style=MaterialTheme.typography.bodySmall)
            if(!context || error) Text(t("Espera la consulta previa. Si el grupo, los permisos o la conexión han cambiado, cierra y actualiza antes de revisar otra vez.","Wait for the preflight query. If the group, rights or connection changed, close and refresh before reviewing again."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=if(review) profileUserGroupDeleteAllowed(s,packet,server) else canReview,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError),onClick={if(!review) {review=true;approved=false} else if(session.submit(packet)) onDismiss() else error=true}) {Text(if(review) t("Eliminar grupo","Delete group") else t("Revisar","Review"))}},dismissButton={Row {if(review) TextButton(onClick={review=false;approved=false}) {Text(t("Volver","Back"))};TextButton(onClick=onDismiss) {Text(t("Cancelar","Cancel"))}}})
}
