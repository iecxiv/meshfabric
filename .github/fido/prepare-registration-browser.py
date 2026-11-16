from pathlib import Path
import hashlib
import subprocess

host_path = 'app/src/main/java/iecxiv/meshfabric/ui/login/FidoWebViewHost.kt'
host = Path(host_path)
raw = host.read_bytes()
assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == '5057b266162f2036aacac0c3e79a6e826260f888', 'Host changed; review required'
files = {}
files['app/src/main/java/iecxiv/meshfabric/ui/login/FidoRegistrationPage.kt'] = r'''package iecxiv.meshfabric.ui.login

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import iecxiv.meshfabric.data.remote.FidoWebOriginPolicy

internal class FidoRegistrationPage(
    private val policy: FidoWebOriginPolicy,
    options: JsonObject,
    private val ceremonyId: String
) {
    sealed class Delivery {
        data class Credential(val value: JsonObject): Delivery()
        object Failed: Delivery()
    }
    private val optionsJson = options.toString()
    private var started = false
    private var terminal = false
    val hasStarted: Boolean get() = started
    init { require(ceremonyId.matches(Regex("[A-Za-z0-9_-]{16,128}"))) }
    fun invalidate() { terminal = true }
    fun receive(sourceOrigin: String, mainFrame: Boolean, currentUrl: String, message: String): Delivery? {
        if (!started || terminal || message.length > 1_000_000) return null
        if (!policy.allowsMessage(sourceOrigin, mainFrame) || !policy.allowsNavigation(currentUrl)) return null
        val payload = runCatching { JsonParser.parseString(message).asJsonObject }.getOrNull() ?: return null
        val id = payload.get("ceremonyId") ?: return null
        if (!id.isJsonPrimitive || !id.asJsonPrimitive.isString || id.asString != ceremonyId) return null
        val error = payload.get("error")
        val credential = payload.get("credential")
        val delivery = when {
            error != null && credential == null && error.isJsonPrimitive && error.asJsonPrimitive.isBoolean && error.asBoolean -> Delivery.Failed
            error == null && credential != null && credential.isJsonObject -> Delivery.Credential(credential.asJsonObject.deepCopy())
            else -> return null
        }
        terminal = true
        return delivery
    }
    fun begin(): String? {
        if (started || terminal) return null
        started = true
        val optionsLiteral = JsonPrimitive(optionsJson).toString()
        val idLiteral = JsonPrimitive(ceremonyId).toString()
        return """
            (() => {
              'use strict';
              const bridge = window.meshFabricFidoRegistration;
              if (!bridge || typeof bridge.postMessage !== 'function') return;
              const ceremonyId = $idLiteral;
              const decode = value => {
                if (typeof value !== 'string' || !value.length) throw new Error('Invalid binary field');
                const raw = value.replace(/-/g, '+').replace(/_/g, '/');
                return Uint8Array.from(atob(raw + '='.repeat((4 - raw.length % 4) % 4)), c => c.charCodeAt(0));
              };
              const encode = value => {
                if (!(value instanceof ArrayBuffer)) throw new Error('Invalid credential buffer');
                const bytes = new Uint8Array(value);
                let raw = '';
                for (let i = 0; i < bytes.length; i++) raw += String.fromCharCode(bytes[i]);
                return btoa(raw).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
              };
              const deliver = payload => bridge.postMessage(JSON.stringify({ceremonyId, ...payload}));
              (async () => {
                try {
                  const publicKey = JSON.parse($optionsLiteral);
                  publicKey.challenge = decode(publicKey.challenge);
                  publicKey.user.id = decode(publicKey.user.id);
                  if (publicKey.excludeCredentials !== undefined) {
                    if (!Array.isArray(publicKey.excludeCredentials)) throw new Error('Invalid exclusions');
                    publicKey.excludeCredentials = publicKey.excludeCredentials.map(item => ({...item, id: decode(item.id)}));
                  }
                  const c = await navigator.credentials.create({publicKey});
                  if (!c || c.type !== 'public-key') throw new Error('No credential');
                  deliver({credential: {
                    id: c.id, rawId: encode(c.rawId), type: c.type,
                    response: {
                      clientDataJSON: encode(c.response.clientDataJSON),
                      attestationObject: encode(c.response.attestationObject)
                    }
                  }});
                } catch (_) { deliver({error: true}); }
              })();
            })();
        """.trimIndent()
    }
}
'''
files['app/src/main/java/iecxiv/meshfabric/ui/login/FidoRegistrationWebBridge.kt'] = '''package iecxiv.meshfabric.ui.login

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.FidoWebOriginPolicy
import java.util.UUID

internal class FidoRegistrationWebBridge(
    private val view: WebView,
    private val policy: FidoWebOriginPolicy,
    options: JsonObject,
    private val onCredential: (JsonObject) -> Unit,
    private val onFailure: () -> Unit
) {
    private val page = FidoRegistrationPage(policy, options, UUID.randomUUID().toString())
    private var closed = false
    val hasStarted: Boolean get() = page.hasStarted
    init {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            error("Secure WebView messaging is unavailable")
        }
        WebViewCompat.addWebMessageListener(view, "meshFabricFidoRegistration", setOf(policy.origin),
            WebViewCompat.WebMessageListener { _, message, origin, mainFrame, _ ->
                if (!closed) {
                    val data = runCatching { message.data }.getOrNull()
                    if (data != null) {
                        when (val result = page.receive(origin.toString(), mainFrame, view.url.orEmpty(), data)) {
                            is FidoRegistrationPage.Delivery.Credential -> onCredential(result.value)
                            FidoRegistrationPage.Delivery.Failed -> onFailure()
                            null -> Unit
                        }
                    }
                }
            })
    }
    fun onPageFinished(url: String) {
        if (closed) return
        if (!policy.allowsNavigation(url) || !policy.allowsNavigation(view.url.orEmpty())) {
            close(); onFailure(); return
        }
        page.begin()?.let { view.evaluateJavascript(it, null) }
    }
    fun close() {
        if (closed) return
        closed = true
        page.invalidate()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(view, "meshFabricFidoRegistration")
        }
    }
}
'''
files['app/src/test/java/iecxiv/meshfabric/ui/login/FidoRegistrationPageTest.kt'] = '''package iecxiv.meshfabric.ui.login

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.FidoWebOriginPolicy
import org.junit.Assert.*
import org.junit.Test

class FidoRegistrationPageTest {
    private val origin = "https://server.example"
    private val id = "ceremony_0123456789"
    private fun page() = FidoRegistrationPage(FidoWebOriginPolicy("$origin/tenant"), JsonObject(), id)
    private fun packet(ceremony: String = id) = """{"ceremonyId":"$ceremony","credential":{"rawId":"AQID"}}"""
    private fun deliver(p: FidoRegistrationPage, message: String = packet()) = p.receive(origin, true, "$origin/tenant", message)
    @Test fun cannotDeliverBeforeBegin() { assertNull(deliver(page())) }
    @Test fun validMainFrameCanDeliver() {
        val p=page(); p.begin()
        val result=deliver(p) as FidoRegistrationPage.Delivery.Credential
        assertEquals("AQID",result.value.get("rawId").asString)
    }
    @Test fun otherOriginCannotDeliver() { val p=page(); p.begin(); assertNull(p.receive("https://other.example",true,origin,packet())) }
    @Test fun otherPortCannotDeliver() { val p=page(); p.begin(); assertNull(p.receive("$origin:444",true,origin,packet())) }
    @Test fun subframeCannotDeliver() { val p=page(); p.begin(); assertNull(p.receive(origin,false,origin,packet())) }
    @Test fun changedDocumentOriginCannotDeliver() { val p=page(); p.begin(); assertNull(p.receive(origin,true,"https://other.example",packet())) }
    @Test fun wrongCeremonyCannotDeliver() { val p=page(); p.begin(); assertNull(deliver(p,packet("another_ceremony_123"))); assertNotNull(deliver(p)) }
    @Test fun duplicateCannotDeliver() { val p=page(); p.begin(); assertNotNull(deliver(p)); assertNull(deliver(p)) }
    @Test fun closedPageCannotDeliver() { val p=page(); p.begin(); p.invalidate(); assertNull(deliver(p)) }
    @Test fun failureIsTerminal() { val p=page(); p.begin(); assertSame(FidoRegistrationPage.Delivery.Failed,deliver(p,"""{"ceremonyId":"$id","error":true}""")); assertNull(deliver(p)) }
    @Test fun malformedPacketCannotConsumeCeremony() { val p=page(); p.begin(); assertNull(deliver(p,"{")); assertNotNull(deliver(p)) }
    @Test fun oversizedPacketCannotDeliver() { val p=page(); p.begin(); assertNull(deliver(p,"x".repeat(1_000_001))) }
    @Test fun ceremonyIsStartedOnlyOnce() { val p=page(); assertNotNull(p.begin()); assertNull(p.begin()) }
    @Test fun errorAndCredentialTogetherAreRejected() { val p=page(); p.begin(); assertNull(deliver(p,"""{"ceremonyId":"$id","error":true,"credential":{}}""")) }
}
'''
edits = [
('    private var browser:WebView?=null', '    private var browser:WebView?=null\n    private var registrationBridge:FidoRegistrationWebBridge?=null'),
('    fun create(server:String,ignoreSsl:Boolean=false,onFailure:(Failure)->Unit):WebView {', '    fun create(server:String,ignoreSsl:Boolean=false,registrationOptions:com.google.gson.JsonObject?=null,onCredential:((com.google.gson.JsonObject)->Unit)?=null,onFailure:(Failure)->Unit):WebView {'),
('            failed=true\n            view.stopLoading()', '            failed=true\n            registrationBridge?.close()\n            view.stopLoading()'),
('        val guardedClient=object:FidoClient {', '        if(registrationOptions!=null) {\n            val deliver=requireNotNull(onCredential)\n            registrationBridge=FidoRegistrationWebBridge(view,policy,registrationOptions,\n                onCredential={ if(!closed && !failed) deliver(it) },\n                onFailure={ block(Failure.HTTP) })\n        }\n        val guardedClient=object:FidoClient {'),
('                return client.makeCredential(origin,request,null,callerLabel)', '                val result=client.makeCredential(origin,request,null,callerLabel)\n                return if(allowed(origin)) result else Result.failure(SecurityException("FIDO ceremony no longer authorized"))'),
('                return client.getAssertion(origin,request,null,callerLabel)', '                if(registrationBridge!=null) return Result.failure(SecurityException("Assertion not allowed during registration"))\n                val result=client.getAssertion(origin,request,null,callerLabel)\n                return if(allowed(origin)) result else Result.failure(SecurityException("FIDO ceremony no longer authorized"))'),
('                if(!policy.allowsNavigation(url.orEmpty())) block(Failure.NAVIGATION)', '                if(registrationBridge?.hasStarted==true || !policy.allowsNavigation(url.orEmpty())) block(Failure.NAVIGATION)'),
('            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {', '            override fun onPageFinished(view:WebView,url:String?) {\n                if(!closed && !failed) registrationBridge?.onPageFinished(url.orEmpty())\n            }\n            override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {'),
('        closed=true\n        scope.cancel()', '        closed=true\n        registrationBridge?.close()\n        registrationBridge=null\n        scope.cancel()')
]
text = raw.decode()
for old, new in edits:
    assert text.count(old) == 1, 'Host structure changed: ' + old
    text = text.replace(old, new)
for path in files:
    assert not Path(path).exists(), 'Candidate already exists: ' + path
for path, content in files.items():
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')
host.write_text(text, encoding='utf-8')
subprocess.run(['git', 'add', '--', host_path, *files], check=True)
subprocess.run(['git', 'diff', '--cached', '--check'], check=True)
expected = {host_path, *files}
actual = set(subprocess.check_output(['git', 'diff', '--cached', '--name-only'], text=True).splitlines())
assert actual == expected, (actual, expected)
out = Path('validation')
out.mkdir(exist_ok=True)
(out/'browser-candidate.patch').write_text(subprocess.check_output(['git','diff','--cached','--binary'],text=True))
