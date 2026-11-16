package iecxiv.meshfabric.data.remote

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit

class FidoLoginHttpTransportTest {
    private lateinit var server:MockWebServer
    private lateinit var client:OkHttpClient
    private lateinit var binding:FidoLoginBinding
    private lateinit var live:FidoLoginBinding
    private lateinit var transport:FidoLoginHttpTransport
    private val nonce=Base64.getEncoder().encodeToString(ByteArray(32))
    @Before fun setup() {
        val cert=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server=MockWebServer(); server.useHttps(serverTls.sslSocketFactory(),false); server.start()
        client=OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager).build()
        binding=FidoLoginBinding(server.url("/tenant").toString(),"alice",1); live=binding
        transport=FidoLoginHttpTransport(binding,{live},client)
    }
    @After fun teardown() { transport.close(); server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    private fun html():String {
        val raw="""{"type":"webAuthn","challenge":"$nonce","keyIds":["AQID"],"userVerification":"required"}"""
        val encoded=URLEncoder.encode(raw,"UTF-8").replace("+","%20")
        return """<script>var loginMode='4';var hardwareKeyChallenge=decodeURIComponent('$encoded');</script><form method=post><input type=hidden name=action value=tokenlogin><input type=hidden name=hwstate value="opaque+state="><input name=hwtoken></form>"""
    }
    private fun credential():com.google.gson.JsonObject {
        val origin=server.url("/").toString().removeSuffix("/")
        val data="""{"type":"webauthn.get","origin":"$origin","challenge":"$nonce"}"""
        return JsonParser.parseString("""{"type":"public-key","id":"AQID","rawId":"AQID","response":{"clientDataJSON":"${Base64.getEncoder().encodeToString(data.toByteArray())}","authenticatorData":"${Base64.getEncoder().encodeToString(ByteArray(37))}","signature":"AQID"}}""").asJsonObject
    }
    private fun ready() { server.enqueue(MockResponse().setBody(html()).addHeader("Set-Cookie","session=challenge; Path=/tenant; Secure; HttpOnly")); transport.begin("secret") }
    private fun reject(block:()->Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun preservesCookiesAcrossRedirectsAndSubmitsOnce() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","/tenant/").addHeader("Set-Cookie","session=challenge; Path=/tenant; Secure; HttpOnly"))
        server.enqueue(MockResponse().setBody(html()))
        assertEquals("required",transport.begin("secret").get("userVerification").asString)
        val first=server.takeRequest(5,TimeUnit.SECONDS)!!; assertEquals("POST",first.method); assertEquals("/tenant/login",first.path)
        val second=server.takeRequest(5,TimeUnit.SECONDS)!!; assertEquals("GET",second.method); assertEquals("session=challenge",second.getHeader("Cookie"))
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","/tenant/").addHeader("Set-Cookie","session=pending; Path=/tenant; Secure; HttpOnly"))
        server.enqueue(MockResponse().setBody("server response, not proof"))
        val pending=transport.submit(credential())
        val post=server.takeRequest(5,TimeUnit.SECONDS)!!; assertEquals("POST",post.method); assertEquals("session=challenge",post.getHeader("Cookie"))
        val body=post.body.readUtf8(); assertTrue(body.contains("action=tokenlogin")); assertTrue(body.contains("hwstate=opaque%2Bstate%3D")); assertTrue(body.contains("hwtoken=")); assertFalse(body.contains("password="))
        assertEquals("GET",server.takeRequest(5,TimeUnit.SECONDS)!!.method)
        assertEquals("session=pending",pending.cookie); assertFalse(pending.toString().contains("session=pending"))
        reject { transport.submit(credential()) }; assertEquals(4,server.requestCount)
    }
    @Test fun rejectsCrossOriginRedirect() { server.enqueue(MockResponse().setResponseCode(302).addHeader("Location","https://other.example/login")); reject { transport.begin("secret") }; assertEquals(1,server.requestCount) }
    @Test fun rejectsTlsDowngradeRedirect() { server.enqueue(MockResponse().setResponseCode(302).addHeader("Location",server.url("/").toString().replace("https:","http:"))); reject { transport.begin("secret") }; assertEquals(1,server.requestCount) }
    @Test fun refuses307PasswordReplay() { server.enqueue(MockResponse().setResponseCode(307).addHeader("Location","/tenant/")); reject { transport.begin("secret") }; assertEquals(1,server.requestCount) }
    @Test fun changedBindingPreventsSubmission() { ready(); live=binding.copy(account="bob"); reject { transport.submit(credential()) }; assertEquals(1,server.requestCount) }
    @Test fun cancelledAttemptCannotSubmit() { ready(); transport.close(); reject { transport.submit(credential()) }; assertEquals(1,server.requestCount) }
    @Test fun doesNotRetryDisconnectedPost() { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)); reject { transport.begin("secret") }; assertEquals(1,server.requestCount) }
    @Test fun rejectsOversizedChallengePage() { server.enqueue(MockResponse().setBody("x".repeat(2_097_153))); reject { transport.begin("secret") } }
    @Test fun defaultTlsRejectsUntrustedCertificate() { transport.close(); transport=FidoLoginHttpTransport(binding,{live}); server.enqueue(MockResponse().setBody(html())); reject { transport.begin("secret") }; assertEquals(0,server.requestCount) }

    private fun auditedProtocolRoundTrip(account:String):Pair<Boolean,Map<String,String>> {
        val submitted=java.util.concurrent.atomic.AtomicReference<Map<String,String>>(emptyMap())
        fun decode(body:String):Map<String,String> = body.split('&').associate { field ->
            val p=field.split('=',limit=2)
            java.net.URLDecoder.decode(p[0],"UTF-8") to java.net.URLDecoder.decode(p.getOrElse(1){""},"UTF-8")
        }
        // Protocol fixture only: not a cryptographic authenticator or live MeshCentral server.
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:okhttp3.mockwebserver.RecordedRequest):MockResponse {
                if(request.method=="POST" && request.path=="/tenant/login" && decode(request.body.clone().readUtf8())["action"]=="login") {
                    return MockResponse().setBody(html()).addHeader("Set-Cookie","session=challenge; Path=/tenant; Secure; HttpOnly")
                }
                if(request.method=="POST" && request.path=="/tenant/login") {
                    val f=decode(request.body.readUtf8()); submitted.set(f)
                    val restore=f["username"]==null && f["password"]==null && f["token"]!=null
                    if(!restore || f["token"]!="" || f["action"]!="tokenlogin" || f["hwstate"]!="opaque+state=" || f["hwtoken"].isNullOrEmpty() || request.getHeader("Cookie")!="session=challenge") {
                        return MockResponse().setBody(html())
                    }
                    return MockResponse().setResponseCode(302).addHeader("Location","/tenant/").addHeader("Set-Cookie","session=authenticated; Path=/tenant; Secure; HttpOnly")
                }
                if(request.method=="GET" && request.path=="/tenant/") return MockResponse().setBody("HTTP response alone is not proof")
                if(request.method=="GET" && request.path=="/tenant/control.ashx") {
                    val authenticated=request.getHeader("Cookie")=="session=authenticated"
                    return MockResponse().withWebSocketUpgrade(object:okhttp3.WebSocketListener() {
                        override fun onOpen(socket:okhttp3.WebSocket,response:okhttp3.Response) {
                            if(authenticated) socket.send(com.google.gson.JsonObject().apply {
                                addProperty("action","userinfo")
                                add("userinfo",com.google.gson.JsonObject().apply { addProperty("_id","user//$account") })
                            }.toString()) else socket.close(1000,"noauth")
                        }
                    })
                }
                return MockResponse().setResponseCode(404)
            }
        }
        transport.begin("secret")
        val pending=transport.submit(credential())
        assertEquals("session=authenticated",pending.cookie)
        val verified=FidoSessionVerifier(binding,{live},client,3000).use { it.verify(pending) }
        return verified to submitted.get()
    }
    @Test fun emptyTokenAllowsSessionRestorationAndVerifiedAccount() {
        val (verified,form)=auditedProtocolRoundTrip("alice")
        assertTrue(verified); assertTrue(form.containsKey("token")); assertEquals("",form["token"])
        assertFalse(form.containsKey("password")); assertFalse(form.containsKey("username")); assertEquals(4,server.requestCount)
    }
    @Test fun emptyTokenDoesNotAllowDifferentAccount() {
        val (verified,form)=auditedProtocolRoundTrip("bob")
        assertFalse(verified); assertEquals("",form["token"]); assertFalse(form["hwtoken"].isNullOrBlank())
    }
}
