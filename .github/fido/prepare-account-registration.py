from pathlib import Path
import re
import subprocess

TARGET = 'app/src/main/java/iecxiv/meshfabric/ui/management/NativeAccount.kt'
TEST = 'app/src/test/java/iecxiv/meshfabric/ui/management/NativeAccountFidoStatusTest.kt'
path = Path(TARGET)
assert subprocess.check_output(['git', 'hash-object', TARGET], text=True).strip() == 'f9c510cf5fa199966defb6c04dbb66cf53e0d052', 'NativeAccount changed; review required'
text = path.read_text(encoding='utf-8')

def once(old, new):
    global text
    assert text.count(old) == 1, ('anchor count', old, text.count(old))
    text = text.replace(old, new)

once('import iecxiv.meshfabric.data.remote.OkHttpHelper\n', 'import iecxiv.meshfabric.data.remote.OkHttpHelper\nimport iecxiv.meshfabric.data.remote.FidoRegistrationBinding\nimport iecxiv.meshfabric.data.remote.FidoRegistrationSession\nimport iecxiv.meshfabric.MainActivity\nimport iecxiv.meshfabric.ui.login.FidoWebViewHost\nimport androidx.compose.ui.viewinterop.AndroidView\n')
once('val webStateError:String="")', 'val webStateError:String="",val fidoOptions:JSONObject?=null,val fidoActive:Boolean=false,val fidoNote:String="")')
once('private val tokens=mutableSetOf<String>()', 'private val tokens=mutableSetOf<String>()\nprivate var ceremony:FidoRegistrationSession?=null\nprivate var ceremonyTimer:Job?=null')
once('else if(pending.isNotBlank() && reason=="offline") "unknown" else reason', 'else if((pending.isNotBlank() || ceremonyUncertain()) && reason=="offline") "unknown" else reason')
once('webStateReader.cancel(); epoch++;', 'ceremony?.cancel(); ceremony=null; ceremonyTimer?.cancel(); webStateReader.cancel(); epoch++;')
once('it.copy(reply=null,tokenList=null)', 'it.copy(reply=null,tokenList=null,fidoNote="")')
once('if(pending.isBlank()) return\nval responseId=p.optString("responseid")', 'if(ceremony!=null && receiveFido(p)) return\nif(pending.isBlank()) return\nval responseId=p.optString("responseid")')
once('"https"->t("Esta operación requiere HTTPS.","This operation requires HTTPS.")', '"fidoExpired"->t("El registro de la llave caducó. No se considera completado.","Key registration expired. Not considered completed.")\n"fidoCancelled"->t("Registro de la llave cancelado. No se considera completado.","Key registration cancelled. Not considered completed.")\n"https"->t("Esta operación requiere HTTPS.","This operation requires HTTPS.")')
once('finally { wire.pause() }', 'finally { wire.leave() }')

METHODS = '''private fun fidoBinding()=FidoRegistrationBinding(identity.first,actor,epoch)
private fun ceremonyUncertain():Boolean { val p=ceremony?.phase; return p==FidoRegistrationSession.Phase.ACK || p==FidoRegistrationSession.Phase.READBACK }
@Synchronized fun leave() {
val s=ceremony
val active=setOf(FidoRegistrationSession.Phase.OPTIONS,FidoRegistrationSession.Phase.AUTHENTICATOR,FidoRegistrationSession.Phase.ACK,FidoRegistrationSession.Phase.READBACK)
if(s!=null && same() && s.phase in active) return
pause()
}
@Synchronized fun beginFido(name:String):Boolean {
val s=state.value
if(!s.ready || s.busy || !same() || ceremony!=null) return false
val site=ProfilePermissions.normalized(s.user?.optLong("siteadmin",0L) ?: 0L)
if(site!=0xffffffffL && site and 1024L!=0L) return false
val list=s.reply?.optJSONArray("keys") ?: return false
val existing=securityKeyIds(list)?.toSet() ?: return false
val trimmed=name.trim()
if(trimmed.isEmpty() || trimmed.length>128) return false
val binding=fidoBinding()
val session=try { FidoRegistrationSession(binding,trimmed,existing,identity.third) } catch(e:Exception) { state.update { it.copy(status="https") }; return false }
val packet=session.start(binding) ?: return false
ceremony=session
state.update { it.copy(busy=true,fidoActive=true,fidoOptions=null,fidoNote="",status="sent") }
val generation=epoch
ceremonyTimer?.cancel()
ceremonyTimer=scope.launch { delay(65_000); synchronized(this@AccountWire) { if(generation==epoch && ceremony===session) { session.cancel(); ceremony=null; if(session.phase==FidoRegistrationSession.Phase.UNKNOWN) pause("unknown") else state.update { it.copy(busy=false,fidoActive=false,fidoOptions=null,status="fidoExpired") } } } }
if(socket?.send(packet.toString())!=true) { session.cancel(); ceremony=null; pause("offline"); return false }
return true
}
@Synchronized fun submitFido(credential:JSONObject) {
val s=ceremony ?: return
if(!same()) { s.cancel(); finishFido(s); return }
val packet=runCatching { s.submit(fidoBinding(),com.google.gson.JsonParser.parseString(credential.toString()).asJsonObject) }.getOrNull()
if(packet==null) { finishFido(s); return }
state.update { it.copy(fidoOptions=null) }
if(socket?.send(packet.toString())!=true) { s.cancel(); finishFido(s) }
}
@Synchronized fun cancelFido() { val s=ceremony ?: return; s.cancel(); finishFido(s) }
private fun finishFido(s:FidoRegistrationSession) {
ceremonyTimer?.cancel()
if(ceremony===s) ceremony=null
when(s.phase) {
FidoRegistrationSession.Phase.COMPLETE -> {
state.update { it.copy(busy=false,fidoActive=false,fidoOptions=null,status="confirmed",fidoNote="confirmed") }
send("userinfo"); source.requestServerData(); request("otp-hkey-get")
}
FidoRegistrationSession.Phase.UNKNOWN -> pause("unknown")
else -> state.update { it.copy(busy=false,fidoActive=false,fidoOptions=null,status=fidoPhaseStatus(s.phase)) }
}
}
private fun receiveFido(p:JSONObject):Boolean {
val s=ceremony ?: return false
val action=p.optString("action")
if(action !in setOf("webauthn-startregister","otp-hkey-setup-response","otp-hkey-get")) return false
val b=fidoBinding()
val reply=runCatching { com.google.gson.JsonParser.parseString(p.toString()).asJsonObject }.getOrNull()
if(reply==null) { s.cancel(); finishFido(s); return true }
when(action) {
"webauthn-startregister" -> {
if(s.phase!=FidoRegistrationSession.Phase.OPTIONS) return false
val options=s.acceptOptions(b,reply)
if(options==null) finishFido(s) else state.update { it.copy(fidoOptions=JSONObject(options.toString())) }
}
"otp-hkey-setup-response" -> {
if(s.phase!=FidoRegistrationSession.Phase.ACK) return false
val next=s.acknowledge(b,reply)
if(next==null) finishFido(s) else if(socket?.send(next.toString())!=true) { s.cancel(); finishFido(s) }
}
else -> {
if(s.phase!=FidoRegistrationSession.Phase.READBACK) return false
if(!s.readback(b,reply)) s.cancel()
finishFido(s)
}
}
return true
}
'''
once('@Synchronized fun request(action:String,fields:JSONObject=JSONObject()):Boolean {', METHODS + '@Synchronized fun request(action:String,fields:JSONObject=JSONObject()):Boolean {')

pattern = re.compile(r'^Text\(t\("Consulta y eliminaci\u00f3n nativas[^\n]*$', re.M)
UI = '''Text(t("Consulta, alta y eliminaci\u00f3n de claves de seguridad. El alta usa una ceremonia WebAuthn con el servidor seleccionado, sin abrir un navegador externo. No se confirma hasta que el servidor responda y la llave aparezca en la lista.","Native listing, registration and removal of security keys. Registration uses a WebAuthn ceremony with the selected server, without an external browser. It is not confirmed until the server responds and the key appears in the list."))
val fidoKeys=reply?.optJSONArray("keys")
val legacyKeys=fidoKeys!=null && (0 until fidoKeys.length()).any { (fidoKeys.optJSONObject(it)?.optInt("type",3) ?: 3)!=3 }
var fidoName by remember { mutableStateOf("") }
var fidoLegacyOk by remember { mutableStateOf(false) }
if(state.fidoNote=="confirmed") Text(t("Llave registrada: el servidor la confirm\u00f3 y aparece en la lista.","Key registered: the server confirmed it and it appears in the list."))
field(fidoName,{ fidoName=it },t("Nombre de la llave","Key name"))
if(legacyKeys) option(t("Entiendo que registrar una llave WebAuthn eliminar\u00e1 las llaves U2F antiguas de esta cuenta.","I understand that registering a WebAuthn key removes older U2F keys from this account."),fidoLegacyOk) { fidoLegacyOk=it }
action("Registrar llave","Register key",!state.fidoActive && fidoKeys!=null && fidoName.trim().isNotEmpty() && fidoName.length<=128 && (!legacyKeys || fidoLegacyOk)) { if(wire.beginFido(fidoName)) { fidoName=""; fidoLegacyOk=false; feedback="" } else feedback=t("No se ha iniciado el registro. Carga la lista, comprueba permisos y conexi\u00f3n.","Registration was not started. Load the list and check permissions and connection.") }
if(state.fidoActive) TextButton(onClick={ wire.cancelFido() }) { Text(t("Cancelar registro","Cancel registration")) }
state.fidoOptions?.let { fidoOptions-> key(fidoOptions.toString()) { FidoRegistrationHost(source.serverUrl,fidoOptions,onCredential={ wire.submitFido(it) },onFailure={ wire.cancelFido() }) } }'''
assert len(pattern.findall(text)) == 1, 'Security-key dialog anchor not found exactly once'
text = pattern.sub(lambda m: UI, text, count=1)

TAIL = '''

internal fun fidoPhaseStatus(phase:FidoRegistrationSession.Phase):String = when(phase) {
FidoRegistrationSession.Phase.COMPLETE->"confirmed"
FidoRegistrationSession.Phase.REJECTED->"rejected"
FidoRegistrationSession.Phase.EXPIRED->"fidoExpired"
FidoRegistrationSession.Phase.INVALID->"incompatible"
FidoRegistrationSession.Phase.UNKNOWN->"unknown"
else->"fidoCancelled"
}

private fun findMainActivity(context:Context):MainActivity? = when(context) {
is MainActivity->context
is android.content.ContextWrapper->findMainActivity(context.baseContext)
else->null
}

@Composable private fun FidoRegistrationHost(server:String,options:JSONObject,onCredential:(JSONObject)->Unit,onFailure:()->Unit) {
val context=LocalContext.current
val activity=remember(context) { findMainActivity(context) }
val credentialCallback=rememberUpdatedState(onCredential)
val failureCallback=rememberUpdatedState(onFailure)
if(activity==null) {
LaunchedEffect(Unit) { failureCallback.value() }
} else {
val host=remember(activity) { FidoWebViewHost(activity,activity.fidoClient) }
val gsonOptions=remember(options) { com.google.gson.JsonParser.parseString(options.toString()).asJsonObject }
DisposableEffect(host) { onDispose { host.close() } }
AndroidView(factory={ host.create(server,false,gsonOptions,{ credentialCallback.value(JSONObject(it.toString())) },{ failureCallback.value() }) },modifier=Modifier.fillMaxWidth().height(4.dp))
}
}
'''
text = text.rstrip('\n') + TAIL
path.write_text(text, encoding='utf-8')

TEST_LINES = [
'package iecxiv.meshfabric.ui.management',
'',
'import iecxiv.meshfabric.data.remote.FidoRegistrationSession.Phase',
'import org.junit.Assert.assertEquals',
'import org.junit.Test',
'',
'class NativeAccountFidoStatusTest {',
'    @Test fun completeIsConfirmed() { assertEquals("confirmed", fidoPhaseStatus(Phase.COMPLETE)) }',
'    @Test fun rejectedIsRejected() { assertEquals("rejected", fidoPhaseStatus(Phase.REJECTED)) }',
'    @Test fun expiredHasOwnStatus() { assertEquals("fidoExpired", fidoPhaseStatus(Phase.EXPIRED)) }',
'    @Test fun invalidIsIncompatible() { assertEquals("incompatible", fidoPhaseStatus(Phase.INVALID)) }',
'    @Test fun uncertainOutcomeIsUnknown() { assertEquals("unknown", fidoPhaseStatus(Phase.UNKNOWN)) }',
'    @Test fun cancelledAndIncompletePhasesAreNeverConfirmed() {',
'        listOf(Phase.NEW, Phase.OPTIONS, Phase.AUTHENTICATOR, Phase.ACK, Phase.READBACK, Phase.CANCELLED).forEach { assertEquals("fidoCancelled", fidoPhaseStatus(it)) }',
'    }',
'}',
'']
test = Path(TEST)
assert not test.exists()
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('\n'.join(TEST_LINES), encoding='utf-8')

subprocess.run(['git', 'add', '--', TARGET, TEST], check=True)
subprocess.run(['git', 'diff', '--cached', '--check'], check=True)
actual = set(subprocess.check_output(['git', 'diff', '--cached', '--name-only'], text=True).splitlines())
assert actual == {TARGET, TEST}, actual
out = Path('validation')
out.mkdir(exist_ok=True)
(out / 'account-candidate.patch').write_text(subprocess.check_output(['git', 'diff', '--cached', '--binary'], text=True), encoding='utf-8')
