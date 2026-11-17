package iecxiv.meshfabric.data.remote

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
