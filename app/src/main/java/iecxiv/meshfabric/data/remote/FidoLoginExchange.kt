package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

internal data class FidoLoginBinding(val server:String,val account:String,val generation:Long)

/** One attempt's request/session state, not a transport or authentication verifier.
 * The transport must disable automatic redirects and connection retries for POSTs.
 * It must obtain hwstate/options from the selected server and verify authentication
 * independently. HTTP success, a redirect or a cookie never establishes success here.
 */
internal class FidoLoginExchange(
    private val binding:FidoLoginBinding,
    private val clock:()->Long={System.nanoTime()/1_000_000L},
    private val wallClock:()->Long={System.currentTimeMillis()}
) {
    enum class Phase { NEW,PASSWORD_SENT,CHALLENGE,SUBMITTED,CANCELLED,EXPIRED,INVALID,UNKNOWN }
    @Volatile var phase=Phase.NEW
        private set
    private val policy=FidoWebOriginPolicy(binding.server)
    private val base=requireNotNull(binding.server.toHttpUrlOrNull())
    private val cookies=mutableListOf<Cookie>()
    private var started=0L
    private var budget=60_000L
    private var options:FidoLoginContract.Options?=null
    private var target:HttpUrl?=null
    private var hwstate=""
    init { require(binding.account.isNotBlank() && binding.generation>=0) }
    private fun terminal()=phase in setOf(Phase.CANCELLED,Phase.EXPIRED,Phase.INVALID,Phase.UNKNOWN)
    private fun stop(next:Phase) { phase=next; cookies.clear(); options=null; target=null; hwstate="" }
    private fun valid(current:FidoLoginBinding):Boolean {
        if(terminal()) return false
        if(current!=binding) { stop(if(phase==Phase.SUBMITTED) Phase.UNKNOWN else Phase.CANCELLED); return false }
        if(phase!=Phase.NEW && clock()-started>=budget) { stop(if(phase==Phase.SUBMITTED) Phase.UNKNOWN else Phase.EXPIRED); return false }
        return true
    }
    private fun header(url:HttpUrl):String {
        cookies.removeAll { it.expiresAt<=wallClock() }
        return cookies.filter { it.matches(url) }.sortedByDescending { it.path.length }.joinToString("; ") { it.name+"="+it.value }
    }
    private fun request(url:HttpUrl,form:FormBody):Request=Request.Builder().url(url)
        .apply { val value=header(url); if(value.isNotEmpty()) header("Cookie",value) }
        .post(form).build()
    @Synchronized fun password(current:FidoLoginBinding,password:String):Request? {
        if(!valid(current) || phase!=Phase.NEW) return null
        require(password.isNotEmpty() && password.length<=256)
        val form=FormBody.Builder().add("action","login").add("username",binding.account).add("password",password).build()
        val result=request(base.newBuilder().addPathSegment("login").build(),form)
        started=clock(); phase=Phase.PASSWORD_SENT
        return result
    }
    @Synchronized fun observeCookies(current:FidoLoginBinding,responseUrl:String,setCookies:List<String>):Boolean {
        if(!valid(current) || phase==Phase.NEW) return false
        return runCatching {
            require(policy.allowsNavigation(responseUrl) && setCookies.size<=64)
            val url=requireNotNull(responseUrl.toHttpUrlOrNull())
            for(line in setCookies) {
                require(line.length<=16384)
                val cookie=Cookie.parse(url,line) ?: continue
                cookies.removeAll { it.name==cookie.name && it.domain==cookie.domain && it.path==cookie.path }
                if(cookie.expiresAt>wallClock()) cookies.add(cookie)
            }
            require(cookies.size<=64)
            true
        }.getOrElse { stop(if(phase==Phase.SUBMITTED) Phase.UNKNOWN else Phase.INVALID); false }
    }
    @Synchronized fun challenge(current:FidoLoginBinding,responseUrl:String,postUrl:String,state:String,raw:JsonObject):JsonObject? {
        if(!valid(current) || phase!=Phase.PASSWORD_SENT) return null
        return runCatching {
            require(policy.allowsNavigation(responseUrl) && policy.allowsNavigation(postUrl))
            require(state.isNotEmpty() && state.length<=16384 && state.none { it=='\r' || it=='\n' })
            val decoded=FidoLoginContract.options(raw)
            val timeout=decoded.publicKey.get("timeout")?.asLong ?: 60_000L
            budget=minOf(budget,(clock()-started)+timeout)
            target=requireNotNull(postUrl.toHttpUrlOrNull()); hwstate=state; options=decoded
            phase=Phase.CHALLENGE
            decoded.publicKey.deepCopy()
        }.getOrElse { stop(Phase.INVALID); null }
    }
    @Synchronized fun submit(current:FidoLoginBinding,credential:JsonObject):Request? {
        if(!valid(current) || phase!=Phase.CHALLENGE) return null
        return runCatching {
            val fields=FidoLoginContract.form(requireNotNull(options),credential,policy.origin)
            val form=FormBody.Builder().add("action","tokenlogin").add("hwstate",hwstate)
            fields.forEach { (key,value)->form.add(key,value) }
            val result=request(requireNotNull(target),form.build())
            options=null; hwstate=""; target=null; phase=Phase.SUBMITTED
            result
        }.getOrElse { stop(Phase.INVALID); null }
    }
    @Synchronized fun get(current:FidoLoginBinding,url:String):Request? {
        if(!valid(current) || phase !in setOf(Phase.PASSWORD_SENT,Phase.SUBMITTED)) return null
        if(!policy.allowsNavigation(url)) { stop(if(phase==Phase.SUBMITTED) Phase.UNKNOWN else Phase.INVALID); return null }
        val target=requireNotNull(url.toHttpUrlOrNull())
        return Request.Builder().url(target).get().header("Cache-Control","no-store")
            .apply { val value=header(target); if(value.isNotEmpty()) header("Cookie",value) }.build()
    }
    @Synchronized fun cookieHeaderForVerification(current:FidoLoginBinding):String? {
        if(!valid(current) || phase!=Phase.SUBMITTED) return null
        return header(base.newBuilder().addPathSegment("control.ashx").build())
    }
    @Synchronized fun poll(current:FidoLoginBinding):Phase { valid(current); return phase }
    @Synchronized fun cancel() { if(!terminal()) stop(if(phase==Phase.SUBMITTED) Phase.UNKNOWN else Phase.CANCELLED) }
}
