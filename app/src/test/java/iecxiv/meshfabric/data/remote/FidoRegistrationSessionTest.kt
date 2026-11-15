package iecxiv.meshfabric.data.remote

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class FidoRegistrationSessionTest {
    private var now=0L
    private val binding=FidoRegistrationBinding("https://server.example/tenant","user//alice",7)
    private val challenge=Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private fun json(s:String)=JsonParser.parseString(s).asJsonObject
    private fun options(user:String=binding.account)=json("""{"action":"webauthn-startregister","keyname":"Primary","request":{"challenge":"$challenge","timeout":60000,"rp":{"name":"Service"},"user":{"id":"AQID","name":"$user"},"pubKeyCredParams":[{"type":"public-key","alg":-7}]}}""")
    private fun credential():com.google.gson.JsonObject {
        val data="""{"type":"webauthn.create","origin":"https://server.example","challenge":"$challenge"}"""
        val encoded=Base64.getEncoder().encodeToString(data.toByteArray())
        return json("""{"type":"public-key","id":"AQID","rawId":"AQID","response":{"clientDataJSON":"$encoded","attestationObject":"AQID"}}""")
    }
    private fun session(previous:Set<Long> = emptySet())=FidoRegistrationSession(binding,"Primary",previous,false) { now }
    private fun ready():FidoRegistrationSession {
        val s=session()
        assertNotNull(s.start(binding)); assertNotNull(s.acceptOptions(binding,options()))
        return s
    }
    private fun submitted():FidoRegistrationSession {
        val s=ready(); assertNotNull(s.submit(binding,credential())); return s
    }
    @Test fun completionRequiresMatchingReadback() {
        val s=submitted()
        assertNotNull(s.acknowledge(binding,json("""{"action":"otp-hkey-setup-response","result":true,"index":9}""")))
        assertEquals(FidoRegistrationSession.Phase.READBACK,s.phase)
        assertFalse(s.readback(binding,json("""{"action":"otp-hkey-get","keys":[]}""")))
        assertTrue(s.readback(binding,json("""{"action":"otp-hkey-get","keys":[{"i":9,"name":"Primary","type":3}]}""")))
        assertEquals(FidoRegistrationSession.Phase.COMPLETE,s.phase)
    }
    @Test fun submissionCannotBeReplayed() { val s=submitted(); assertNull(s.submit(binding,credential())); assertEquals(FidoRegistrationSession.Phase.ACK,s.phase) }
    @Test fun serverChangeCancelsBeforeSubmission() { val s=ready(); assertNull(s.submit(binding.copy(server="https://other.example"),credential())); assertEquals(FidoRegistrationSession.Phase.CANCELLED,s.phase) }
    @Test fun accountChangeCancelsBeforeSubmission() { val s=ready(); assertNull(s.submit(binding.copy(account="user//bob"),credential())); assertEquals(FidoRegistrationSession.Phase.CANCELLED,s.phase) }
    @Test fun socketChangeCancelsBeforeSubmission() { val s=ready(); assertNull(s.submit(binding.copy(generation=8),credential())); assertEquals(FidoRegistrationSession.Phase.CANCELLED,s.phase) }
    @Test fun expiryBeforeSubmissionIsDefinite() { val s=ready(); now=60000; assertNull(s.submit(binding,credential())); assertEquals(FidoRegistrationSession.Phase.EXPIRED,s.phase) }
    @Test fun expiryAfterSubmissionIsUnknown() { val s=submitted(); now=60000; assertEquals(FidoRegistrationSession.Phase.UNKNOWN,s.poll(binding)) }
    @Test fun cancellationAfterSubmissionIsUnknown() { val s=submitted(); s.cancel(); assertEquals(FidoRegistrationSession.Phase.UNKNOWN,s.phase); assertNull(s.start(binding)) }
    @Test fun optionsForAnotherAccountAreRejected() { val s=session(); s.start(binding); assertNull(s.acceptOptions(binding,options("user//bob"))); assertEquals(FidoRegistrationSession.Phase.INVALID,s.phase) }
    @Test fun existingIndexCannotConfirmNewRegistration() {
        val s=session(setOf(9L)); s.start(binding); s.acceptOptions(binding,options()); s.submit(binding,credential())
        assertNull(s.acknowledge(binding,json("""{"action":"otp-hkey-setup-response","result":true,"index":9}""")))
        assertEquals(FidoRegistrationSession.Phase.UNKNOWN,s.phase)
    }
}
