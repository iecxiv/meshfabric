package iecxiv.meshfabric.ui.management

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.OkHttpHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal fun nativePasswordResult(id:Int):Boolean? = when(id) { 20->true; 17,18,19,21->false; else->null }
internal fun nativeTotpValid(secret:String,token:String)=secret.matches(Regex("[A-Z2-7]{16,128}")) && token.matches(Regex("[0-9]{6}"))
private fun parseNativeBackupResponse(p:JSONObject?):List<NativeBackupCode>? {
if(p==null || !p.has("passwords")) return null
if(p.isNull("passwords")) return emptyList()
val a=p.optJSONArray("passwords") ?: return null
return nativeBackupCodes((0 until a.length()).map { index->
val value=a.opt(index)
if(value is JSONObject) mapOf("p" to value.opt("p"),"u" to value.opt("u")) else value
})
}
private data class AccountState(val ready:Boolean=false,val busy:Boolean=false,val user:JSONObject?=null,val reply:JSONObject?=null,val tokenList:JSONArray?=null,val status:String="offline",val server:JSONObject?=null,val notice:AccountNotice?=null,val webState:JSONObject?=null)
private class AccountWire(private val source:MeshCentralClient,private val actor:String) {
val state=MutableStateFlow(AccountState())
private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
private var socket:WebSocket?=null
private var epoch=0L
private var timer:Job?=null
private var call:Call?=null
private var pending=""
private var rid=""
private var args=JSONObject()
private var secret=""
private var cookie=""
private var retired=false
private var tokenReadback=false
private var noticeSerial=0L
private val connectivity=mutableMapOf<String,Int>()
private var identity=Triple("","",false)
private val keys=mutableSetOf<Int>()
private val tokens=mutableSetOf<String>()
private fun same()=identity==Triple(source.serverUrl,source.authCookie,source.ignoreSsl) && source.connectionState.value && !source.isUserLogout
private fun announce(kind:String,e:JSONObject,channels:Int=0) {
val id=e.optString("nodeid").ifBlank { e.optJSONObject("node")?.optString("_id").orEmpty() }
val match=source.groupedDevices.value.firstNotNullOfOrNull { (group,nodes)->nodes.firstOrNull { it.id==id }?.let { group to it } }
val name=match?.second?.name.orEmpty().ifBlank { e.optString("nodename").ifBlank { id } }.take(200)
val group=match?.first?.name.orEmpty().ifBlank { e.optString("meshname") }.take(200)
val text=if(kind=="general") e.optString("value").ifBlank { e.optString("msg") }.take(1000) else if(kind=="amt") e.optString("value").ifBlank { e.optString("msg") }.take(1000) else ""
val entry=AccountNotice(++noticeSerial,kind,text,name,group,channels)
state.update { it.copy(notice=entry) }
}
private fun noticeEvent(e:JSONObject) {
val (id,next)=noticeConnectivity(e) ?: return
val (gained,lost)=accountConnectionChange(connectivity.put(id,next),next)
if(gained!=0) announce("connect",e,gained)
if(lost!=0) announce("disconnect",e,lost)
}
@Synchronized fun start() {
if(retired || socket!=null || actor.isBlank() || !source.connectionState.value || source.isUserLogout) return
identity=Triple(source.serverUrl,source.authCookie,source.ignoreSsl)
connectivity.clear(); source.groupedDevices.value.forEach { (_,nodes)->nodes.forEach { connectivity[noticeNodeKey(it.id)]=it.conn } }
val generation=++epoch
state.value=AccountState(status="connecting")
runCatching {
socket=OkHttpHelper.createClient(identity.third).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(identity.first,"/control.ashx?moreargs=1")).header("Cookie",identity.second).build(),object:WebSocketListener() {
override fun onOpen(webSocket:WebSocket,response:Response) { synchronized(this@AccountWire) {
if(generation!=epoch || !same()) { webSocket.cancel(); return }
webSocket.send(JSONObject().put("action","urlargs").put("args",JSONObject().put("auth",source.extractRawToken(identity.second))).toString())
webSocket.send(JSONObject().put("action","userinfo").toString())
} }
override fun onMessage(webSocket:WebSocket,text:String) { synchronized(this@AccountWire) {
if(generation!=epoch) return
if(!same() || text.length>1_000_000) { pause("offline"); return }
runCatching { receive(JSONObject(text)) }.onFailure { pause(if(pending.isBlank()) "malformed" else "unknown") }
} }
override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { webSocket.close(code,"") }
override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { failed(generation) }
override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { failed(generation) }
})
timer=scope.launch { delay(20_000); synchronized(this@AccountWire) { if(generation==epoch && !state.value.ready) pause("timeout") } }
}.onFailure { pause("offline") }
}
@Synchronized private fun failed(generation:Long) { if(generation==epoch) pause(if(pending.isBlank()) "offline" else "unknown") }
@Synchronized fun pause(reason:String="offline") {
val notice=if(retired) "deleteUnknown" else if(pending.isNotBlank() && reason=="offline") "unknown" else reason
epoch++; timer?.cancel(); call?.cancel(); call=null; socket?.cancel(); socket=null
pending=""; rid=""; args=JSONObject(); secret=""; cookie=""; tokenReadback=false; keys.clear(); tokens.clear(); connectivity.clear()
state.value=AccountState(status=notice)
}
@Synchronized fun dispose() { pause(); scope.cancel() }
@Synchronized fun clearResult() { if(state.value.busy) pause("unknown") else { secret=""; keys.clear(); tokens.clear(); state.update { it.copy(reply=null,tokenList=null) } } }
private fun send(action:String)=socket?.send(JSONObject().put("action",action).toString())==true
private fun finish(reply:JSONObject?,status:String) {
timer?.cancel(); pending=""; rid=""; args=JSONObject(); tokenReadback=false
state.update { it.copy(busy=false,reply=reply,status=status) }
if(status=="confirmed") { send("userinfo"); source.requestServerData() }
}
private fun validKeys(a:JSONArray):Boolean {
val ids=mutableSetOf<Int>()
for(i in 0 until a.length()) {
val value=a.optJSONObject(i)?.opt("i") as? Number ?: return false
val id=value.toLong()
if(id !in 0..Int.MAX_VALUE.toLong() || value.toDouble()!=id.toDouble() || !ids.add(id.toInt())) return false
}
return true
}
private fun objectArray(a:JSONArray?)=a!=null && (0 until a.length()).all { a.optJSONObject(it)!=null }
private fun tokenIds(a:JSONArray?):Set<String>? {
if(a==null) return null
val ids=(0 until a.length()).map { (a.optJSONObject(it)?.opt("tokenUser") as? String) ?: return null }
return nativeTokenIds(ids)
}
private fun receive(p:JSONObject) {
val action=p.optString("action")
if(action=="ping") { send("pong"); return }
if(action=="close" || action=="stopped") { pause(); return }
if(action=="authcookie") { cookie=p.optString("cookie"); return }
if(action=="serverinfo") { state.update { it.copy(server=accountServerInfo(p)) }; return }
if(action=="userWebState" || (action=="event" && p.optJSONObject("event")?.optString("action")=="userWebState")) {
val snapshot=webNoticeState(p) ?: return
state.update { it.copy(webState=snapshot) }
if(pending=="userWebState" && webNoticeMask(snapshot)==args.optLong("notifications",-1)) finish(null,"confirmed")
return
}
if(action in setOf("nodeconnect","nodedisconnect","changenode")) { noticeEvent(p); return }
if(action=="userinfo") {
val user=p.optJSONObject("userinfo") ?: return
if(user.optString("_id")!=actor) { pause("identity"); return }
val first=!state.value.ready
state.update { it.copy(ready=true,user=JSONObject(user.toString()),status=if(first) "ready" else it.status) }
if(first) { timer?.cancel(); send("authcookie") }
val lang=if(user.isNull("lang")) "" else user.optString("lang")
if(pending=="changelang" && accountDefaultLanguage(lang)==accountDefaultLanguage(args.optString("lang"))) finish(JSONObject(),"confirmed")
return
}
if(action=="event") {
val event=p.optJSONObject("event") ?: return
val account=event.optJSONObject("account")
if(account?.optString("_id")==actor) { state.update { it.copy(user=JSONObject(account.toString())) }; send("userinfo") }
noticeEvent(event)
return
}
if(action=="msg" && p.optString("type")=="notify") {
val result=nativePasswordResult(p.optInt("msgid",-1))
if(pending=="changepassword" && result!=null) { finish(null,if(result) "confirmed" else "password:${p.optInt("msgid")}"); return }
announce(webNoticeKind(p),p)
return
}
if(pending.isBlank()) return
val responseId=p.optString("responseid")
if(responseId.isNotBlank() && responseId!=rid) return
if(pending=="otp-hkey-remove" && action=="otp-hkey-get") {
val list=p.optJSONArray("keys") ?: return
if(!validKeys(list)) return
if((0 until list.length()).none { list.optJSONObject(it).optInt("i")==args.optInt("index") }) { keys.remove(args.optInt("index")); finish(p,"confirmed") }
return
}
if(action!=pending) return
if(action in setOf("otp-hkey-remove","changelang","changepassword")) {
if(p.has("err") || p.has("error") || p.opt("success")==false || (p.has("result") && !p.optString("result").equals("ok",true))) finish(null,"rejected")
return
}
if(p.has("err") || p.has("error") || (p.has("result") && !p.optString("result").equals("ok",true))) { finish(null,"rejected"); return }
if(action=="loginTokens" && args.has("remove") && !p.has("loginTokens")) {
if(p.optString("result").equals("ok",true) && !tokenReadback) {
tokenReadback=true
if(socket?.send(JSONObject().put("action","loginTokens").put("responseid",rid).toString())!=true) pause("unknown")
}
return
}
val valid=when(action) {
"otpauth-request"->p.optString("secret").matches(Regex("[A-Z2-7]{16,128}"))
"otpauth-setup","otpauth-clear"->p.opt("success")==true
"otpauth-getpasswords"->parseNativeBackupResponse(p)!=null
"otp-hkey-get"->p.optJSONArray("keys")?.let(::validKeys)==true
"previousLogins"->objectArray(p.optJSONArray("events"))
"loginTokens"->tokenIds(p.optJSONArray("loginTokens"))!=null
"createLoginToken"->nativeTokenIds(listOf(p.optString("tokenUser")))!=null && p.optString("tokenPass").isNotEmpty()
else->false
}
if(!valid) { finish(null,"rejected"); return }
if(action=="otpauth-request") secret=p.optString("secret")
if(action=="otpauth-setup" || action=="otpauth-clear") secret=""
if(action=="otp-hkey-get") { keys.clear(); val a=p.getJSONArray("keys"); for(i in 0 until a.length()) keys.add(a.getJSONObject(i).getInt("i")) }
var resultStatus=if(action in setOf("otpauth-setup","otpauth-clear") || (action=="otpauth-getpasswords" && args.has("subaction"))) "confirmed" else "received"
if(action=="loginTokens") {
val a=p.getJSONArray("loginTokens")
val ids=tokenIds(a) ?: return
val removed=args.optJSONArray("remove")?.optString(0)
tokens.clear(); tokens.addAll(ids)
state.update { it.copy(tokenList=JSONArray(a.toString())) }
if(removed!=null) resultStatus=if(removed !in ids) "confirmed" else "notRevoked"
}
if(action=="createLoginToken") {
val id=p.getString("tokenUser")
val list=state.value.tokenList?.let { JSONArray(it.toString()) } ?: JSONArray()
if(id !in tokens) list.put(JSONObject().put("tokenUser",id).put("name",p.optString("name").ifBlank { args.optString("name") }))
tokens.add(id)
state.update { it.copy(tokenList=list) }
}
finish(p,resultStatus)
}
@Synchronized fun saveNotificationOptions(options:AccountNoticeOptions,before:Long):Boolean {
val s=state.value
if(!s.ready || s.busy || !same()) return false
val site=ProfilePermissions.normalized(s.user?.optLong("siteadmin",0L) ?: 0L)
if(site!=0xffffffffL && site and 1024L!=0L) return false
val snapshot=s.webState ?: return false
val updated=mergeWebNoticeState(snapshot,before,options) ?: return false
pending="userWebState"; rid=UUID.randomUUID().toString()
args=JSONObject().put("notifications",webNoticeMask(updated))
state.update { it.copy(busy=true,status="sent",reply=null) }
val generation=epoch
timer?.cancel(); timer=scope.launch { delay(20_000); synchronized(this@AccountWire) { if(generation==epoch && pending.isNotBlank()) pause("unknown") } }
val payload=JSONObject().put("action","userWebState").put("state",updated.toString()).put("responseid",rid)
if(socket?.send(payload.toString())!=true) { pause("unknown"); return false }
return true
}
@Synchronized fun request(action:String,fields:JSONObject=JSONObject()):Boolean {
val s=state.value
if(!s.ready || s.busy || !same()) return false
val reads=setOf("otpauth-request","otpauth-getpasswords","otp-hkey-get","previousLogins","loginTokens")
val allowed=reads+setOf("otpauth-setup","otpauth-clear","otp-hkey-remove","createLoginToken","changepassword","changelang","deleteaccount")
if(action !in allowed) return false
val site=ProfilePermissions.normalized(s.user?.optLong("siteadmin",0L) ?: 0L)
val mutation=action !in reads || fields.has("subaction") || fields.has("remove")
if(mutation && site!=0xffffffffL && site and 1024L != 0L) return false
if(action=="otpauth-setup" && (fields.optString("secret")!=secret || !nativeTotpValid(secret,fields.optString("token")))) return false
if(action=="otp-hkey-remove" && fields.optInt("index",-1) !in keys) return false
if(action=="createLoginToken" && (fields.optString("name").isBlank() || fields.optString("name").length>128 || fields.optInt("expire") !in 1..43200)) return false
if(action=="changepassword" && (fields.optString("oldpass").isEmpty() || fields.optString("newpass").isEmpty() || fields.optString("newpass").length>256)) return false
if(action=="changelang" && fields.optString("lang")!="*" && fields.optString("lang") !in accountLanguageTags(s.server?.optJSONArray("languages"))) return false
if(action=="loginTokens" && fields.has("remove") && fields.optJSONArray("remove")?.let { a->a.length()==1 && a.optString(0) in tokens }!=true) return false
if(action=="deleteaccount" && (cookie.isBlank() || fields.optString("password").isEmpty() || fields.optString("confirmation")!=actor)) return false
pending=action; args=JSONObject(fields.toString()); rid=UUID.randomUUID().toString(); tokenReadback=false
state.update { it.copy(busy=true,reply=null,status="sent") }
val generation=epoch
timer?.cancel(); timer=scope.launch { delay(20_000); synchronized(this@AccountWire) { if(generation==epoch && pending.isNotBlank()) pause("unknown") } }
if(action=="deleteaccount") {
val url=identity.first.toHttpUrlOrNull()?.newBuilder()?.username("")?.password("")?.addPathSegment("deleteaccount")?.query(null)?.fragment(null)?.build()
if(url==null || url.scheme!="https") { finish(null,"https"); return false }
val body=FormBody.Builder().add("authcookie",cookie).add("apassword1",fields.getString("password")).add("apassword2",fields.getString("password")).build()
val client=OkHttpHelper.createClient(identity.third).newBuilder().followRedirects(false).followSslRedirects(false).build()
val request=client.newCall(Request.Builder().url(url).header("Cookie",identity.second).post(body).build())
call=request
retired=true
scope.launch { runCatching { request.execute().use { } }; synchronized(this@AccountWire) { if(generation==epoch) pause("deleteUnknown") } }
return true
}
val payload=JSONObject(fields.toString()).put("action",action).put("responseid",rid)
if(socket?.send(payload.toString())!=true) { pause("unknown"); return false }
if(action=="otp-hkey-remove" || action=="changelang") scope.launch { delay(600); synchronized(this@AccountWire) { if(generation==epoch && pending==action) send(if(action=="changelang") "userinfo" else "otp-hkey-get") } }
return true
}
}

@Composable internal fun NativeAccountPane(source:MeshCentralClient,actor:String,modifier:Modifier=Modifier) {
val es=LocalConfiguration.current.locales[0].language=="es"
fun t(s:String,e:String)=if(es) s else e
val context=LocalContext.current
val lifecycle=LocalLifecycleOwner.current.lifecycle
val connected by source.connectionState.collectAsState()
val wire=remember(source,actor) { AccountWire(source,actor) }
val state by wire.state.collectAsState()
val prefs=remember(context) { context.getSharedPreferences("native_account_view",Context.MODE_PRIVATE) }
val prefKey=remember(source.serverUrl,actor) { MessageDigest.getInstance("SHA-256").digest((source.serverUrl+"\u0000"+actor).toByteArray()).joinToString("") { "%02x".format(it) } }
var hours24 by remember(prefKey) { mutableStateOf(prefs.getBoolean("${prefKey}_24",true)) }
var dateLocale by remember(prefKey) { mutableStateOf(prefs.getString("${prefKey}_locale","").orEmpty()) }
var alerts by remember(prefKey) { mutableStateOf(prefs.getBoolean("${prefKey}_alerts",false)) }
val options=webNoticeOptions(state.webState?.let(::webNoticeMask) ?: 0L)
var mode by remember { mutableStateOf<Int?>(null) }
DisposableEffect(wire) { onDispose { wire.dispose() } }
LaunchedEffect(wire,lifecycle,connected) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
if(connected) { wire.start(); try { awaitCancellation() } finally { wire.pause() } } else wire.pause()
} }
LaunchedEffect(state.ready) { if(!state.ready) mode=null }
val labels=listOf(t("Administrar la aplicación de autenticación","Manage authenticator app"),t("Administrar claves de seguridad","Manage security keys"),t("Administrar códigos de respaldo","Manage backup codes"),t("Ver inicios de sesión anteriores","View previous logins"),t("Habilitar notificaciones en la app","Enable in-app notifications"),t("Opciones de hora e idioma","Time and language options"),t("Opciones de notificación","Notification options"),t("Cambiar contraseña","Change password"),t("Eliminar cuenta","Delete account"),t("Crear token de inicio de sesión","Create login token"))
val status=when(state.status) {
"ready","received"->""
"confirmed"->t("El servidor confirmó el cambio o se observó el estado solicitado.","The server acknowledged the change or the requested state was observed.")
"notRevoked"->t("El servidor sigue incluyendo el token en la lista. La revocación no se considera completada.","The server still includes the token in its list. Revocation is not considered completed.")
"sent"->t("Solicitud enviada; resultado pendiente.","Request sent; outcome pending.")
"unknown"->t("Resultado sin confirmar. Reconecta y comprueba antes de reintentar. No hay reenvíos automáticos.","Outcome unconfirmed. Reconnect and check before retrying. No automatic resends.")
"deleteUnknown"->t("Borrado solicitado, sin confirmación de eliminación. Una redirección no acredita éxito. Cierra sesión desde Ajustes antes de volver a acceder; no se reintentará.","Deletion requested, but deletion is unconfirmed. A redirect is not proof of success. Sign out from Settings before signing in again; no retry will occur.")
"rejected"->t("Solicitud rechazada o respuesta incompatible. No se considera completada.","Rejected request or unsupported response. Not considered completed.")
"password:21"->t("Contraseña actual incorrecta.","Current password is incorrect.")
"password:17"->t("La nueva contraseña no cumple los requisitos.","The new password does not meet requirements.")
"password:18"->t("Esa contraseña ya fue utilizada.","That password was used previously.")
"password:19"->t("El servidor aún no permite cambiar la contraseña.","The server does not yet allow a password change.")
"https"->t("Esta operación requiere HTTPS.","This operation requires HTTPS.")
else->t("Cuenta desconectada o conectando.","Account offline or connecting.")
}
fun openAction(index:Int) {
wire.clearResult(); mode=index
when(index) { 0->wire.request("otpauth-request"); 1->wire.request("otp-hkey-get"); 2->wire.request("otpauth-getpasswords"); 3->wire.request("previousLogins"); 9->wire.request("loginTokens") }
}
val icons=listOf(Icons.Default.Security,Icons.Default.VpnKey,Icons.Default.Description,Icons.Default.History,Icons.Default.Notifications,Icons.Default.Language,Icons.Default.Settings,Icons.Default.Lock,Icons.Default.Delete,Icons.Default.Key)
Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodySmall,color=if(state.status in setOf("unknown","deleteUnknown","rejected","https","notRevoked") || state.status.startsWith("password:")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
AccountNoticeBanner(state.notice,options,alerts,es,dateLocale,hours24)
if(!state.ready) TextButton(enabled=state.status!="deleteUnknown",onClick=wire::start) { Text(t("Reconectar cuenta","Reconnect account")) }
if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
Text(t("Mi cuenta","My account"),style=MaterialTheme.typography.titleMedium)
ManagementCard {
ManagementInfoRow(t("Nombre de usuario","Username"),state.user?.optString("name").orEmpty())
HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
ManagementInfoRow(t("Correo electrónico","Email"),state.user?.optString("email").orEmpty())
HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
ManagementInfoRow(t("ID de cuenta","Account ID"),actor)
}
Text(t("Seguridad de la cuenta","Account security"),style=MaterialTheme.typography.titleMedium)
ManagementCard {
(0..2).forEach { index->
ManagementActionRow(labels[index],icons[index],state.ready && !state.busy,onClick={ openAction(index) })
if(index<2) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
}
Text(t("Acciones","Actions"),style=MaterialTheme.typography.titleMedium)
ManagementCard {
(3..9).forEach { index->
ManagementActionRow(labels[index],icons[index],state.ready && !state.busy,dangerous=index==8,onClick={ openAction(index) })
if(index<9) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
}
}
mode?.let { selected->key(selected) {
var first by remember { mutableStateOf("") }
var second by remember { mutableStateOf("") }
var third by remember { mutableStateOf("") }
var confirmation by remember { mutableStateOf(false) }
var reveal by remember { mutableStateOf(false) }
var feedback by remember { mutableStateOf("") }
val reply=state.reply
fun run(action:String,fields:JSONObject=JSONObject()) {
if(!wire.request(action,fields)) feedback=t("No se ha enviado. Comprueba permisos, conexión y valores.","Not sent. Check permissions, connection and values.") else { feedback=""; confirmation=false; reveal=false; first=""; second=""; third="" }
}
@Composable fun field(value:String,onChange:(String)->Unit,label:String,password:Boolean=false) {
OutlinedTextField(value,{ if(it.length<=4096) onChange(it) },label={ Text(label) },singleLine=true,visualTransformation=if(password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,modifier=Modifier.fillMaxWidth())
}
@Composable fun action(s:String,e:String,enabled:Boolean=true,onClick:()->Unit) { TextButton(enabled=enabled && !state.busy,onClick=onClick) { Text(t(s,e)) } }
@Composable fun option(label:String,value:Boolean,change:(Boolean)->Unit) { Row { Checkbox(value,change); Text(label,Modifier.weight(1f)) } }
fun copy(value:String) {
val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
val clip=ClipData.newPlainText("MeshFabric",value).apply { description.extras=PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE",true) } }
feedback=if(runCatching { checkNotNull(clipboard).setPrimaryClip(clip) }.isSuccess) t("Copiado.","Copied.") else t("No se pudo copiar.","Could not copy.")
}
AlertDialog(onDismissRequest={ if(!state.busy) { wire.clearResult(); mode=null } },properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),title={ Text(labels[selected]) },text={
Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
if(status.isNotBlank()) Text(status)
if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
when(selected) {
0->{
val value=reply?.optString("secret").orEmpty()
Text(t("Introduce esta clave manualmente en tu autenticador y confirma con su código de seis dígitos. No se activa hasta que el servidor lo confirme.","Enter this key manually in your authenticator and confirm with its six-digit code. It is not enabled until the server confirms."))
Row { Checkbox(reveal,{ reveal=it }); Text(t("Mostrar clave","Show key")) }
if(reveal && value.isNotBlank()) { SelectionContainer { Text(value) }; action("Copiar clave","Copy key") { copy(value) } }
field(first,{ first=it },t("Código TOTP","TOTP code"))
action("Activar autenticador","Enable authenticator",nativeTotpValid(value,first)) { run("otpauth-setup",JSONObject().put("secret",value).put("token",first)) }
Row { Checkbox(confirmation,{ confirmation=it }); Text(t("Confirmo desactivar el autenticador de esta cuenta.","I confirm disabling this account's authenticator.")) }
action("Desactivar autenticador","Disable authenticator",confirmation) { run("otpauth-clear") }
}
1->{
Text(t("Consulta y eliminación nativas. El alta de nuevas claves FIDO2 no está implementada; no se abrirá un navegador.","Native listing and removal. New FIDO2 key registration is not implemented; no browser will open."))
val list=reply?.optJSONArray("keys")
if(list!=null && list.length()==0) Text(t("No hay claves registradas.","No registered keys."))
if(list!=null) for(i in 0 until list.length()) list.optJSONObject(i)?.let { item-> Text("${item.optString("name")} · ID ${item.optInt("i")}") }
field(first,{ first=it },t("ID de la clave que quieres eliminar","ID of key to remove"))
Row { Checkbox(confirmation,{ confirmation=it }); Text(t("Confirmo eliminar esa clave de seguridad.","I confirm removing that security key.")) }
action("Eliminar clave","Remove key",confirmation && first.toIntOrNull()!=null) { run("otp-hkey-remove",JSONObject().put("index",first.toInt())) }
}
2->{
Text(t("Cada código es de un solo uso. Generar otros invalida los anteriores.","Each code is single-use. Generating new codes invalidates the previous ones."))
val values=parseNativeBackupResponse(reply)
Row { Checkbox(checked=reveal,onCheckedChange={ reveal=it },enabled=!state.busy && values?.isNotEmpty()==true); Text(t("Mostrar códigos","Show codes")) }
when {
state.busy->Text(t("Consultando códigos…","Loading codes…"))
values==null->Text(t("No se recibió una lista válida de códigos. Puede que la función no esté disponible para esta cuenta o servidor.","No valid code list was received. The feature may be unavailable for this account or server."),style=MaterialTheme.typography.bodySmall)
values.isEmpty()->Text(t("El servidor devolvió una lista vacía: no hay códigos de respaldo.","The server returned an empty list: no backup codes."))
!reveal->Text(t("Códigos ocultos. Activa Mostrar códigos para verlos.","Codes hidden. Enable Show codes to view them."),style=MaterialTheme.typography.bodySmall)
else->{
for(code in values) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
Text(code.code,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,fontFamily=FontFamily.Monospace,textDecoration=if(code.available) TextDecoration.None else TextDecoration.LineThrough,color=if(code.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
Text(if(code.available) t("Disponible","Available") else t("Utilizado","Used"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
val copyable=nativeBackupCopyValues(values)
if(copyable.isEmpty()) Text(t("Todos los códigos recibidos están utilizados.","All received codes have been used."),style=MaterialTheme.typography.bodySmall)
action("Copiar códigos disponibles","Copy available codes",copyable.isNotEmpty()) { if(reveal) copy(copyable.joinToString("\n")) }
}
}
action("Volver a consultar","Reload codes",state.ready) { run("otpauth-getpasswords") }
Row { Checkbox(confirmation,{ confirmation=it }); Text(t("Confirmo invalidar los códigos anteriores.","I confirm invalidating the previous codes.")) }
action("Generar nuevos","Generate new",confirmation) { run("otpauth-getpasswords",JSONObject().put("subaction",1)) }
action("Eliminar códigos","Clear codes",confirmation) { run("otpauth-getpasswords",JSONObject().put("subaction",2)) }
}
3->{
Text(t("El servidor puede registrar aquí solo accesos web, no todos los accesos de la app.","The server may record only web sign-ins here, not all app sign-ins."))
val list=reply?.optJSONArray("events")
if(list!=null && list.length()==0) Text(t("No hay registros.","No records."))
val rows=if(list==null) emptyList() else (0 until list.length()).mapNotNull { list.optJSONObject(it) }
fun instant(item:JSONObject):Instant?=runCatching {
val value=item.opt("t")
val parsed:Instant=when(value) {
is Number->Instant.ofEpochMilli(if(value.toLong()<100000000000L) Math.multiplyExact(value.toLong(),1000L) else value.toLong())
is String->Instant.parse(value)
else->return@runCatching null
}
parsed.also { it.toEpochMilli() }
}.getOrNull()
for(item in rows.sortedByDescending { instant(it)?.toEpochMilli() ?: Long.MIN_VALUE }.take(200)) {
val date=instant(item)?.let { accountTime(it,dateLocale,hours24) }.orEmpty()
val details=item.optJSONArray("a")?.let { a->(0 until a.length()).joinToString(" · ") { a.optString(it) } }.orEmpty()
Text("$date · ${loginHistoryLabel(item.opt("m"),es)}\n$details"); HorizontalDivider()
}
}
4->{
Text(t("Activa los avisos dentro de Mi Usuario mientras esta vista esté abierta y en primer plano. No activa notificaciones del sistema, push, ni una bandeja global.","Enables notices within My user while this view is open and foregrounded. Does not enable system notifications, push, or a global inbox."))
option(t("Habilitar notificaciones en la app","Enable in-app notifications"),alerts,{ alerts=it; prefs.edit().putBoolean("${prefKey}_alerts",it).apply() })
}
5->{
var localeDraft by remember { mutableStateOf(dateLocale) }
var hoursDraft by remember { mutableStateOf(hours24) }
val locales=remember(es) { accountLocaleChoices(es) }
AccountChoiceDropdown(t("Configuración regional de fecha y hora","Date and time locale"),localeDraft,locales,es=es,onSelect={ localeDraft=it })
Text(t("Ejemplo: ","Example: ")+accountTime(Instant.now(),localeDraft,hoursDraft),style=MaterialTheme.typography.bodySmall)
option(t("Formato de 24 horas","24-hour format"),hoursDraft,{ hoursDraft=it })
action("Guardar formato local","Save local format") { dateLocale=localeDraft; hours24=hoursDraft; prefs.edit().putString("${prefKey}_locale",localeDraft).putBoolean("${prefKey}_24",hoursDraft).apply(); feedback=t("Formato local guardado.","Local format saved.") }
val current=accountDefaultLanguage(state.user?.let { if(it.isNull("lang")) "" else it.optString("lang") }.orEmpty())
var languageDraft by remember(current) { mutableStateOf(current) }
val tags=accountLanguageTags(state.server?.optJSONArray("languages"))
val languages=listOf("*" to t("Predeterminado del servidor/navegador","Server/browser default"))+(tags+listOf(current).filter { it!="*" }).distinct().map { it to accountLocaleLabel(it,es) }
AccountChoiceDropdown(t("Idioma de la cuenta","Account language"),languageDraft,languages,enabled=tags.isNotEmpty() && !state.busy,es=es,onSelect={ languageDraft=it })
if(tags.isEmpty()) Text(t("El servidor no anunció su lista de idiomas. No se enviarán idiomas arbitrarios.","The server did not advertise its language list. Arbitrary languages will not be sent."),style=MaterialTheme.typography.bodySmall)
action("Guardar idioma de cuenta","Save account language",tags.isNotEmpty() && languageDraft!=current && (languageDraft=="*" || languageDraft in tags)) { run("changelang",JSONObject().put("lang",languageDraft)) }
Text(t("El formato regional se guarda localmente para las fechas de Mi Usuario y no cambia la zona horaria. El idioma se solicita al servidor y se comprueba mediante userinfo; no cambia el idioma global de Android ni de toda la app.","The locale is stored locally for My user dates and does not change the time zone. Account language is requested from the server and checked through userinfo; it does not change Android or app-wide language."),style=MaterialTheme.typography.bodySmall)
}
6->{
val beforeMask=state.webState?.let(::webNoticeMask)
var draft by remember(beforeMask) { mutableStateOf(options) }
Text(t("Opciones compartidas con la web de esta cuenta. Los cambios se envían al servidor; la confirmación requiere observar userWebState. La recepción y el permiso Android son independientes.","Options shared with this account's web interface. Changes are sent to the server; confirmation requires observing userWebState. Reception and Android permission are independent."),style=MaterialTheme.typography.bodySmall)
if(beforeMask==null) Text(t("No se ha recibido el estado web. Guardar permanece deshabilitado para no sobrescribir otros ajustes.","Web state has not been received. Saving remains disabled to avoid overwriting other settings."),style=MaterialTheme.typography.bodySmall)
option(t("Sonido de notificación","Notification sound"),draft.sound,{ draft=draft.copy(sound=it) })
option(t("Mostrar el nombre de grupo de dispositivos","Display device group name"),draft.groupName,{ draft=draft.copy(groupName=it) })
option(t("Conexiones de dispositivo","Device connections"),draft.connections,{ draft=draft.copy(connections=it) })
option(t("Desconexiones de dispositivo","Device disconnections"),draft.disconnections,{ draft=draft.copy(disconnections=it) })
option(t("Eventos de escritorio y serial de Intel(R) AMT","Intel(R) AMT desktop and serial events"),draft.amt,{ draft=draft.copy(amt=it) })
action("Guardar opciones en el servidor","Save server options",beforeMask!=null) {
feedback=if(beforeMask!=null && wire.saveNotificationOptions(draft,beforeMask)) t("Solicitud enviada; confirmación pendiente.","Request sent; confirmation pending.") else t("No enviado. El estado o los permisos han cambiado; vuelve a consultar.","Not sent. State or permissions changed; reload before retrying.")
}
Text(t("AMT filtra mensajes marcados amtMessage. Conexiones y desconexiones se generan a partir de cambios de conectividad. Sonido y nombre del grupo modifican la presentación; no son tipos de mensaje. Android puede modificar el sonido del canal. No se garantiza entrega con el proceso detenido ni actualización inmediata de una pestaña web que no procese el evento.","AMT filters messages marked amtMessage. Connections and disconnections are generated from connectivity changes. Sound and group name affect presentation; they are not message types. Android may override channel sound. Delivery with a stopped process or immediate refresh in a web tab that ignores the event is not guaranteed."),style=MaterialTheme.typography.bodySmall)
}
7->{
field(first,{ first=it },t("Contraseña actual","Current password"),true)
field(second,{ second=it },t("Nueva contraseña","New password"),true)
field(third,{ third=it },t("Repetir nueva contraseña","Repeat new password"),true)
action("Cambiar contraseña","Change password",first.isNotEmpty() && second.isNotEmpty() && second==third && second.length<=256) { run("changepassword",JSONObject().put("oldpass",first).put("newpass",second)) }
}
8->{
Text(actor)
Text(t("Operación irreversible. La respuesta HTTP puede ser ambigua: no se mostrará una eliminación confirmada solo por recibir una redirección. Después de solicitarla, cierra sesión desde Ajustes.","Irreversible operation. The HTTP response can be ambiguous: a redirect will not be shown as confirmed deletion. After requesting deletion, sign out from Settings."))
field(first,{ first=it },t("Escribe el ID completo de tu cuenta","Type your complete account ID"))
field(second,{ second=it },t("Contraseña actual","Current password"),true)
field(third,{ third=it },t("Repetir contraseña","Repeat password"),true)
Row { Checkbox(confirmation,{ confirmation=it }); Text(t("Confirmo solicitar la eliminación de esta cuenta.","I confirm requesting deletion of this account.")) }
action("Solicitar eliminación","Request deletion",confirmation && first==actor && second.isNotEmpty() && second==third) { run("deleteaccount",JSONObject().put("confirmation",first).put("password",second)) }
}
9->{
Text(t("La contraseña del token se muestra solo en esta respuesta. No se guarda en disco.","The token password is shown only in this response. It is not saved to disk."))
field(first,{ first=it },t("Nombre del token","Token name"))
field(second,{ second=it },t("Caducidad en minutos (1–43200)","Expiry in minutes (1–43200)"))
action("Crear token","Create token",first.isNotBlank() && (second.toIntOrNull()?.let { it in 1..43200 }==true)) { run("createLoginToken",JSONObject().put("name",first.trim()).put("expire",second.toInt())) }
val tokenUser=reply?.optString("tokenUser").orEmpty()
val tokenPass=reply?.optString("tokenPass").orEmpty()
if(tokenPass.isNotEmpty()) { Row { Checkbox(reveal,{ reveal=it }); Text(t("Mostrar credenciales del token","Show token credentials")) }; if(reveal) { SelectionContainer { Text("$tokenUser\n$tokenPass") }; action("Copiar credenciales","Copy credentials") { copy("$tokenUser\n$tokenPass") } } }
Text(t("Selecciona el token exacto que quieres revocar. Actualiza para consultar de nuevo la lista del servidor.","Select the exact token to revoke. Refresh to query the server list again."),style=MaterialTheme.typography.bodySmall)
val list=state.tokenList
if(list==null && !state.busy) Text(t("Lista de tokens no disponible.","Token list unavailable."))
if(list!=null && list.length()==0) Text(t("El servidor no devuelve tokens.","The server returned no tokens."))
if(list!=null) for(i in 0 until list.length()) list.optJSONObject(i)?.let { token->
val id=token.optString("tokenUser")
TextButton(enabled=!state.busy,onClick={ third=id; confirmation=false }) {
RadioButton(selected=third==id,onClick=null)
Column(Modifier.weight(1f)) { Text(token.optString("name").ifBlank { t("Sin nombre","Unnamed") }); Text(id,style=MaterialTheme.typography.bodySmall) }
}
}
if(third.isNotBlank()) Text(t("Token seleccionado: ","Selected token: ")+third,style=MaterialTheme.typography.bodySmall)
Row { Checkbox(confirmation,{ confirmation=it }); Text(t("Confirmo revocar el token seleccionado.","I confirm revoking the selected token.")) }
action("Revocar token","Revoke token",confirmation && third.isNotBlank()) { run("loginTokens",JSONObject().put("remove",JSONArray().put(third))) }
action("Actualizar lista","Refresh list") { third=""; confirmation=false; run("loginTokens") }
}
}
if(feedback.isNotBlank()) Text(feedback)
}
},confirmButton={ TextButton(enabled=!state.busy,onClick={ wire.clearResult(); mode=null }) { Text(t("Cerrar","Close")) } })
} }
}
