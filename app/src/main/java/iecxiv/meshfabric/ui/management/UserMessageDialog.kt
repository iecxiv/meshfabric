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
internal fun userMessageValid(value:String)=value.isNotBlank() && value.length<=512 && !value.contains('\u0000')
internal fun userMessagePacket(before:JSONObject,message:String):JSONObject {
    require(userMessageValid(message))
    return JSONObject().put("action","notifyuser").put("userid",before.getString("_id")).put("msg",message)
}
internal fun userMessageContext(s:ProfileState,before:JSONObject?,actor:String,site:Long,beforeServer:String,server:String,es:Boolean,actorBefore:JSONObject?,serverBefore:String):Boolean {
    if(before==null || actorBefore==null || s.user==null || !s.canWrite || s.detailsBusy || s.server==null || s.server.toString()!=serverBefore || serverBefore.isBlank()) return false
    if(s.id!=actor || ProfilePermissions.normalized(s.site)!=ProfilePermissions.normalized(site) || beforeServer.isBlank() || server!=beforeServer) return false
    fun validId(id:String)=id.startsWith("user/") && id.split('/').size==3 && id.split('/')[2].isNotBlank() && id.length<=256 && id.none {it.isISOControl()}
    val id=before.optString("_id")
    if(!validId(actor) || !validId(id) || id==actor || userDomain(id)!=userDomain(actor)) return false
    val current=s.users.singleOrNull {it.optString("_id")==id} ?: return false
    if(!canAdministerUser(s,current) || !profileReviewIdentity(before,s.users,es).usable) return false
    if(!listOf("_id","name","siteadmin","groups").all {actorBefore.opt(it)?.toString()==s.user.opt(it)?.toString()}) return false
    val realms=userRealmValues(s.user.opt("groups"))
    if(realms.isNotEmpty() && userRealmValues(current.opt("groups")).none {it in realms}) return false
    return listOf("_id","name","realname","email","siteadmin","links","groups").all {before.opt(it)?.toString()==current.opt(it)?.toString()}
}
@Composable internal fun UserMessageDialog(session:ProfileSession,s:ProfileState,id:String,onDismiss:()->Unit) {
    val es=LocalConfiguration.current.locales[0].language=="es"
    fun t(a:String,b:String)=if(es) a else b
    val baseline=remember(id) {s.users.singleOrNull {it.optString("_id")==id}?.let {JSONObject(it.toString())}}
    val actor=remember(id) {s.id};val site=remember(id) {s.site}
    val beforeServer=remember(id) {session.broadcastServerLabel()}
    val actorSnapshot=remember(id) {s.user?.let {JSONObject(it.toString())}}
    val serverSnapshot=remember(id) {s.server?.toString().orEmpty()}
    var message by remember(id) {mutableStateOf("")}
    var frozen by remember(id) {mutableStateOf<String?>(null)}
    var approved by remember(id) {mutableStateOf(false)}
    var invalidated by remember(id) {mutableStateOf(false)}
    var submitted by remember(id) {mutableStateOf(false)}
    var error by remember(id) {mutableStateOf(false)}
    val context=userMessageContext(s,baseline,actor,site,beforeServer,session.broadcastServerLabel(),es,actorSnapshot,serverSnapshot)
    LaunchedEffect(frozen,context) {if(frozen!=null && !context && !submitted) {invalidated=true;approved=false}}
    val active=context && !invalidated && !submitted
    val identity=profileReviewIdentity(baseline,s.users,es)
    ManagementDialog(onDismissRequest=onDismiss,modifier=Modifier.widthIn(max=640.dp).fillMaxWidth(0.94f),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn,usePlatformDefaultWidth=false,dismissOnClickOutside=false),title={Text(if(frozen==null) t("Enviar mensaje","Send message") else t("Revisar mensaje","Review message"))},text={
        Column(Modifier.fillMaxWidth().heightIn(max=(LocalConfiguration.current.screenHeightDp*0.68f).dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(t("Destinatario: ","Recipient: ")+identity.label,style=MaterialTheme.typography.titleSmall)
            Text(beforeServer,style=MaterialTheme.typography.bodySmall)
            Text(t("Notificación individual, no Broadcast ni chat. Se dirige a las sesiones del destinatario en el nodo que procese la solicitud. No hay propagación garantizada a otros nodos. Si está desconectado, el servidor puede intentar web push cuando esté disponible.","Individual notification, not Broadcast or chat. Targets recipient sessions on the node processing the request. Propagation to other nodes is not guaranteed. If offline, the server may attempt web push when available."),style=MaterialTheme.typography.bodySmall)
            if(frozen==null && !submitted) OutlinedTextField(message,{if(it.length<=4096) {message=it;approved=false}},label={Text(t("Mensaje","Message"))},supportingText={Text("${message.length}/512")},isError=message.isNotEmpty() && !userMessageValid(message),enabled=active,modifier=Modifier.fillMaxWidth().testTag("user-message-input"),minLines=3,maxLines=6)
            else {
                Text(frozen.orEmpty(),Modifier.testTag("user-message-preview"))
                if(!submitted) Row {Checkbox(approved,{approved=it},enabled=active,modifier=Modifier.testTag("user-message-consent"));Text(t("Confirmo enviar este mensaje al destinatario revisado.","I confirm sending this message to the reviewed recipient."),Modifier.weight(1f))}
            }
            if(invalidated || !context) Text(t("Revisión no disponible o invalidada por cambios de identidad, datos, ámbito, autoridad o conexión. No se reactiva automáticamente.","Review unavailable or invalidated by identity, data, scope, authority or connection changes. It does not reactivate automatically."),color=MaterialTheme.colorScheme.error)
            Text(t("No hay confirmación de entrega ni lectura. Cerrar no cancela un envío iniciado. No se reenvía automáticamente.","There is no delivery or read confirmation. Closing does not cancel an initiated submission. No automatic resend."),style=MaterialTheme.typography.bodySmall)
            if(submitted) Text(t("Resultado no confirmado. Comprueba con el destinatario antes de repetir.","Outcome unconfirmed. Check with the recipient before repeating."),Modifier.testTag("user-message-unconfirmed"))
            if(error) Text(t("No se pudo iniciar el envío de forma segura. Comprueba el estado antes de repetir.","Submission could not be safely initiated. Check state before repeating."),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(enabled=active && userMessageValid(frozen ?: message) && (frozen==null || approved),modifier=Modifier.testTag(if(frozen==null) "user-message-prepare" else "user-message-send"),onClick={
        if(frozen==null) {frozen=message;approved=false}
        else if(active && approved) {
            val reviewed=requireNotNull(frozen)
            submitted=true;approved=false
            if(!session.sendUserMessage(requireNotNull(baseline),actor,site,beforeServer,actorSnapshot,serverSnapshot,reviewed,true)) {error=true;invalidated=true}
        }
    }) {Text(if(frozen==null) t("Revisar","Review") else t("Enviar mensaje","Send message"))}},dismissButton={Row {
        if(frozen!=null && !submitted) TextButton(onClick={frozen=null;approved=false;invalidated=false;error=false}) {Text(t("Volver a redactar","Edit message"))}
        TextButton(onClick=onDismiss) {Text(t("Cerrar","Close"))}
    }})
}
