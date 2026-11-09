package iecxiv.meshfabric.ui.management

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import org.json.JSONObject

@Composable internal fun UserGroupEditDialog(session:ProfileSession,s:ProfileState,original:JSONObject,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val id=original.getString("_id")
    val actor=remember(id) {s.id}
    val before=remember(id) {profileUserGroupBefore(original)}
    var name by remember(id) {mutableStateOf(original.getString("name"))}
    var desc by remember(id) {mutableStateOf(profileUserGroupDesc(original))}
    var review by remember(id) {mutableStateOf(false)}
    var approved by remember(id) {mutableStateOf(false)}
    var error by remember(id) {mutableStateOf(false)}
    val identity=profileReviewIdentity(original,s.listedUserGroups.orEmpty(),es)
    val identityReady=identity.usable
    var identityInvalidated by remember {mutableStateOf(false)}
    LaunchedEffect(review,identityReady) {if(review && !identityReady) {identityInvalidated=true;approved=false}}
    val context=identityReady && !identityInvalidated && profileUserGroupEditContext(s,actor,id,before)
    val packet=profileUserGroupEditPacket(actor,original,name,desc,approved)
    val canReview=context && profileUserGroupEditAllowed(s,JSONObject(packet.toString()).put("_groupConfirmed",true))
    val canSend=context && profileUserGroupEditAllowed(s,packet)
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Editar grupo de usuarios","Edit user group"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(identity.label,style=MaterialTheme.typography.titleSmall)
            if(!identityReady || identityInvalidated) Text(t("No se puede confirmar: falta un nombre o los datos visibles no distinguen inequívocamente el destino. Cierra y actualiza la ficha.","Cannot confirm: a name is missing or visible information does not uniquely identify the target. Close and refresh the details."),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            if(!original.isNull("membershipType")) Text(t("Grupo gestionado externamente: el nombre no se puede cambiar.","Externally managed group: its name cannot be changed."),style=MaterialTheme.typography.bodySmall)
            if(review) {
                if(packet.has("name")) {Text(t("Nombre anterior: ","Previous name: ")+original.getString("name"));Text(t("Nombre nuevo: ","New name: ")+name)}
                if(packet.has("desc")) {Text(t("Descripción anterior: ","Previous description: ")+profileUserGroupDesc(original));Text(t("Descripción nueva: ","New description: ")+desc)}
                Row {Checkbox(approved,{approved=it},enabled=context,modifier=Modifier.testTag("ug-edit-consent"));Text(t("Confirmo estos cambios para este grupo de usuarios.","I confirm these changes for this user group."),Modifier.weight(1f))}
            } else {
                OutlinedTextField(name,{if(it.length<=256) {name=it;approved=false;error=false}},label={Text(t("Nombre","Name"))},supportingText={Text("${name.length}/64")},singleLine=true,enabled=context && original.isNull("membershipType"),isError=original.isNull("membershipType") && !profileUserGroupNameValid(name),modifier=Modifier.fillMaxWidth().testTag("ug-edit-name"))
                OutlinedTextField(desc,{if(it.length<=4096) {desc=it;approved=false;error=false}},label={Text(t("Descripción","Description"))},supportingText={Text("${desc.length}/1024")},minLines=2,maxLines=5,enabled=context,isError=!profileUserGroupDescValid(desc),modifier=Modifier.fillMaxWidth())
            }
            Text(t("Solo se envían los campos modificados. No se cambian miembros, permisos, consentimientos ni opciones.","Only changed fields are sent. Members, rights, consent and options remain untouched."),style=MaterialTheme.typography.bodySmall)
            Text(t("La comprobación previa no bloquea cambios simultáneos de otro administrador. El resultado se verifica consultando el estado del servidor; no hay reenvíos automáticos.","The preflight check does not lock out concurrent administrator changes. The result is verified by reading server state; writes are not automatically retried."),style=MaterialTheme.typography.bodySmall)
            if(!context || error) Text(t("No se puede enviar: revisa la conexión, los permisos y los datos actuales del grupo. Si han cambiado, cierra y actualiza.","Cannot send: check the connection, rights and current group data. If they changed, close and refresh."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(modifier=Modifier.testTag(if(review) "ug-edit-send" else "ug-edit-prepare"),enabled=if(review) canSend else canReview,onClick={if(!review) {review=true;approved=false} else if(session.submit(packet)) onDismiss() else error=true}) {Text(if(review) t("Aplicar cambios","Apply changes") else t("Revisar","Review"))}},dismissButton={Row {if(review) TextButton(onClick={review=false;approved=false}) {Text(t("Volver","Back"))};TextButton(onClick=onDismiss) {Text(t("Cancelar","Cancel"))}}})
}
