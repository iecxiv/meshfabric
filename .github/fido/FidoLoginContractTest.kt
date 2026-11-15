package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class FidoLoginContractTest {
    private val origin="https://server.example"
    private val challenge=Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private fun json(s:String)=JsonParser.parseString(s).asJsonObject
    private fun request()=json("""{"type":"webAuthn","challenge":"$challenge","keyIds":["AQID"],"timeout":90000}""")
    private fun options()=FidoLoginContract.options(request())
    private fun credential(kind:String="webauthn.get",site:String=origin,nonce:String=challenge,cross:String="false"):JsonObject {
        val client="""{ "type":"$kind", "origin":"$site", "challenge":"$nonce", "crossOrigin":$cross }"""
        return json("""{"type":"public-key","id":"AQID","rawId":"AQID","response":{"clientDataJSON":"${Base64.getEncoder().encodeToString(client.toByteArray())}","authenticatorData":"${Base64.getEncoder().encodeToString(ByteArray(37))}","signature":"AQID","userHandle":null}}""")
    }
    private fun reject(block:()->Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun createsRequestAndBoundsTimeout() { val p=options().publicKey; assertEquals(challenge,p.get("challenge").asString); assertEquals(60000L,p.get("timeout").asLong); assertEquals(1,p.getAsJsonArray("allowCredentials").size()) }
    @Test fun formUsesOnlyHardwareToken() { assertEquals(setOf("hwtoken"),FidoLoginContract.form(options(),credential(),origin).keys) }
    @Test fun clientDataBytesArePreserved() { val c=credential(); val result=json(FidoLoginContract.form(options(),c,origin).getValue("hwtoken")); assertEquals(c.getAsJsonObject("response").get("clientDataJSON"),result.get("clientDataJSON")) }
    @Test fun nullHandleIsEmptyString() { val result=json(FidoLoginContract.form(options(),credential(),origin).getValue("hwtoken")); assertEquals("",result.get("userHandle").asString) }
    @Test fun populatedHandleIsPreserved() { val c=credential(); c.getAsJsonObject("response").addProperty("userHandle","BAUG"); val result=json(FidoLoginContract.form(options(),c,origin).getValue("hwtoken")); assertEquals("BAUG",result.get("userHandle").asString) }
    @Test fun registrationResponseIsRejected() { reject { FidoLoginContract.form(options(),credential(kind="webauthn.create"),origin) } }
    @Test fun changedOriginIsRejected() { reject { FidoLoginContract.form(options(),credential(site="https://other.example"),origin) } }
    @Test fun changedChallengeIsRejected() { reject { FidoLoginContract.form(options(),credential(nonce=Base64.getEncoder().encodeToString(ByteArray(32) { 9 })),origin) } }
    @Test fun crossOriginResponseIsRejected() { reject { FidoLoginContract.form(options(),credential(cross="true"),origin) } }
    @Test fun unlistedKeyIsRejected() { val c=credential(); c.addProperty("id","BAUG"); c.addProperty("rawId","BAUG"); reject { FidoLoginContract.form(options(),c,origin) } }
    @Test fun mismatchedIdsAreRejected() { val c=credential(); c.addProperty("id","BAUG"); reject { FidoLoginContract.form(options(),c,origin) } }
    @Test fun truncatedAuthenticatorDataIsRejected() { val c=credential(); c.getAsJsonObject("response").addProperty("authenticatorData","AQID"); reject { FidoLoginContract.form(options(),c,origin) } }
    @Test fun insecureOriginIsRejected() { reject { FidoLoginContract.form(options(),credential(),"http://server.example") } }
    @Test fun repeatedKeyIdsAreRejected() { val r=request(); r.getAsJsonArray("keyIds").add("AQID"); reject { FidoLoginContract.options(r) } }
    @Test fun missingKeysAreRejected() { val r=request(); r.remove("keyIds"); reject { FidoLoginContract.options(r) } }
    @Test fun invalidTimeoutIsRejected() { val r=request(); r.addProperty("timeout",0); reject { FidoLoginContract.options(r) } }
}
