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
        fun start(selection:ServerEventsQuery=legacyServerEventsQuery) {reader.load(selection);listener.onOpen(fake,Response.Builder().request(fake.request()).protocol(Protocol.HTTP_1_1).code(101).message("Switching").build());listener.onMessage(fake,"""{"action":"userinfo","userinfo":{"_id":"user/a/me","siteadmin":0,"links":{}}}""")}
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
    @Test fun oversizedFrameIsNotReportedAsInvalidHistory() {
        val h=Harness();try {h.start();h.listener.onMessage(h.fake,"x".repeat(2_000_001));assertEquals(ServerEventsIssueKind.FRAME_SIZE,h.reader.state.value.issue?.kind);assertTrue(h.fake.cancelled)} finally {h.reader.close()}
    }
    @Test fun malformedJsonHasItsOwnSafeIssue() {
        val h=Harness();try {h.start();h.listener.onMessage(h.fake,"{\"private-content\":");assertEquals(ServerEventsIssueKind.JSON,h.reader.state.value.issue?.kind);assertFalse(h.reader.state.value.issue!!.safeCode().contains("private-content"))} finally {h.reader.close()}
    }
    @Test fun nonObjectJsonIsAnEnvelopeFailure() {
        val h=Harness();try {h.start();h.listener.onMessage(h.fake,"[]");assertEquals(ServerEventsIssueKind.ENVELOPE,h.reader.state.value.issue?.kind)} finally {h.reader.close()}
    }
    @Test fun malformedUserInfoIsNotReportedAsHistoryFailure() {
        val h=Harness();try {h.start();h.listener.onMessage(h.fake,"{\"action\":\"userinfo\",\"userinfo\":[]}");assertEquals(ServerEventsIssueKind.IDENTITY,h.reader.state.value.issue?.kind)} finally {h.reader.close()}
    }
    @Test fun nullSitePermissionsRemainInvalidNotImplicitlyAuthorized() {
        val h=Harness();try {h.start();h.listener.onMessage(h.fake,"{\"action\":\"userinfo\",\"userinfo\":{\"_id\":\"user/a/me\",\"siteadmin\":null}}");assertEquals(ServerEventsIssueKind.IDENTITY,h.reader.state.value.issue?.kind);assertTrue(h.reader.state.value.records.isEmpty())} finally {h.reader.close()}
    }
    @Test fun selectedFilterAndLimitAreSentAfterIdentityCheck() {
        val h=Harness();try {
            h.start(ServerEventsQuery(ServerEventType.MANUAL,120))
            val p=JsonParser.parseString(h.fake.sent.last()).asJsonObject
            assertEquals("manual",p.get("filter").asString);assertEquals(120,p.get("limit").asInt)
        } finally {h.reader.close()}
    }
    @Test fun selectionChangeRetiresOldSocketReplies() {
        val h=Harness();try {
            h.start(ServerEventsQuery(limit=60));val old=h.listener
            val tag=JsonParser.parseString(h.fake.sent.last()).asJsonObject.get("tag").asString
            val next=ServerEventsQuery(ServerEventType.RELAY,1000);h.start(next)
            old.onMessage(h.fake,"{\"action\":\"events\",\"tag\":\""+tag+"\",\"events\":[{\"msg\":\"retired\"}]}")
            assertEquals(ServerEventsPhase.LOADING,h.reader.state.value.phase)
            h.history();assertEquals(ServerEventsPhase.READY,h.reader.state.value.phase)
            assertEquals(next,h.reader.state.value.selection);assertTrue(h.reader.state.value.records.isEmpty())
        } finally {h.reader.close()}
    }
}
