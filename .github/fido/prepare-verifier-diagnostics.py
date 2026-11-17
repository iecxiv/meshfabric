from pathlib import Path
import hashlib,json,subprocess
root='app/src/main/java/iecxiv/meshfabric/'
v=root+'data/remote/FidoSessionVerifier.kt'
f=root+'data/remote/FidoLoginFailure.kt'
flow=root+'ui/login/FidoLoginFlow.kt'
test='app/src/test/java/iecxiv/meshfabric/data/remote/FidoSessionVerifierTest.kt'
expected={v:'0f2ab66761379b979d145709688da9d8bc02dbed',f:'a28cb45b8c99102dfd38392404de08dc70351222',flow:'e67b027e6eaf4b6cf8388c135e1eb1a520986348',test:'32c40208708bd407f8c9ec35c65b9be41df19f66'}
sources={}
for n,sha in expected.items():
    old=Path(n).read_bytes()
    assert hashlib.sha1(b'blob '+str(len(old)).encode()+b'\0'+old).hexdigest()==sha,'Source changed: '+n
    sources[n]=old.decode('utf-8')
def edit(n,before,after):
    assert sources[n].count(before)==1,('Anchor changed',n,before)
    sources[n]=sources[n].replace(before,after)
edit(f,'ACCOUNT("FIDO-300"),','''ACCOUNT("FIDO-300"),
        VERIFY_CONNECTION("FIDO-301"), VERIFY_HTTP("FIDO-302"), VERIFY_TIMEOUT("FIDO-303"),
        VERIFY_CLOSED("FIDO-304"), VERIFY_IDENTITY("FIDO-305"), VERIFY_PROTOCOL("FIDO-306"),
        VERIFY_TLS("FIDO-307"), VERIFY_INVALIDATED("FIDO-308"),
        VERIFY_UNAUTHORIZED("FIDO-311"), VERIFY_FORBIDDEN("FIDO-312"),
        VERIFY_REDIRECT("FIDO-313"), VERIFY_NOT_WEBSOCKET("FIDO-314"),''')
edit(flow,'override fun verify(pending:FidoLoginHttpTransport.PendingLogin)=verifier.verify(pending)','''override fun verify(pending:FidoLoginHttpTransport.PendingLogin):Boolean {
                if(!verifier.verify(pending)) throw FidoLoginFailure(verifier.failureCode ?: FidoLoginFailure.Code.ACCOUNT)
                return true
            }''')
sources[v]=r'''package iecxiv.meshfabric.data.remote

import com.google.gson.JsonParser
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class FidoSessionVerifier(
    private val binding:FidoLoginBinding,
    private val current:()->FidoLoginBinding,
    client:OkHttpClient=OkHttpHelper.createClient(false),
    private val timeoutMs:Long=15_000L
):Closeable {
    private val policy=FidoWebOriginPolicy(binding.server)
    private val http=client.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false)
        .followSslRedirects(false).retryOnConnectionFailure(false).build()
    private val started=AtomicBoolean(false)
    private val lock=Any()
    @Volatile private var closed=false
    @Volatile var failureCode:FidoLoginFailure.Code?=null
        private set
    private var active:WebSocket?=null
    private var cancelWait:(()->Unit)?=null
    init { require(timeoutMs in 1..15_000L) }
    private fun live()=runCatching { current()==binding }.getOrDefault(false)
    private fun reject(code:FidoLoginFailure.Code):Boolean { failureCode=code; return false }
    private fun accountMatches(id:String):Boolean {
        val parts=id.split("/",limit=3)
        return parts.size==3 && parts[0]=="user" && parts[2].isNotEmpty() && parts[2].equals(binding.account,ignoreCase=true)
    }
    fun verify(pending:FidoLoginHttpTransport.PendingLogin):Boolean {
        if(!started.compareAndSet(false,true)) return false
        if(closed || pending.binding!=binding || binding.generation==Long.MIN_VALUE || !live()) return reject(FidoLoginFailure.Code.VERIFY_INVALIDATED)
        val cookie=pending.cookie
        if(cookie.isBlank() || cookie.any { it=='\r' || it=='\n' }) return reject(FidoLoginFailure.Code.COOKIES)
        val request=try {
            require(policy.allowsNavigation(binding.server+"/control.ashx"))
            Request.Builder().url(OkHttpHelper.toWebSocketUrl(binding.server,"/control.ashx")).header("Cookie",cookie).build()
        } catch(_:Exception) { return reject(FidoLoginFailure.Code.VERIFY_PROTOCOL) }
        val outcome=AtomicReference<Boolean?>(null)
        val done=CountDownLatch(1)
        fun finish(value:Boolean,code:FidoLoginFailure.Code?=null) {
            if(outcome.compareAndSet(null,value)) {
                failureCode=if(value) null else code ?: FidoLoginFailure.Code.ACCOUNT
                done.countDown()
            }
        }
        val listener=object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {
                if(closed || !live()) { finish(false,FidoLoginFailure.Code.VERIFY_INVALIDATED); return }
                if(!webSocket.send("""{"action":"userinfo"}""")) finish(false,FidoLoginFailure.Code.VERIFY_CONNECTION)
            }
            override fun onMessage(webSocket:WebSocket,text:String) {
                if(closed || !live()) { finish(false,FidoLoginFailure.Code.VERIFY_INVALIDATED); return }
                if(text.length>1_048_576) { finish(false,FidoLoginFailure.Code.VERIFY_PROTOCOL); return }
                val packet=runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
                val action=packet.get("action")
                if(action==null || !action.isJsonPrimitive || !action.asJsonPrimitive.isString || action.asString!="userinfo") return
                val id=runCatching {
                    packet.getAsJsonObject("userinfo")?.get("_id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                }.getOrNull()
                if(id==null) { finish(false,FidoLoginFailure.Code.VERIFY_PROTOCOL); return }
                finish(accountMatches(id),FidoLoginFailure.Code.VERIFY_IDENTITY)
            }
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { finish(false,FidoLoginFailure.Code.VERIFY_CLOSED) }
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { finish(false,FidoLoginFailure.Code.VERIFY_CLOSED) }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) {
                val reason=when {
                    closed || !live() -> FidoLoginFailure.Code.VERIFY_INVALIDATED
                    FidoLoginFailure.transportCode(t)==FidoLoginFailure.Code.TLS -> FidoLoginFailure.Code.VERIFY_TLS
                    response!=null -> when(response.code) {
                        401 -> FidoLoginFailure.Code.VERIFY_UNAUTHORIZED
                        403 -> FidoLoginFailure.Code.VERIFY_FORBIDDEN
                        in 300..399 -> FidoLoginFailure.Code.VERIFY_REDIRECT
                        200 -> FidoLoginFailure.Code.VERIFY_NOT_WEBSOCKET
                        101 -> FidoLoginFailure.Code.VERIFY_PROTOCOL
                        else -> FidoLoginFailure.Code.VERIFY_HTTP
                    }
                    else -> FidoLoginFailure.Code.VERIFY_CONNECTION
                }
                finish(false,reason)
            }
        }
        synchronized(lock) {
            if(closed) return reject(FidoLoginFailure.Code.VERIFY_INVALIDATED)
            cancelWait={ finish(false,FidoLoginFailure.Code.VERIFY_INVALIDATED) }
            try { active=http.newWebSocket(request,listener) }
            catch(_:Exception) { finish(false,FidoLoginFailure.Code.VERIFY_CONNECTION) }
        }
        try {
            if(!done.await(timeoutMs,TimeUnit.MILLISECONDS)) {
                finish(false,FidoLoginFailure.Code.VERIFY_TIMEOUT)
                return reject(FidoLoginFailure.Code.VERIFY_TIMEOUT)
            }
            if(closed || !live()) return reject(FidoLoginFailure.Code.VERIFY_INVALIDATED)
            return outcome.get()==true
        } catch(_:InterruptedException) {
            Thread.currentThread().interrupt()
            return reject(FidoLoginFailure.Code.VERIFY_INVALIDATED)
        } finally { close() }
    }
    override fun close() {
        val resources=synchronized(lock) {
            closed=true
            val result=Pair(active,cancelWait)
            active=null; cancelWait=null
            result
        }
        resources.second?.invoke()
        resources.first?.cancel()
    }
}
'''
extra=r'''
    private fun expectDiagnostic(code:FidoLoginFailure.Code) {
        assertFalse(verifier.verify(pending())); assertEquals(code,verifier.failureCode)
    }
    @Test fun diagnosticUnauthorized() { server.enqueue(MockResponse().setResponseCode(401)); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_UNAUTHORIZED) }
    @Test fun diagnosticForbidden() { server.enqueue(MockResponse().setResponseCode(403)); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_FORBIDDEN) }
    @Test fun diagnosticRedirect() { server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","https://other.example/login")); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_REDIRECT); assertEquals(1,server.requestCount) }
    @Test fun diagnosticLoginPage() { server.enqueue(MockResponse().setBody("login")); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_NOT_WEBSOCKET) }
    @Test fun diagnosticHttpError() { server.enqueue(MockResponse().setResponseCode(500)); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_HTTP) }
    @Test fun diagnosticTimeout() { server.enqueue(upgrade { _,_ -> }); create(300); expectDiagnostic(FidoLoginFailure.Code.VERIFY_TIMEOUT) }
    @Test fun diagnosticClosed() { server.enqueue(upgrade { socket,_ -> socket.close(1000,"private reason") }); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_CLOSED) }
    @Test fun diagnosticWrongAccount() { server.enqueue(answers("user//bob")); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_IDENTITY) }
    @Test fun diagnosticMissingId() { server.enqueue(upgrade { socket,_ -> socket.send("""{"action":"userinfo","userinfo":{}}""") }); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_PROTOCOL) }
    @Test fun diagnosticNonStringId() { server.enqueue(upgrade { socket,_ -> socket.send("""{"action":"userinfo","userinfo":{"_id":123}}""") }); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_PROTOCOL) }
    @Test fun diagnosticTls() { server.enqueue(answers("user//alice")); create(3000,OkHttpHelper.createClient(false)); expectDiagnostic(FidoLoginFailure.Code.VERIFY_TLS); assertEquals(0,server.requestCount) }
    @Test fun diagnosticChangedBinding() { live.set(binding.copy(account="bob")); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_INVALIDATED); assertEquals(0,server.requestCount) }
    @Test fun diagnosticEmptyCookie() { create(); assertFalse(verifier.verify(FidoLoginHttpTransport.PendingLogin(binding,""))); assertEquals(FidoLoginFailure.Code.COOKIES,verifier.failureCode); assertEquals(0,server.requestCount) }
    @Test fun diagnosticInitialUserinfo() {
        server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:okhttp3.Response) { webSocket.send(identity("user//alice")) }
        }))
        create(); assertTrue(verifier.verify(pending())); assertNull(verifier.failureCode)
    }
    @Test fun diagnosticCancellation() {
        server.enqueue(upgrade { _,_ -> }); create(10000)
        val result=AtomicReference<Boolean?>(null)
        val thread=Thread { result.set(verifier.verify(pending())) }; thread.start()
        try {
            assertNotNull(server.takeRequest(5,TimeUnit.SECONDS)); verifier.close(); thread.join(3000)
            assertFalse(thread.isAlive); assertEquals(false,result.get()); assertEquals(FidoLoginFailure.Code.VERIFY_INVALIDATED,verifier.failureCode)
        } finally { verifier.close(); thread.join(3000) }
    }
    @Test fun diagnosticConnectionFailure() { server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST)); create(); expectDiagnostic(FidoLoginFailure.Code.VERIFY_CONNECTION) }
'''
old=sources[test].rstrip()
assert old.endswith('}') and extra.count('@Test')==16 and old.count('@Test')==16
sources[test]=old[:-1]+extra+'}\n'
for n,text in sources.items(): Path(n).write_text(text,encoding='utf-8')
subprocess.run(['git','add','--',*sorted(sources)],check=True)
subprocess.run(['git','diff','--cached','--check'],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==set(expected)
out=Path('validation');out.mkdir(exist_ok=True)
(out/'verifier-diagnostics.patch').write_bytes(subprocess.check_output(['git','diff','--cached','--binary']))
(out/'source-digests.json').write_text(json.dumps({n:hashlib.sha256(Path(n).read_bytes()).hexdigest() for n in sorted(sources)},indent=2)+'\n')
(out/'preparer-sha256.txt').write_text(hashlib.sha256(Path(__file__).read_bytes()).hexdigest()+'\n')
