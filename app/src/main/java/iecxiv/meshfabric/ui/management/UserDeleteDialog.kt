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
internal fun userDeletePacket(before:JSONObject)=JSONObject().put("action","deleteuser").put("userid",before.getString("_id")).put("username",before.getString("name"))
internal fun userDeleteContext(s:ProfileState,before:JSONObject?,actor:String,site:Long,beforeServer:String,server:String,es:Boolean):Boolean {
    if(before==null || !s.canWrite || actor.isBlank() || s.id!=actor || ProfilePermissions.normalized(s.site)!=ProfilePermissions.normalized(site) || beforeServer.isBlank() || server!=beforeServer) return false
    val id=before.optString("_id")
    val current=s.users.singleOrNull {it.optString("_id")==id} ?: return false
    if(id==s.id || !canAdministerUser(s,current) || !profileReviewIdentity(before,s.users,es).usable) return false
    return listOf("name","realname","email","siteadmin","links","groups").all {before.opt(it)?.toString()==current.opt(it)?.toString()}
}
@Composable internal fun UserDeleteDialog(session:ProfileSession,s:ProfileState,id:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val baseline=remember(id) {s.users.singleOrNull {it.optString("_id")==id}?.let {JSONObject(it.toString())}}
    val actor=remember(id) {s.id};val site=remember(id) {s.site};val beforeServer=remember(id) {session.broadcastServerLabel()}
    val identity=profileReviewIdentity(baseline,s.users,es)
    var typed by remember(id) {mutableStateOf("")}
    var review by remember(id) {mutableStateOf(false)}
    var approved by remember(id) {mutableStateOf(false)}
    var invalidated by remember(id) {mutableStateOf(false)}
    var submitted by remember(id) {mutableStateOf(false)}
    var error by remember(id) {mutableStateOf(false)}
    val context=userDeleteContext(s,baseline,actor,site,beforeServer,session.broadcastServerLabel(),es)
    LaunchedEffect(review,context) {if(review && !context && !submitted) {invalidated=true;approved=false}}
    val active=context && !invalidated && !submitted
    val exact=typed.isNotBlank() && typed==baseline?.optString("name")
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(t("Eliminar usuario","Delete user"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(identity.label,style=MaterialTheme.typography.titleSmall)
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Eliminar esta cuenta es irreversible desde la app.","Deleting this account cannot be undone from the app."),color=MaterialTheme.colorScheme.error)
            if(!review) OutlinedTextField(typed,{if(it.length<=4096) {typed=it;approved=false}},label={Text(t("Escribe el nombre exacto","Type the exact name"))},enabled=active,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("user-delete-name"))
            else {
                Text(t("Nombre confirmado: ","Confirmed name: ")+typed)
                Row {Checkbox(approved,{approved=it},enabled=active,modifier=Modifier.testTag("user-delete-consent"));Text(t("Entiendo que se eliminará esta cuenta.","I understand this account will be deleted."),Modifier.weight(1f))}
            }
            Text(t("La revisión usa los datos recibidos y no es atómica con el borrado del servidor. Si cambian el destino, los datos revisados, los permisos o la conexión, vuelve atrás y revisa de nuevo; si persiste el cambio, cierra y actualiza. No hay reintentos automáticos. Un envío aceptado por la app no demuestra que el servidor haya borrado la cuenta.","Review uses received data and is not atomic with server deletion. If the target, reviewed data, rights or connection change, go back and review again; if the change persists, close and refresh. There are no automatic retries. App acceptance does not prove the server deleted the account."),style=MaterialTheme.typography.bodySmall)
            if(!context || invalidated) Text(t("Destino no identificable, datos modificados o autoridad no disponible. Esta revisión no se reactiva automáticamente.","Target not identifiable, data changed or authority unavailable. This review does not reactivate automatically."),color=MaterialTheme.colorScheme.error)
            if(error) Text(t("No se pudo iniciar el borrado de forma segura. Cierra y comprueba el estado antes de repetir.","Deletion could not be safely initiated. Close and check state before repeating."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=active && exact && (!review || approved),modifier=Modifier.testTag(if(review) "user-delete-send" else "user-delete-prepare"),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError),onClick={
        if(!review) {review=true;approved=false}
        else if(active && exact && approved) {
            submitted=true;approved=false
            if(session.submit(userDeletePacket(requireNotNull(baseline)))) onDismiss() else {error=true;invalidated=true}
        }
    }) {Text(if(review) t("Eliminar usuario","Delete user") else t("Revisar","Review"))}},dismissButton={Row {
        if(review && !submitted) TextButton(onClick={review=false;approved=false;invalidated=false;error=false}) {Text(t("Volver","Back"))}
        TextButton(onClick=onDismiss) {Text(t("Cancelar","Cancel"))}
    }})
}
