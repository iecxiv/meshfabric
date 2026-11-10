package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class TraceSourcesChannelTest {
    @get:Rule val compose=createComposeRule()
    private class FakeSocket:WebSocket {
        val sent=mutableListOf<String>()
        override fun request()=Request.Builder().url("https://server.invalid/control.ashx").build()
        override fun queueSize()=0L
        override fun send(text:String):Boolean {sent.add(text);return true}
        override fun send(bytes:ByteString)=false
        override fun close(code:Int,reason:String?)=true
        override fun cancel() {}
    }
    private class Harness:AutoCloseable {
        val client=MeshCentralClient().also {c->
            for((key,value) in mapOf("serverUrl" to "https://server.invalid","authCookie" to "auth=synthetic-test")) c.javaClass.getDeclaredField(key).apply {isAccessible=true}.set(c,value)
        }
        val calls=mutableListOf<Pair<FakeSocket,WebSocketListener>>()
        val channel=ServerAdminChannel(client,open={_,listener->FakeSocket().also {calls.add(it to listener)}})
        fun open() {val (socket,listener)=calls.last();listener.onOpen(socket,Response.Builder().request(socket.request()).protocol(Protocol.HTTP_1_1).code(101).message("test").build())}
        fun reply(text:String,index:Int=calls.lastIndex) {val (socket,listener)=calls[index];listener.onMessage(socket,text)}
        fun identify(admin:Boolean=true)=reply("{\"action\":\"userinfo\",\"userinfo\":{\"_id\":\"user//tester\",\"siteadmin\":"+(if(admin) "4294967295" else "0")+"}}")
        fun writes()=calls.flatMap {it.first.sent}.filter {JSONObject(it).optString("action")=="traceinfo"}
        override fun close() {channel.close();val field=client.javaClass.getDeclaredField("scope").apply {isAccessible=true};(field.get(client) as CoroutineScope).cancel()}
    }
    private fun show(h:Harness) {
        compose.setContent {MaterialTheme {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {ServerAdminPanel(h.client,true,channelOverride=h.channel)}}}
        compose.waitUntil {h.calls.isNotEmpty()};compose.runOnIdle {h.open()}
    }
    @Test fun initialNullEnablesSelectionWithoutWritingGlobalSources() {Harness().use {h->
        show(h);compose.runOnIdle {h.identify();h.reply("{\"action\":\"traceinfo\",\"traceSources\":null}")}
        compose.onNodeWithTag("server-trace-sources-select").assertIsEnabled()
        compose.runOnIdle {assertEquals(emptySet<String>(),h.channel.state.value.sources);assertTrue(h.writes().isEmpty())}
    }}
    @Test fun malformedStateIsNotAcceptedAsAnEmptySelection() {Harness().use {h->
        show(h);compose.runOnIdle {h.identify();h.reply("{\"action\":\"traceinfo\",\"traceSources\":[\"agent\"]}");h.reply("{\"action\":\"traceinfo\",\"traceSources\":[\"agent\",123]}")}
        compose.onNodeWithTag("server-trace-sources-select").assertIsNotEnabled()
        compose.runOnIdle {assertNull(h.channel.state.value.sources);assertEquals("invalid",h.channel.state.value.sourcesIssue);assertTrue(h.writes().isEmpty())}
    }}
    @Test fun missingStateTimesOutAndReconnectDoesNotWriteSources() {Harness().use {h->
        show(h);compose.runOnIdle {h.identify();h.reply("{\"action\":\"traceinfo\"}");h.channel.sourcesTimeout();assertEquals("timeout",h.channel.state.value.sourcesIssue);assertNull(h.channel.state.value.sources);assertTrue(h.channel.state.value.ready)}
        compose.onNodeWithTag("server-trace-sources-select").assertIsNotEnabled();compose.onNodeWithTag("server-trace-reconnect").performScrollTo().performClick()
        compose.waitUntil {h.calls.size>=2};compose.runOnIdle {assertTrue(h.writes().isEmpty())}
    }}
    @Test fun nonAdminCannotUnlockOrChangeSources() {Harness().use {h->
        show(h);compose.runOnIdle {h.identify(false);h.reply("{\"action\":\"traceinfo\",\"traceSources\":null}");assertFalse(h.channel.apply(TraceProposal(emptySet(),setOf("agent"))));assertTrue(h.writes().isEmpty())}
        compose.onNodeWithTag("server-trace-sources-select").assertIsNotEnabled()
    }}
    @Test fun userChangeWritesOnceAndOnlyReadbackConfirmsIt() {Harness().use {h->
        show(h);compose.runOnIdle {h.identify();h.reply("{\"action\":\"traceinfo\",\"traceSources\":null}")}
        compose.onNodeWithTag("server-trace-sources-select").performClick();compose.onNodeWithTag("trace-source-agent").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1,h.writes().size);assertEquals("agent",JSONObject(h.writes().single()).getJSONArray("traceSources").getString(0))
            assertEquals(emptySet<String>(),h.channel.state.value.sources);assertEquals("trace",h.channel.state.value.pending)
            h.reply("{\"action\":\"event\",\"event\":{\"action\":\"traceinfo\",\"traceSources\":[\"agent\"]}}")
            assertEquals(setOf("agent"),h.channel.state.value.sources);assertEquals("confirmed",h.channel.state.value.notice);assertEquals("",h.channel.state.value.pending);assertEquals(1,h.writes().size)
        }
    }}
    @Test fun staleSocketCannotUnlockANewConnection() {Harness().use {h->
        h.channel.connect();h.open();h.channel.close();h.channel.connect();h.open()
        h.reply("{\"action\":\"userinfo\",\"userinfo\":{\"_id\":\"user//tester\",\"siteadmin\":4294967295}}",0)
        h.reply("{\"action\":\"traceinfo\",\"traceSources\":null}",0)
        assertFalse(h.channel.state.value.ready);assertNull(h.channel.state.value.sources);assertTrue(h.writes().isEmpty())
    }}
}
