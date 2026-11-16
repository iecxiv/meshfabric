from pathlib import Path
import hashlib,json,subprocess
root='app/src/main/java/iecxiv/meshfabric/data/remote/'
tests='app/src/test/java/iecxiv/meshfabric/data/remote/'
exchange=root+'FidoLoginExchange.kt'
contract=tests+'FidoLoginExchangeTest.kt'
http=tests+'FidoLoginHttpTransportTest.kt'
expected={exchange:'9bb52a43e27f2a2e0c50c938ada46dddeb537f1d',contract:'53821bd90d440f4d3240f644ce2ef9dc76b2d48b',http:'51f6e088b82301e495465fbd6a1dda72814e71a9'}
sources={}
for n,sha in expected.items():
    old=Path(n).read_bytes()
    assert hashlib.sha1(b'blob '+str(len(old)).encode()+b'\0'+old).hexdigest()==sha,'Source changed: '+n
    sources[n]=old.decode('utf-8')
def edit(n,before,after):
    assert sources[n].count(before)==1,('Anchor changed',n,before)
    sources[n]=sources[n].replace(before,after)
def append_tests(n,text,count):
    old=sources[n].rstrip()
    assert old.endswith('}') and text.count('@Test')==count
    sources[n]=old[:-1]+text+'}\n'
edit(exchange,'.add("action","tokenlogin").add("hwstate",hwstate)','.add("action","tokenlogin").add("hwstate",hwstate).add("token","")')
edit(contract,'assertEquals(setOf("action","hwstate","hwtoken"),f.keys);','assertEquals(setOf("action","hwstate","hwtoken","token"),f.keys); assertEquals("",f["token"]);')
append_tests(contract,r'''
    @Test fun emptyTokenSelectsAuditedSessionLookupCondition() {
        val body=ready().submit(binding,credential())!!.body as FormBody
        val f=fields(body)
        fun restores(input:Map<String,String>)=input["username"]==null && input["password"]==null && input["token"]!=null
        // MeshCentral 1.3.1 webserver.js lines 1379-1383: presence, not OTP validity.
        assertTrue(restores(f)); assertFalse(restores(f-"token")); assertEquals("",f["token"])
        assertFalse(f["hwtoken"].isNullOrBlank()); assertEquals("opaque+state=",f["hwstate"])
    }
    @Test fun emptyTokenIsPresentExactlyOnceWithoutPrimaryCredentials() {
        val body=ready().submit(binding,credential())!!.body as FormBody
        val indexes=(0 until body.size).filter { body.name(it)=="token" }
        assertEquals(1,indexes.size); assertEquals("",body.value(indexes.single()))
        assertFalse((0 until body.size).any { body.name(it) in setOf("username","password","pin","otptoken","vtoken") })
        assertEquals(1,(0 until body.size).count { body.name(it)=="hwtoken" })
    }
''',2)
append_tests(http,r'''
    private fun auditedProtocolRoundTrip(account:String):Pair<Boolean,Map<String,String>> {
        val submitted=java.util.concurrent.atomic.AtomicReference<Map<String,String>>(emptyMap())
        fun decode(body:String):Map<String,String> = body.split('&').associate { field ->
            val p=field.split('=',limit=2)
            java.net.URLDecoder.decode(p[0],"UTF-8") to java.net.URLDecoder.decode(p.getOrElse(1){""},"UTF-8")
        }
        // Protocol fixture only: not a cryptographic authenticator or live MeshCentral server.
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:okhttp3.mockwebserver.RecordedRequest):MockResponse {
                if(request.method=="POST" && request.path=="/tenant/login") {
                    return MockResponse().setBody(html()).addHeader("Set-Cookie","session=challenge; Path=/tenant; Secure; HttpOnly")
                }
                if(request.method=="POST" && request.path=="/tenant/") {
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
''',2)
assert sources[contract].count('@Test')==20 and sources[http].count('@Test')==11
for n,text in sources.items(): Path(n).write_text(text,encoding='utf-8')
subprocess.run(['git','add','--',*sorted(sources)],check=True)
subprocess.run(['git','diff','--cached','--check'],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==set(expected)
out=Path('validation'); out.mkdir(exist_ok=True)
(out/'token-fix.patch').write_bytes(subprocess.check_output(['git','diff','--cached','--binary']))
(out/'source-digests.json').write_text(json.dumps({n:hashlib.sha256(Path(n).read_bytes()).hexdigest() for n in sorted(sources)},indent=2)+'\n')
(out/'preparer-sha256.txt').write_text(hashlib.sha256(Path(__file__).read_bytes()).hexdigest()+'\n')
