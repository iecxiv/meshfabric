package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import okhttp3.*
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
class LogEventsReaderTest {
    private class Fake:WebSocket {
        val sent=mutableListOf<String>();var cancelled=false
        override fun request()=Request.Builder().url("https://server").build()
        override fun queueSize()=0L
        override fun send(text:String):Boolean {sent.add(text);return true}
        override fun send(bytes:ByteString)=false
        override fun close(code:Int,reason:String?):Boolean {cancelled=true;return true}
        override fun cancel() {cancelled=true}
    }
    private class Harness {
        var binding:LogEventsBinding?=LogEventsBinding("https://server","user/a/me","test-cookie",false,0,"test-token")
        val fake=Fake();lateinit var listener:WebSocketListener
        val reader=LogEventsReader({binding},{_,l->listener=l;fake})
        fun start() {reader.load();listener.onOpen(fake,Response.Builder().request(fake.request()).protocol(Protocol.HTTP_1_1).code(101).message("Switching").build());listener.onMessage(fake,"""{"action":"userinfo","userinfo":{"_id":"user/a/me","siteadmin":0,"links":{}}}""")}
        fun history() {val p=JsonParser.parseString(fake.sent.last()).asJsonObject;listener.onMessage(fake,"{\"action\":\"events\",\"tag\":\""+p.get("tag").asString+"\",\"events\":[] }")}
    }
    @Test fun authenticatedReadUsesBoundedTaggedRequestAndEmptyReply() {
        val h=Harness();try {h.start();val p=JsonParser.parseString(h.fake.sent.last()).asJsonObject
            assertEquals("events",p.get("action").asString);assertEquals(200,p.get("limit").asInt);h.history();assertEquals(ServerEventsPhase.READY,h.reader.state.value.phase)
        } finally {h.reader.close()}
    }
    @Test fun timeoutRetiresChannelAndRejectsLateHistory() {
        val h=Harness();try {h.start();h.reader.timeout();h.history();assertTrue(h.fake.cancelled);assertEquals(ServerEventsPhase.TIMEOUT,h.reader.state.value.phase)} finally {h.reader.close()}
    }
    @Test fun changedAccountCannotAcceptHistory() {
        val h=Harness();try {h.start();h.binding=h.binding?.copy(actor="user/a/other");h.history();assertEquals(ServerEventsPhase.INVALIDATED,h.reader.state.value.phase);assertTrue(h.reader.state.value.records.isEmpty())} finally {h.reader.close()}
    }
    @Test fun accessChangeClearsPreviouslyLoadedData() {
        val h=Harness();try {h.start();h.history();h.listener.onMessage(h.fake,"""{"action":"event","event":{"action":"meshchange"}}""");assertEquals(ServerEventsPhase.INVALIDATED,h.reader.state.value.phase)} finally {h.reader.close()}
    }
}
