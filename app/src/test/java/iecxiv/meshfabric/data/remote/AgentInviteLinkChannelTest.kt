package iecxiv.meshfabric.data.remote
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
class AgentInviteLinkChannelTest {
    private class Clock:InvitationScheduler {val tasks=mutableListOf<Pair<Long,()->Unit>>();override fun later(delay:Long,task:()->Unit) {tasks.add(delay to task)};override fun close() {};fun expire() {tasks.first {it.first==20000L}.second()}}
    private class Socket:WebSocket {var req=Request.Builder().url("https://example.test").build();val sent=mutableListOf<JsonObject>();override fun request()=req;override fun queueSize()=0L;override fun send(text:String):Boolean {sent.add(JsonParser.parseString(text).asJsonObject);return true};override fun send(bytes:ByteString)=true;override fun close(code:Int,reason:String?)=true;override fun cancel() {}}
    private class Fixture {
        val socket=Socket();val clock=Clock();val results=mutableListOf<InvitationDelivery>();lateinit var listener:WebSocketListener;var opens=0
        val factory=object:WebSocket.Factory {override fun newWebSocket(request:Request,listener:WebSocketListener):WebSocket {opens++;socket.req=request;this@Fixture.listener=listener;return socket}}
        val channel=AgentInvitationChannel(InvitationBinding("https://example.test/domain","user//actor","cookie=secret",false),factory,{true},{results.add(it)},clock)
        fun open() {listener.onOpen(socket,Response.Builder().request(socket.req).protocol(Protocol.HTTP_1_1).code(101).message("Switching").build())}
        fun login() {listener.onMessage(socket,"""{"action":"userinfo","userinfo":{"_id":"user//actor"}}""")}
    }
    @Test fun linkWaitsForIdentityAndSendsOnce() {val f=Fixture();assertTrue(f.channel.start(AgentInvitationContract.groupLink("mesh//group",1,0,0,"old")));f.open();assertEquals(1,f.socket.sent.size);f.login();f.login();assertEquals(2,f.socket.sent.size);val p=f.socket.sent.last();assertNotEquals("old",p.get("responseid").asString);val r=p.deepCopy().apply {addProperty("url","https://example.test/agentinvite?c=secret")};f.listener.onMessage(f.socket,r.toString());assertTrue((f.results.single() as InvitationDelivery.Reply).outcome is AgentInvitationOutcome.GroupLink)}
    @Test fun nonLinkOperationsNeverOpenSocket() {val f=Fixture();for(a in listOf("inviteAgent","createDeviceShareLink","deviceShares","removeDeviceShare")) {val p=AgentInvitationContract.groupLink("mesh//group",1,0,0,"r").apply {addProperty("action",a)};assertFalse(f.channel.start(p))};assertEquals(0,f.opens);f.channel.close()}
    @Test fun timeoutDoesNotReplayMutation() {val f=Fixture();f.channel.start(AgentInvitationContract.groupLink("mesh//group",1,0,0,"r"));f.open();f.login();f.clock.expire();f.login();assertEquals(listOf(InvitationDelivery.Unconfirmed),f.results);assertEquals(1,f.socket.sent.count {it.get("action").asString=="createInviteLink"})}
}
