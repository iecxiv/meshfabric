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

@Composable internal fun UserGroupCreateDialog(session:ProfileSession,s:ProfileState,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val actor=remember {s.id}
    val server=session.broadcastServerLabel()
    val beforeServer=remember {server}
    var name by remember {mutableStateOf("")}
    var desc by remember {mutableStateOf("")}
    var review by remember {mutableStateOf(false)}
    var approved by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val context=profileUserGroupCreateContext(s,actor,server,beforeServer)
    val packet=profileUserGroupCreatePacket(actor,beforeServer,name,desc,approved)
    val canReview=profileUserGroupCreateAllowed(s,JSONObject(packet.toString()).put("_groupConfirmed",true),server)
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Crear grupo de usuarios","Create user group"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Cuenta: ","Account: ")+actor,style=MaterialTheme.typography.bodySmall)
            Text(t("Grupo vacío en el dominio actual. Sin clonación, miembros ni asignaciones de permisos.","Empty group in the current domain. No cloning, members or rights assignments."),style=MaterialTheme.typography.bodySmall)
            if(review) {
                Text(t("Nombre: ","Name: ")+name);Text(t("Descripción: ","Description: ")+desc)
                ProfileAlignedCheckboxRow {Checkbox(approved,{approved=it},enabled=context);Text(t("Confirmo crear este grupo de usuarios.","I confirm creating this user group."),Modifier.weight(1f))}
            } else {
                OutlinedTextField(name,{if(it.length<=256) {name=it;approved=false;error=false}},label={Text(t("Nombre","Name"))},supportingText={Text("${name.length}/64")},singleLine=true,enabled=context,isError=name.isNotEmpty() && !profileUserGroupNameValid(name),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(desc,{if(it.length<=4096) {desc=it;approved=false;error=false}},label={Text(t("Descripción","Description"))},supportingText={Text("${desc.length}/1024")},minLines=2,maxLines=5,enabled=context,isError=!profileUserGroupDescValid(desc),modifier=Modifier.fillMaxWidth())
            }
            Text(t("El servidor puede rechazar la creación por sincronización externa u otras políticas. Puede haber nombres repetidos; el servidor asigna el identificador.","The server may reject creation due to external synchronization or other policies. Names can repeat; the server assigns the identifier."),style=MaterialTheme.typography.bodySmall)
            Text(t("Si el resultado queda sin confirmar, consulta el listado antes de volver a crear: podrían generarse duplicados. No hay reintentos automáticos.","If the result is unconfirmed, check the list before creating again: duplicates could result. There are no automatic retries."),style=MaterialTheme.typography.bodySmall)
            if(!context || error) Text(t("No se puede enviar. Comprueba la conexión, los permisos y el listado actual; cierra y actualiza si han cambiado.","Cannot send. Check the connection, rights and current list; close and refresh if they changed."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=if(review) profileUserGroupCreateAllowed(s,packet,server) else canReview,onClick={if(!review) {review=true;approved=false} else if(session.submit(packet)) onDismiss() else error=true}) {Text(if(review) t("Crear grupo","Create group") else t("Revisar","Review"))}},dismissButton={Row {if(review) TextButton(onClick={review=false;approved=false}) {Text(t("Volver","Back"))};TextButton(onClick=onDismiss) {Text(t("Cancelar","Cancel"))}}})
}
