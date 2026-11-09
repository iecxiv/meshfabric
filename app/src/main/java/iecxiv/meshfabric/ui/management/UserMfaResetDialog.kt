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
internal fun userMfaResetPacket(before:JSONObject)=JSONObject().put("action","changeuserpass").put("userid",before.getString("_id")).put("pass","").put("removeMultiFactor",true).put("resetNextLogin",before.opt("passchange")==-1L || before.opt("passchange")==-1)
internal fun userMfaResetContext(s:ProfileState,before:JSONObject?,actor:String,site:Long,beforeServer:String,server:String,es:Boolean,actorBefore:JSONObject?,serverBefore:String):Boolean {
    if(before==null || actorBefore==null || s.user==null || !s.canWrite || s.detailsBusy || s.server==null || s.server.toString()!=serverBefore || serverBefore.isBlank()) return false
    if(s.server.has("lock2factor") && s.server.opt("lock2factor")!=false) return false
    if(s.id!=actor || ProfilePermissions.normalized(s.site)!=ProfilePermissions.normalized(site) || beforeServer.isBlank() || server!=beforeServer) return false
    fun validId(id:String)=id.startsWith("user/") && id.split('/').size==3 && id.split('/')[2].isNotBlank() && id.length<=256 && id.none {it.isISOControl()}
    val id=before.optString("_id")
    if(!validId(actor) || !validId(id) || id==actor || userDomain(id)!=userDomain(actor)) return false
    val current=s.users.singleOrNull {it.optString("_id")==id} ?: return false
    if(!canAdministerUser(s,current) || !profileReviewIdentity(before,s.users,es).usable) return false
    if(!listOf("_id","name","siteadmin","groups").all {actorBefore.opt(it)?.toString()==s.user.opt(it)?.toString()}) return false
    val realms=userRealmValues(s.user.opt("groups"))
    if(realms.isNotEmpty() && userRealmValues(current.opt("groups")).none {it in realms}) return false
    if(before.has("passchange")) {
        val value=before.opt("passchange") as? Number ?: return false
        if(!value.toDouble().isFinite() || value.toDouble()%1.0!=0.0) return false
    }
    return listOf("_id","name","realname","email","siteadmin","links","groups","phone","passchange","passtype","otpkeys","otpsecret","otphkeys","otpekey","otpdev","otpduo","msghandle").all {before.opt(it)?.toString()==current.opt(it)?.toString()}
}
@Composable internal fun UserMfaResetDialog(session:ProfileSession,s:ProfileState,id:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val baseline=remember(id) {s.users.singleOrNull {it.optString("_id")==id}?.let {JSONObject(it.toString())}}
    val actor=remember(id) {s.id};val site=remember(id) {s.site};val beforeServer=remember(id) {session.broadcastServerLabel()}
    val actorSnapshot=remember(id) {s.user?.let {JSONObject(it.toString())}}
    val serverSnapshot=remember(id) {s.server?.toString().orEmpty()}
    val identity=profileReviewIdentity(baseline,s.users,es)
    var typed by remember(id) {mutableStateOf("")}
    var review by remember(id) {mutableStateOf(false)}
    var approved by remember(id) {mutableStateOf(false)}
    var effectsApproved by remember(id) {mutableStateOf(false)}
    var invalidated by remember(id) {mutableStateOf(false)}
    var submitted by remember(id) {mutableStateOf(false)}
    var error by remember(id) {mutableStateOf(false)}
    val context=userMfaResetContext(s,baseline,actor,site,beforeServer,session.broadcastServerLabel(),es,actorSnapshot,serverSnapshot)
    LaunchedEffect(review,context) {if(review && !context && !submitted) {invalidated=true;approved=false;effectsApproved=false}}
    val active=context && !invalidated && !submitted
    val exact=typed.isNotBlank() && typed==baseline?.optString("name")
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Restablecer MFA","Reset MFA"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(identity.label,style=MaterialTheme.typography.titleSmall)
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Se retirarán códigos de respaldo, autenticador, llaves FIDO, MFA por correo, push y Duo. También se borrará el teléfono guardado para SMS. No se garantiza retirar métodos adicionales que el servidor no elimina.","Backup codes, authenticator, FIDO keys, email MFA, push and Duo will be removed. The stored SMS phone number will also be deleted. Additional methods not removed by the server are not guaranteed to be reset."),color=MaterialTheme.colorScheme.error)
            if(!review) OutlinedTextField(typed,{if(it.length<=4096) {typed=it;approved=false}},label={Text(t("Escribe el nombre exacto","Type the exact name"))},enabled=active,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("mfa-reset-name"))
            else {
                Text(t("Nombre confirmado: ","Confirmed name: ")+typed)
                Row {Checkbox(approved,{approved=it},enabled=active,modifier=Modifier.testTag("mfa-reset-consent"));Text(t("Confirmo restablecer MFA para la cuenta revisada.","I confirm resetting MFA for the reviewed account."),Modifier.weight(1f))}
            }
            Text(t("La contraseña no se sustituye, pero el servidor actualiza passchange y elimina passtype. Se conserva la obligación de cambio en el próximo inicio si ya estaba marcada; en otro caso se actualiza la fecha. No se restauran metadatos ni teléfono. La revisión no es atómica y no hay reintentos automáticos.","The password is not replaced, but the server updates passchange and deletes passtype. An existing next-login password-change requirement is retained; otherwise the timestamp is updated. Metadata and phone are not restored. Review is not atomic and there are no automatic retries."),style=MaterialTheme.typography.bodySmall)
            if(review && !submitted) Row {Checkbox(effectsApproved,{effectsApproved=it},enabled=active,modifier=Modifier.testTag("mfa-reset-effects"));Text(t("Acepto el borrado del teléfono y los cambios en los metadatos de contraseña.","I accept phone deletion and password metadata changes."),Modifier.weight(1f))}
            if(submitted) Text(t("Resultado no confirmado. Un envío aceptado o un evento de credenciales modificadas no demuestra la retirada de MFA. Comprueba la cuenta antes de repetir.","Outcome unconfirmed. Accepted submission or a credentials-change event does not prove MFA removal. Check the account before repeating."),Modifier.testTag("mfa-reset-unconfirmed"))
            if(!context || invalidated) Text(t("Destino no identificable, datos modificados o autoridad no disponible. Esta revisión no se reactiva automáticamente.","Target not identifiable, data changed or authority unavailable. This review does not reactivate automatically."),color=MaterialTheme.colorScheme.error)
            if(error) Text(t("No se pudo iniciar el restablecimiento de forma segura. Cierra y comprueba el estado antes de repetir.","Reset could not be safely initiated. Close and check state before repeating."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=active && exact && (!review || (approved && effectsApproved)),modifier=Modifier.testTag(if(review) "mfa-reset-send" else "mfa-reset-prepare"),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError),onClick={
        if(!review) {review=true;approved=false;effectsApproved=false}
        else if(active && exact && approved && effectsApproved) {
            submitted=true;approved=false
            if(!session.resetUserMfa(requireNotNull(baseline),actor,site,beforeServer,actorSnapshot,serverSnapshot,true)) {error=true;invalidated=true}
        }
    }) {Text(if(review) t("Restablecer MFA","Reset MFA") else t("Revisar","Review"))}},dismissButton={Row {
        if(review && !submitted) TextButton(onClick={review=false;approved=false;effectsApproved=false;invalidated=false;error=false}) {Text(t("Volver","Back"))}
        TextButton(onClick=onDismiss) {Text(t("Cancelar","Cancel"))}
    }})
}
