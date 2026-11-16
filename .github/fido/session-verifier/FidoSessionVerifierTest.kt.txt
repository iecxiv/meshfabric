package iecxiv.meshfabric.data.remote

import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class FidoSessionVerifierTest {
    private lateinit var server:MockWebServer
    private lateinit var client:OkHttpClient
    private lateinit var binding:FidoLoginBinding
    private lateinit var verifier:FidoSessionVerifier
    private val live=AtomicReference<FidoLoginBinding>()
    @Before fun setup() {
        val cert=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server=MockWebServer(); server.useHttps(serverTls.sslSocketFactory(),false); server.start()
        client=OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager).build()
        binding=FidoLoginBinding(server.url("/tenant").toString(),"alice",1); live.set(binding)
    }
    @After fun teardown() {
        if(::verifier.isInitialized) verifier.close()
        server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
    }
    private fun create(timeout:Long=3000,custom:OkHttpClient=client) { verifier=FidoSessionVerifier(binding,{live.get()},custom,timeout) }
    private fun pending()=FidoLoginHttpTransport.PendingLogin(binding,"session=pending")
    private fun identity(id:String)="{\"action\":\"userinfo\",\"userinfo\":{\"_id\":\"$id\",\"name\":\"Display\"}}"
    private fun upgrade(reply:(WebSocket,String)->Unit):MockResponse=MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
        override fun onMessage(webSocket:WebSocket,text:String) { reply(webSocket,text) }
    })
    private fun answers(id:String)=upgrade { socket,_ -> socket.send(identity(id)) }
    @Test fun matchingAccountVerifiesWithAttemptCookie() {
        server.enqueue(answers("user//alice")); create()
        assertTrue(verifier.verify(pending()))
        val request=server.takeRequest(5,TimeUnit.SECONDS)!!
        assertEquals("/tenant/control.ashx",request.path); assertEquals("session=pending",request.getHeader("Cookie"))
    }
    @Test fun accountMatchIgnoresCaseAndAcceptsDomainQualifiedIds() { server.enqueue(answers("user/tenant/Alice")); create(); assertTrue(verifier.verify(pending())) }
    @Test fun differentAccountIsRejected() { server.enqueue(answers("user//bob")); create(); assertFalse(verifier.verify(pending())) }
    @Test fun lookalikeIdentitiesAreRejected() {
        for(id in listOf("user//alice2","user//malice","mesh//alice","alice","")) {
            server.enqueue(answers(id)); val attempt=FidoSessionVerifier(binding,{live.get()},client,3000)
            assertFalse(id,attempt.verify(pending())); attempt.close()
        }
    }
    @Test fun unrelatedMessagesAreIgnoredUntilIdentityArrives() {
        server.enqueue(upgrade { socket,_ -> socket.send("{\"action\":\"serverinfo\"}"); socket.send("not json"); socket.send(identity("user//alice")) })
        create(); assertTrue(verifier.verify(pending()))
    }
    @Test fun missingIdentityTimesOut() {
        server.enqueue(upgrade { _,_ -> }); create(300)
        val start=System.nanoTime(); assertFalse(verifier.verify(pending()))
        assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(4))
    }
    @Test fun closeBeforeIdentityIsRejected() { server.enqueue(upgrade { socket,_ -> socket.close(1000,"done") }); create(); assertFalse(verifier.verify(pending())) }
    @Test fun plainHttpResponseIsRejected() { server.enqueue(MockResponse().setBody("hello")); create(); assertFalse(verifier.verify(pending())) }
    @Test fun unauthorizedUpgradeIsRejected() { server.enqueue(MockResponse().setResponseCode(401)); create(); assertFalse(verifier.verify(pending())) }
    @Test fun changedBindingPreventsRequest() {
        live.set(binding.copy(account="bob")); server.enqueue(answers("user//alice")); create()
        assertFalse(verifier.verify(pending())); assertEquals(0,server.requestCount)
    }
    @Test fun bindingChangeBeforeIdentityIsRejected() {
        server.enqueue(upgrade { socket,_ -> live.set(binding.copy(account="bob")); socket.send(identity("user//alice")) })
        create(); assertFalse(verifier.verify(pending()))
    }
    @Test fun cancellationDuringVerificationIsRejected() {
        server.enqueue(upgrade { _,_ -> }); create(10_000)
        val result=AtomicReference<Boolean?>(null)
        val thread=Thread { result.set(verifier.verify(pending())) }; thread.start()
        assertNotNull(server.takeRequest(5,TimeUnit.SECONDS)); verifier.close(); thread.join(5000)
        assertFalse(thread.isAlive); assertEquals(false,result.get())
    }
    @Test fun tlsBypassMarkerNeverConnects() {
        val bypass=binding.copy(generation=Long.MIN_VALUE); server.enqueue(answers("user//alice"))
        verifier=FidoSessionVerifier(bypass,{bypass},client,3000)
        assertFalse(verifier.verify(FidoLoginHttpTransport.PendingLogin(bypass,"session=pending"))); assertEquals(0,server.requestCount)
    }
    @Test fun defaultTlsRejectsUntrustedCertificate() {
        server.enqueue(answers("user//alice")); create(3000,OkHttpHelper.createClient(false))
        assertFalse(verifier.verify(pending())); assertEquals(0,server.requestCount)
    }
    @Test fun verifierIsSingleUse() {
        server.enqueue(answers("user//alice")); server.enqueue(answers("user//alice")); create()
        assertTrue(verifier.verify(pending())); assertFalse(verifier.verify(pending())); assertEquals(1,server.requestCount)
    }
    @Test fun cookiesFromAnotherAttemptAreRejected() {
        server.enqueue(answers("user//alice")); create()
        assertFalse(verifier.verify(FidoLoginHttpTransport.PendingLogin(binding.copy(generation=2),"session=other"))); assertEquals(0,server.requestCount)
    }
}
