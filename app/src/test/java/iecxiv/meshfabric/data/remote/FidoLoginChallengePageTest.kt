package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonArray
import java.net.URLEncoder
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class FidoLoginChallengePageTest {
    private val server="https://server.example/tenant"
    private val url="$server/"
    private fun page(mode:String="4",state:String="opaque+state&amp;x=1",action:String="",method:String="post",uv:String="required"):String {
        val challenge=JsonObject().apply { addProperty("type","webAuthn"); addProperty("challenge",Base64.getEncoder().encodeToString(ByteArray(32))); add("keyIds",JsonArray().apply { add("AQID") }); addProperty("userVerification",uv) }
        val encoded=URLEncoder.encode(challenge.toString(),"UTF-8").replace("+","%20")
        return """<script>var loginMode = '$mode'; var hardwareKeyChallenge = decodeURIComponent('$encoded');</script><form method=$method action="$action"><input type=hidden name=action value=tokenlogin><input type=hidden name=hwstate value="$state"><input name=hwtoken id=hwtokenInput></form>"""
    }
    private fun parse(html:String)=FidoLoginChallengePage.parse(server,url,html)
    private fun reject(block:()->Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun extractsStateWithoutChangingPlus() { assertEquals("opaque+state&x=1",parse(page()).state) }
    @Test fun keepsDefaultPostTarget() { assertEquals(url,parse(page()).postUrl) }
    @Test fun resolvesRelativePostTarget() { assertEquals("${server}/login",parse(page(action="login")).postUrl) }
    @Test fun preservesVerificationRequirement() { assertEquals("required",parse(page()).request.get("userVerification").asString) }
    @Test fun rejectsOtherOriginAction() { reject { parse(page(action="https://other.example/login")) } }
    @Test fun rejectsOtherOriginResponse() { reject { FidoLoginChallengePage.parse(server,"https://other.example",page()) } }
    @Test fun rejectsResetMode() { reject { parse(page(mode="5")) } }
    @Test fun rejectsGetForm() { reject { parse(page(method="get")) } }
    @Test fun rejectsEmptyState() { reject { parse(page(state="")) } }
    @Test fun rejectsDuplicateTokenForms() { reject { parse(page()+page().substringAfter("</script>")) } }
    @Test fun rejectsUnsupportedVerification() { reject { parse(page(uv="anything")) } }
    @Test fun rejectsMissingChallenge() { reject { parse("<form method=post></form>") } }
}
