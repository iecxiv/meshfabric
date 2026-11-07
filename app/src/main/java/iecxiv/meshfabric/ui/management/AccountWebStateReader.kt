package iecxiv.meshfabric.ui.management

import iecxiv.meshfabric.data.remote.OkHttpHelper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder

internal fun embeddedWebState(html:String):JSONObject? {
    val match=Regex("""\bvar\s+webState\s*=\s*(['"])(.*?)\1\s*;""").find(html) ?: return null
    val encoded=match.groupValues[2]
    if(encoded.length>60000) return null
    val decoded=runCatching { URLDecoder.decode(encoded.replace("+","%2B"),"UTF-8") }.getOrNull() ?: return null
    return webNoticeState(JSONObject().put("action","userWebState").put("state",decoded))
}

internal class AccountWebStateReader {
    private var active:Call?=null
    @Synchronized fun isRunning()=active!=null
    @Synchronized fun cancel() { active?.cancel(); active=null }
    private fun complete(call:Call,state:JSONObject?,reason:String,callback:(JSONObject?,String)->Unit) {
        val deliver=synchronized(this) { if(active!==call) false else { active=null; true } }
        if(deliver) callback(state,reason)
    }
    @Synchronized fun load(identity:Triple<String,String,Boolean>,callback:(JSONObject?,String)->Unit) {
        cancel()
        val original=identity.first.toHttpUrlOrNull()
        if(original==null || identity.second.isBlank()) { callback(null,"invalid_session"); return }
        val url=original.newBuilder().encodedPath(original.encodedPath.trimEnd('/')+"/")
            .query(null).fragment(null).addQueryParameter("nominify","1").build()
        val client=OkHttpHelper.createClient(identity.third).newBuilder()
            .followRedirects(false).followSslRedirects(false).callTimeout(20,java.util.concurrent.TimeUnit.SECONDS).build()
        val call=client.newCall(Request.Builder().url(url).header("Cookie",identity.second)
            .header("Accept","text/html").header("Cache-Control","no-cache, no-store").build())
        active=call
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) { complete(call,null,"network_failure",callback) }
            override fun onResponse(call:Call,response:Response) {
                var reason="state_not_found_or_invalid"
                val state=runCatching { response.use { reply->
                    if(!reply.isSuccessful) { reason="http_${reply.code}"; return@use null }
                    val type=reply.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                    if(type !in setOf("text/html","application/xhtml+xml")) { reason="unexpected_content_type"; return@use null }
                    val bytes=reply.peekBody(4194305L).bytes()
                    if(bytes.size>4194304) { reason="page_too_large"; return@use null }
                    embeddedWebState(String(bytes,Charsets.UTF_8))
                } }.getOrNull()
                complete(call,state,if(state==null) reason else "",callback)
            }
        })
    }
}
