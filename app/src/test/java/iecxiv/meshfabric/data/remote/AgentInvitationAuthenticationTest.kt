package iecxiv.meshfabric.data.remote
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
class AgentInvitationAuthenticationTest {
    private class Clock:InvitationScheduler {val tasks=mutableListOf<Pair<Long,()->Unit>>();override fun later(delay:Long,task:()->Unit) {tasks.add(delay to task)};override fun close() {};fun expire() {tasks.first {it.first==20000L}.second()}}
    private class Socket:WebSocket {var req=Request.Builder().url("https://example.test").build();val sent=mutableListOf<JsonObject>();var reject="";override fun request()=req;override fun queueSize()=0L;override fun send(text:String):Boolean {val p=JsonParser.parseString(text).asJsonObject;sent.add(p);return p.get("action").asString!=reject};override fun send(bytes:ByteString)=true;override fun close(code:Int,reason:String?)=true;override fun cancel() {}}
    private class Fixture(token:String="synthetic-token") {
        val socket=Socket();val clock=Clock();val results=mutableListOf<InvitationDelivery>();lateinit var listener:WebSocketListener;var opens=0;var current=true
        val factory=object:WebSocket.Factory {override fun newWebSocket(request:Request,listener:WebSocketListener):WebSocket {opens++;socket.req=request;this@Fixture.listener=listener;return socket}}
        val channel=AgentInvitationChannel(InvitationBinding("https://example.test/domain","user//actor","cookie=synthetic",false),factory,{current},{results.add(it)},clock,token)
        fun start()=channel.start(AgentInvitationContract.groupLink("mesh//group",1,0,0,"old"))
        fun open() {listener.onOpen(socket,Response.Builder().request(socket.req).protocol(Protocol.HTTP_1_1).code(101).message("Switching").build())}
        fun login(id:String="user//actor") {listener.onMessage(socket,JsonObject().apply {addProperty("action","userinfo");add("userinfo",JsonObject().apply {addProperty("_id",id)})}.toString())}
        fun actions()=socket.sent.map {it.get("action").asString}
    }
    @Test fun authenticationPrecedesUserInfo() {val f=Fixture();assertTrue(f.start());f.open();assertEquals(listOf("urlargs","userinfo"),f.actions());assertEquals("synthetic-token",f.socket.sent.first().getAsJsonObject("args").get("auth").asString);assertEquals("INVITE_WAITING_USERINFO",f.channel.diagnosticCode);f.channel.close()}
    @Test fun invitationWaitsForAuthenticatedIdentityAndSendsOnce() {val f=Fixture();f.start();f.login();assertTrue(f.actions().isEmpty());f.open();assertFalse("createInviteLink" in f.actions());f.login();f.login();assertEquals(listOf("urlargs","userinfo","createInviteLink"),f.actions());f.channel.close()}
    @Test fun failedAuthenticationSendNeverSendsInvitation() {val f=Fixture();f.socket.reject="urlargs";f.start();f.open();f.login();assertEquals(listOf("urlargs"),f.actions());assertEquals(listOf(InvitationDelivery.NotSent),f.results);assertEquals("INVITE_AUTH_SEND_FAILED",f.channel.diagnosticCode)}
    @Test fun failedUserInfoSendNeverSendsInvitation() {val f=Fixture();f.socket.reject="userinfo";f.start();f.open();f.login();assertEquals(listOf("urlargs","userinfo"),f.actions());assertEquals(listOf(InvitationDelivery.NotSent),f.results);assertEquals("INVITE_USERINFO_SEND_FAILED",f.channel.diagnosticCode)}
    @Test fun wrongIdentityNeverSendsInvitation() {val f=Fixture();f.start();f.open();f.login("user//other");assertFalse("createInviteLink" in f.actions());assertEquals(listOf(InvitationDelivery.ContextChanged),f.results)}
    @Test fun identityTimeoutDoesNotReplay() {val f=Fixture();f.start();f.open();f.clock.expire();f.login();assertEquals(listOf("urlargs","userinfo"),f.actions());assertEquals(listOf(InvitationDelivery.NotSent),f.results);assertEquals("INVITE_TIMEOUT_USERINFO",f.channel.diagnosticCode);assertEquals(1,f.opens)}
    @Test fun replyTimeoutDoesNotReplay() {val f=Fixture();f.start();f.open();f.login();f.clock.expire();f.login();assertEquals(1,f.actions().count {it=="createInviteLink"});assertEquals(listOf(InvitationDelivery.Unconfirmed),f.results);assertEquals("INVITE_TIMEOUT_LINK_REPLY",f.channel.diagnosticCode);assertEquals(1,f.opens)}
    @Test fun missingTokenNeverOpensSocket() {val f=Fixture("");assertFalse(f.start());assertEquals(0,f.opens);assertEquals("INVITE_AUTH_MISSING",f.channel.diagnosticCode);f.channel.close()}
    @Test fun changedSessionBeforeOpenSendsNoCredentials() {val f=Fixture();f.start();f.current=false;f.open();assertTrue(f.actions().isEmpty());assertEquals(listOf(InvitationDelivery.ContextChanged),f.results)}
}
