package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class FidoWebOriginPolicyTest {
    private val policy=FidoWebOriginPolicy("https://server.example:8443/tenant/")
    @Test fun retainsServerPath() { assertEquals("https://server.example:8443/tenant/",policy.entryUrl); assertEquals("https://server.example:8443",policy.origin) }
    @Test fun permitsSameOriginNavigation() { assertTrue(policy.allowsNavigation("https://server.example:8443/tenant/login?mode=2")) }
    @Test fun rejectsDifferentServer() { assertFalse(policy.allowsNavigation("https://other.example:8443/")) }
    @Test fun rejectsDifferentPort() { assertFalse(policy.allowsNavigation("https://server.example/")) }
    @Test fun rejectsHttpAndLocalSchemes() { listOf("http://server.example:8443/","file:///page.html","data:text/html,test","javascript:alert(1)","about:blank").forEach { assertFalse(policy.allowsNavigation(it)) } }
    @Test fun rejectsUserInfo() { assertFalse(policy.allowsNavigation("https://alice:secret@server.example:8443/")) }
    @Test fun rejectsSubframeMessages() { assertFalse(policy.allowsMessage(policy.origin,false)); assertTrue(policy.allowsMessage(policy.origin,true)) }
    @Test fun originCannotContainPathOrQuery() { assertFalse(policy.allowsOrigin("${policy.origin}/tenant")); assertFalse(policy.allowsOrigin("${policy.origin}/?q=1")) }
    @Test(expected=IllegalArgumentException::class) fun ignoredTlsNotAllowed() { FidoWebOriginPolicy("https://server.example/",true) }
    @Test fun supportsIndependentServerDomains() { val other=FidoWebOriginPolicy("https://independent.example/"); assertTrue(other.allowsMessage("https://independent.example",true)); assertFalse(other.allowsMessage(policy.origin,true)) }
}
