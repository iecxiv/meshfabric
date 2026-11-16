package iecxiv.meshfabric.data.remote

import com.google.gson.JsonParser
import okhttp3.FormBody
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class FidoLoginExchangeTest {
    private var now=0L
    private val origin="https://server.example"
    private val binding=FidoLoginBinding("$origin/tenant","alice",3)
    private val challenge=Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private fun json(s:String)=JsonParser.parseString(s).asJsonObject
    private fun options()=json("""{"type":"webAuthn","challenge":"$challenge","keyIds":["AQID"]}""")
    private fun session()=FidoLoginExchange(binding,{now})
    private fun credential():com.google.gson.JsonObject {
        val client="""{"type":"webauthn.get","origin":"$origin","challenge":"$challenge"}"""
        return json("""{"type":"public-key","id":"AQID","rawId":"AQID","response":{"clientDataJSON":"${Base64.getEncoder().encodeToString(client.toByteArray())}","authenticatorData":"${Base64.getEncoder().encodeToString(ByteArray(37))}","signature":"AQID"}}""")
    }
    private fun started():FidoLoginExchange { val s=session(); assertNotNull(s.password(binding,"secret")); return s }
    private fun ready():FidoLoginExchange { val s=started(); assertNotNull(s.challenge(binding,"$origin/tenant/","$origin/tenant/","opaque+state=",options())); return s }
    private fun fields(body:FormBody)=(0 until body.size).associate { body.name(it) to body.value(it) }
    @Test fun passwordRequestKeepsTenantPath() { val s=session(); val r=s.password(binding,"secret")!!; assertEquals("$origin/tenant/login",r.url.toString()); assertEquals(mapOf("action" to "login","username" to "alice","password" to "secret"),fields(r.body as FormBody)) }
    @Test fun assertionUsesTokenLoginAndExactState() { val r=ready().submit(binding,credential())!!; val f=fields(r.body as FormBody); assertEquals(setOf("action","hwstate","hwtoken"),f.keys); assertEquals("tokenlogin",f["action"]); assertEquals("opaque+state=",f["hwstate"]) }
    @Test fun cookieFromChallengeIsPreserved() { val s=ready(); assertTrue(s.observeCookies(binding,"$origin/tenant/",listOf("meshsession=abc; Path=/tenant; Secure; HttpOnly"))); assertEquals("meshsession=abc",s.submit(binding,credential())!!.header("Cookie")) }
    @Test fun updatedCookieReplacesOldValue() { val s=ready(); s.observeCookies(binding,origin,listOf("session=old; Path=/; Secure")); s.observeCookies(binding,origin,listOf("session=new; Path=/; Secure")); assertEquals("session=new",s.submit(binding,credential())!!.header("Cookie")) }
    @Test fun unrelatedCookiePathIsNotSent() { val s=ready(); s.observeCookies(binding,origin,listOf("session=abc; Path=/other; Secure")); assertNull(s.submit(binding,credential())!!.header("Cookie")) }
    @Test fun expiredCookieIsNotSent() { val s=ready(); s.observeCookies(binding,origin,listOf("session=abc; Path=/; Secure")); s.observeCookies(binding,origin,listOf("session=; Path=/; Max-Age=0; Secure")); assertNull(s.submit(binding,credential())!!.header("Cookie")) }
    @Test fun passwordCannotBeReplayed() { val s=started(); assertNull(s.password(binding,"secret")) }
    @Test fun assertionCannotBeReplayed() { val s=ready(); assertNotNull(s.submit(binding,credential())); assertNull(s.submit(binding,credential())); assertEquals(FidoLoginExchange.Phase.SUBMITTED,s.phase) }
    @Test fun accountChangeCancelsAttempt() { val s=ready(); assertNull(s.submit(binding.copy(account="bob"),credential())); assertEquals(FidoLoginExchange.Phase.CANCELLED,s.phase) }
    @Test fun serverChangeCancelsAttempt() { val s=ready(); assertNull(s.submit(binding.copy(server="https://other.example"),credential())) }
    @Test fun generationChangeCancelsAttempt() { val s=ready(); assertNull(s.submit(binding.copy(generation=4),credential())) }
    @Test fun expirationBeforeSubmitIsDefinite() { val s=ready(); now=60000; assertNull(s.submit(binding,credential())); assertEquals(FidoLoginExchange.Phase.EXPIRED,s.phase) }
    @Test fun expirationAfterSubmitIsUnknown() { val s=ready(); s.submit(binding,credential()); now=60000; assertEquals(FidoLoginExchange.Phase.UNKNOWN,s.poll(binding)) }
    @Test fun cancellationAfterSubmitIsUnknown() { val s=ready(); s.submit(binding,credential()); s.cancel(); assertEquals(FidoLoginExchange.Phase.UNKNOWN,s.phase) }
    @Test fun cancellationBeforeSubmitIsDefinite() { val s=ready(); s.cancel(); assertNull(s.submit(binding,credential())); assertEquals(FidoLoginExchange.Phase.CANCELLED,s.phase) }
    @Test fun otherOriginPostIsRejected() { val s=started(); assertNull(s.challenge(binding,origin,"https://other.example/login","state",options())); assertEquals(FidoLoginExchange.Phase.INVALID,s.phase) }
    @Test fun otherOriginCookiesAreRejected() { val s=started(); assertFalse(s.observeCookies(binding,"https://other.example",listOf("session=abc"))) }
    @Test fun emptyStateIsRejected() { val s=started(); assertNull(s.challenge(binding,origin,origin,"",options())) }
}
