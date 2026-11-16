package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Blocking IO; caller must run off the main thread and close on cancellation/disposal. */
internal class FidoLoginHttpTransport(
    private val binding:FidoLoginBinding,
    private val current:()->FidoLoginBinding,
    client:OkHttpClient=OkHttpHelper.createClient(false)
):Closeable {
    class PendingLogin internal constructor(val binding:FidoLoginBinding,val cookie:String) {
        override fun toString()="PendingFidoLogin(<redacted>)"
    }
    private data class Page(val url:String,val code:Int,val location:String?,val body:String)
    private val policy=FidoWebOriginPolicy(binding.server)
    private val exchange=FidoLoginExchange(binding)
    private val http=client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).cookieJar(CookieJar.NO_COOKIES).cache(null)
        .callTimeout(20,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
    private val running=AtomicBoolean(false)
    private val lock=Any()
    @Volatile private var closed=false
    private var active:Call?=null
    private fun guard() {
        check(!closed)
        require(exchange.poll(current()) in setOf(FidoLoginExchange.Phase.NEW,FidoLoginExchange.Phase.PASSWORD_SENT,FidoLoginExchange.Phase.CHALLENGE,FidoLoginExchange.Phase.SUBMITTED))
    }
    private fun <T> operation(block:()->T):T {
        check(running.compareAndSet(false,true)) { "FIDO exchange already running" }
        try { guard(); return block() } catch(e:Exception) { close(); throw IOException("FIDO HTTP exchange failed",e) } finally { running.set(false) }
    }
    fun begin(password:String):JsonObject=operation {
        val first=requireNotNull(exchange.password(current(),password))
        val page=execute(first,true)
        require(page.code in 200..299)
        val challenge=FidoLoginChallengePage.parse(binding.server,page.url,page.body)
        val options=requireNotNull(exchange.challenge(current(),challenge.responseUrl,challenge.postUrl,challenge.state,challenge.request))
        options.addProperty("userVerification",challenge.request.get("userVerification")?.asString ?: "preferred")
        if(!options.has("timeout")) options.addProperty("timeout",60000)
        guard()
        options
    }
    /** A pending cookie is not authenticated proof. Verify the expected user separately. */
    fun submit(credential:JsonObject):PendingLogin=operation {
        val first=exchange.submit(current(),credential) ?: throw FidoLoginFailure(FidoLoginFailure.Code.ASSERTION)
        val page=try { execute(first,false) } catch(e:Exception) {
            throw FidoLoginFailure(FidoLoginFailure.transportCode(e))
        }
        if(page.code !in 200..299) throw FidoLoginFailure(FidoLoginFailure.Code.HTTP_STATUS)
        val cookies=exchange.cookieHeaderForVerification(current()).orEmpty()
        if(cookies.isEmpty()) throw FidoLoginFailure(FidoLoginFailure.Code.COOKIES)
        guard()
        PendingLogin(binding,cookies)
    }
    private fun execute(initial:Request,readHtml:Boolean):Page {
        var request=initial
        repeat(6) {
            guard()
            require(policy.allowsNavigation(request.url.toString()))
            val call=http.newCall(request)
            synchronized(lock) { check(!closed); active=call }
            val page=try {
                call.execute().use { response ->
                    guard()
                    val url=response.request.url.toString()
                    require(exchange.observeCookies(current(),url,response.headers.values("Set-Cookie")))
                    val redirect=response.code in setOf(301,302,303,307,308)
                    var html=""
                    if(readHtml && !redirect) {
                        val body=requireNotNull(response.body)
                        val source=body.source()
                        require(!source.request(2_097_153)) { "FIDO page too large" }
                        require(source.buffer.size<=2_097_152)
                        html=source.readUtf8()
                    }
                    Page(url,response.code,response.header("Location"),html)
                }
            } finally { synchronized(lock) { if(active===call) active=null } }
            guard()
            if(page.code !in setOf(301,302,303,307,308)) return page
            require(request.method=="GET" || page.code in setOf(301,302,303)) { "Refusing automatic POST replay" }
            val location=requireNotNull(page.location)
            val next=requireNotNull(request.url.resolve(location)).toString()
            require(policy.allowsNavigation(next))
            request=requireNotNull(exchange.get(current(),next))
        }
        throw IOException("Too many FIDO redirects")
    }
    override fun close() {
        val call=synchronized(lock) { closed=true; active.also { active=null } }
        call?.cancel()
        exchange.cancel()
    }
}
