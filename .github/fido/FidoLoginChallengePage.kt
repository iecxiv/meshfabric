package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLDecoder
import java.util.Locale

/** Strict parser for the server's rendered token-login page; never evaluates JavaScript. */
internal object FidoLoginChallengePage {
    data class Challenge(val responseUrl:String,val postUrl:String,val state:String,val request:JsonObject)
    private val flags=setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)
    private val attributes=Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    private fun value(raw:String):String {
        val numeric=Regex("""&#(?:x([0-9a-fA-F]+)|([0-9]+));""").replace(raw) {
            val code=if(it.groupValues[1].isNotEmpty()) it.groupValues[1].toInt(16) else it.groupValues[2].toInt()
            require(Character.isValidCodePoint(code))
            String(Character.toChars(code))
        }
        return numeric.replace("&quot;","\"").replace("&apos;","'").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&")
    }
    private fun attrs(raw:String):Map<String,String> {
        val result=mutableMapOf<String,String>()
        for(m in attributes.findAll(raw)) {
            val name=m.groupValues[1].lowercase(Locale.ROOT)
            require(name !in result)
            val content=(2..4).firstNotNullOfOrNull { m.groups[it]?.value }.orEmpty()
            result[name]=value(content)
        }
        return result
    }
    fun parse(server:String,responseUrl:String,html:String):Challenge {
        val policy=FidoWebOriginPolicy(server)
        require(policy.allowsNavigation(responseUrl) && html.length<=2_097_152)
        val modes=Regex("""\bvar\s+loginMode\s*=\s*(['"])([0-9]+)\1""").findAll(html).toList()
        require(modes.size==1 && modes[0].groupValues[2]=="4")
        val matches=Regex("""\bvar\s+hardwareKeyChallenge\s*=\s*decodeURIComponent\(\s*(['"])([^'"]*)\1\s*\)""").findAll(html).toList()
        require(matches.size==1 && matches[0].groupValues[2].length<=131072)
        val decoded=URLDecoder.decode(matches[0].groupValues[2].replace("+","%2B"),"UTF-8")
        val challenge=JsonParser.parseString(decoded).asJsonObject
        FidoLoginContract.options(challenge)
        if(challenge.has("userVerification")) {
            val uv=challenge.get("userVerification")
            require(uv.isJsonPrimitive && uv.asJsonPrimitive.isString && uv.asString in setOf("required","preferred","discouraged"))
        }
        val forms=Regex("""<form\b([^>]*)>(.*?)</form\s*>""",flags).findAll(html).map { form ->
            attrs(form.groupValues[1]) to Regex("""<input\b([^>]*)>""",flags).findAll(form.groupValues[2]).map { attrs(it.groupValues[1]) }.toList()
        }.filter { (_,inputs)->inputs.any { it["name"]=="hwtoken" } && inputs.any { it["name"]=="action" && it["value"]=="tokenlogin" } }.toList()
        require(forms.size==1)
        val (form,inputs)=forms.single()
        require(form["method"]?.equals("post",ignoreCase=true)==true)
        require(inputs.count { it["name"]=="action" }==1 && inputs.count { it["name"]=="hwtoken" }==1)
        val states=inputs.filter { it["name"]=="hwstate" }
        require(states.size==1 && states[0]["type"]?.equals("hidden",ignoreCase=true)==true)
        val state=requireNotNull(states[0]["value"])
        require(state.isNotEmpty() && state.length<=16384 && state.none { it=='\r' || it=='\n' })
        val response=requireNotNull(responseUrl.toHttpUrlOrNull())
        val post=requireNotNull(response.resolve(form["action"].orEmpty())).toString()
        require(policy.allowsNavigation(post))
        return Challenge(responseUrl,post,state,challenge.deepCopy())
    }
}
