package iecxiv.meshfabric.ui.login

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.remote.FidoWebOriginPolicy
import org.junit.Assert.*
import org.junit.Test

class FidoRegistrationPageTest {
    private val origin = "https://server.example"
    private val id = "ceremony_0123456789"
    private fun page() = FidoRegistrationPage(FidoWebOriginPolicy("$origin/tenant"), JsonObject(), id)
    private fun packet(ceremony: String = id) = """{"ceremonyId":"$ceremony","credential":{"rawId":"AQID"}}"""
    private fun deliver(p: FidoRegistrationPage, message: String = packet()) = p.receive(origin, true, "$origin/tenant", message)
    @Test fun cannotDeliverBeforeBegin() { assertNull(deliver(page())) }
    @Test fun validMainFrameCanDeliver() {
        val p=page(); p.begin()
        val result=deliver(p) as FidoRegistrationPage.Delivery.Credential
        assertEquals("AQID",result.value.get("rawId").asString)
    }
    @Test fun otherOriginCannotDeliver() { val p=page(); p.begin(); assertNull(p.receive("https://other.example",true,origin,packet())) }
    @Test fun otherPortCannotDeliver() { val p=page(); p.begin(); assertNull(p.receive("$origin:444",true,origin,packet())) }
    @Test fun subframeCannotDeliver() { val p=page(); p.begin(); assertNull(p.receive(origin,false,origin,packet())) }
    @Test fun changedDocumentOriginCannotDeliver() { val p=page(); p.begin(); assertNull(p.receive(origin,true,"https://other.example",packet())) }
    @Test fun wrongCeremonyCannotDeliver() { val p=page(); p.begin(); assertNull(deliver(p,packet("another_ceremony_123"))); assertNotNull(deliver(p)) }
    @Test fun duplicateCannotDeliver() { val p=page(); p.begin(); assertNotNull(deliver(p)); assertNull(deliver(p)) }
    @Test fun closedPageCannotDeliver() { val p=page(); p.begin(); p.invalidate(); assertNull(deliver(p)) }
    @Test fun failureIsTerminal() { val p=page(); p.begin(); assertSame(FidoRegistrationPage.Delivery.Failed,deliver(p,"""{"ceremonyId":"$id","error":true}""")); assertNull(deliver(p)) }
    @Test fun malformedPacketCannotConsumeCeremony() { val p=page(); p.begin(); assertNull(deliver(p,"{")); assertNotNull(deliver(p)) }
    @Test fun oversizedPacketCannotDeliver() { val p=page(); p.begin(); assertNull(deliver(p,"x".repeat(1_000_001))) }
    @Test fun ceremonyIsStartedOnlyOnce() { val p=page(); assertNotNull(p.begin()); assertNull(p.begin()) }
    @Test fun errorAndCredentialTogetherAreRejected() { val p=page(); p.begin(); assertNull(deliver(p,"""{"ceremonyId":"$id","error":true,"credential":{}}""")) }
}
