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

/** Confirms that the cookies of one FIDO attempt open a control session for the expected account.
 * Blocking and single use: run off the main thread and close on cancellation. A cookie, an HTTP
 * status or an open socket is never proof; only a userinfo reply naming the account is.
 */
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
    private var active:WebSocket?=null
    private fun accountMatches(id:String):Boolean {
        val parts=id.split("/",limit=3)
        return parts.size==3 && parts[0]=="user" && parts[2].isNotEmpty() && parts[2].equals(binding.account,ignoreCase=true)
    }
    fun verify(pending:FidoLoginHttpTransport.PendingLogin):Boolean {
        if(!started.compareAndSet(false,true)) return false
        val cookie=pending.cookie
        if(closed || pending.binding!=binding || binding.generation==Long.MIN_VALUE || current()!=binding) return false
        if(cookie.isBlank() || cookie.any { it=='\r' || it=='\n' }) return false
        val request=try {
            require(policy.allowsNavigation(binding.server+"/control.ashx"))
            Request.Builder().url(OkHttpHelper.toWebSocketUrl(binding.server,"/control.ashx")).header("Cookie",cookie).build()
        } catch(_:Exception) { return false }
        val outcome=AtomicReference<Boolean?>(null)
        val done=CountDownLatch(1)
        fun finish(value:Boolean) { if(outcome.compareAndSet(null,value)) done.countDown() }
        val listener=object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) {
                if(!webSocket.send("{\"action\":\"userinfo\"}")) finish(false)
            }
            override fun onMessage(webSocket:WebSocket,text:String) {
                if(text.length>1_048_576) { finish(false); return }
                val id=runCatching {
                    JsonParser.parseString(text).asJsonObject.takeIf { it.get("action")?.asString=="userinfo" }
                        ?.getAsJsonObject("userinfo")?.get("_id")?.asString
                }.getOrNull() ?: return
                finish(accountMatches(id) && current()==binding && !closed)
            }
            override fun onClosing(webSocket:WebSocket,code:Int,reason:String) { finish(false) }
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { finish(false) }
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { finish(false) }
        }
        synchronized(lock) {
            if(closed) return false
            active=http.newWebSocket(request,listener)
        }
        try {
            val finished=done.await(timeoutMs,TimeUnit.MILLISECONDS)
            return finished && outcome.get()==true && !closed && current()==binding
        } catch(_:InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        } finally {
            close()
        }
    }
    override fun close() {
        val socket=synchronized(lock) { closed=true; active.also { active=null } }
        socket?.cancel()
    }
}
