package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class FidoRegistrationContractTest {
    private val origin="https://server.example:8443"
    private val challenge=Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private fun json(s:String)=JsonParser.parseString(s).asJsonObject
    private fun b64(s:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
    private fun credential(o:String=origin,c:String=challenge):JsonObject {
        val client=json("""{"type":"webauthn.create","origin":"$o","challenge":"$c","crossOrigin":false}""")
        return json("""{"type":"public-key","id":"AQID","rawId":"AQID","response":{"clientDataJSON":"${b64(client.toString())}","attestationObject":"AQID"}}""")
    }
    private fun options()=json("""{"action":"webauthn-startregister","keyname":"Primary","request":{"challenge":"$challenge","rp":{"name":"Service"},"user":{"id":"AQID","name":"user//alice"},"pubKeyCredParams":[{"type":"public-key","alg":-7}]}}""")
    @Test fun startUsesAuditedCommand() {
        val p=FidoRegistrationContract.start("Primary")
        assertEquals("webauthn-startregister",p.get("action").asString)
        assertEquals("Primary",p.get("name").asString)
    }
    @Test(expected=IllegalArgumentException::class) fun blankNameRejected() { FidoRegistrationContract.start(" ") }
    @Test fun optionsAreDetached() {
        val reply=options()
        val value=FidoRegistrationContract.options(reply,"Primary")
        value.addProperty("challenge","changed")
        assertEquals(challenge,reply.getAsJsonObject("request").get("challenge").asString)
    }
    @Test(expected=IllegalArgumentException::class) fun differentNameRejected() { FidoRegistrationContract.options(options(),"Other") }
    @Test fun finishPreservesClientDataBytes() {
        val input=credential()
        val result=FidoRegistrationContract.finish("Primary",input,challenge,origin)
        assertEquals("webauthn-endregister",result.get("action").asString)
        assertEquals("Primary",result.get("name").asString)
        val output=result.getAsJsonObject("response").getAsJsonObject("response")
        assertArrayEquals(Base64.getUrlDecoder().decode(input.getAsJsonObject("response").get("clientDataJSON").asString),Base64.getDecoder().decode(output.get("clientDataJSON").asString))
    }
    @Test(expected=IllegalArgumentException::class) fun differentOriginRejected() { FidoRegistrationContract.finish("Primary",credential("https://other.example"),challenge,origin) }
    @Test(expected=IllegalArgumentException::class) fun differentChallengeRejected() { FidoRegistrationContract.finish("Primary",credential(c=b64("different challenge")),challenge,origin) }
    @Test fun booleanRejectionIsNotSuccess() { assertNull(FidoRegistrationContract.acknowledgedIndex(json("""{"action":"otp-hkey-setup-response","result":false}"""))) }
    @Test fun unsignedKeyIndexAccepted() { assertEquals(4294967295L,FidoRegistrationContract.acknowledgedIndex(json("""{"action":"otp-hkey-setup-response","result":true,"index":4294967295}"""))) }
    @Test(expected=IllegalArgumentException::class) fun stringSuccessRejected() { FidoRegistrationContract.acknowledgedIndex(json("""{"action":"otp-hkey-setup-response","result":"true","index":7}""")) }
}
