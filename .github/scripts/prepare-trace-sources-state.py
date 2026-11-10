FILES={
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerTraceSourcesPolicy.kt':r"""package iecxiv.meshfabric.ui.management
import com.google.gson.JsonObject
internal sealed class TraceSourcesRead {
    data class Known(val sources:Set<String>):TraceSourcesRead()
    object Missing:TraceSourcesRead()
    object Invalid:TraceSourcesRead()
}
internal fun decodeTraceSources(packet:JsonObject):TraceSourcesRead {
    val action=packet.get("action")
    if(action==null || !action.isJsonPrimitive || !action.asJsonPrimitive.isString || action.asString!="traceinfo" || packet.has("error") || packet.has("err")) return TraceSourcesRead.Invalid
    if(packet.has("result")) {
        val result=packet.get("result")
        if(!result.isJsonPrimitive || !result.asJsonPrimitive.isString || !result.asString.equals("ok",true)) return TraceSourcesRead.Invalid
    }
    val value=packet.get("traceSources") ?: return TraceSourcesRead.Missing
    if(value.isJsonNull) return TraceSourcesRead.Known(emptySet())
    if(!value.isJsonArray || value.asJsonArray.size()>256) return TraceSourcesRead.Invalid
    val sources=linkedSetOf<String>()
    for(item in value.asJsonArray) {
        if(!item.isJsonPrimitive || !item.asJsonPrimitive.isString) return TraceSourcesRead.Invalid
        val name=item.asString
        if(name.isBlank() || name.length>100) return TraceSourcesRead.Invalid
        sources.add(name)
    }
    return TraceSourcesRead.Known(sources)
}
""",
'app/src/test/java/iecxiv/meshfabric/ui/management/TraceSourcesPolicyTest.kt':r"""package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class TraceSourcesPolicyTest {
    private fun decode(fields:String)=decodeTraceSources(JsonParser.parseString("{\"action\":\"traceinfo\""+fields+"}").asJsonObject)
    @Test fun explicitNullIsKnownEmptyState() {assertEquals(TraceSourcesRead.Known(emptySet()),decode(",\"traceSources\":null"))}
    @Test fun emptyArrayIsKnownEmptyState() {assertEquals(TraceSourcesRead.Known(emptySet()),decode(",\"traceSources\":[]"))}
    @Test fun stringArrayPreservesKnownAndUnknownNames() {assertEquals(TraceSourcesRead.Known(setOf("agent","future-source")),decode(",\"traceSources\":[\"agent\",\"future-source\",\"agent\"]"))}
    @Test fun absentFieldIsNotEmptyState() {assertEquals(TraceSourcesRead.Missing,decode(""))}
    @Test fun malformedTypesAreRejectedWithoutPartialAcceptance() {
        for(value in listOf("{}","true","123","\"*\"","[\"agent\",123]","[null]","[\"\"]")) assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":"+value))
    }
    @Test fun excessiveCountAndNameLengthAreRejected() {
        assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":["+List(257) {"\"agent\""}.joinToString(",")+"]"))
        assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":[\""+"x".repeat(101)+"\"]"))
    }
    @Test fun errorReplyCannotBeMistakenForNoActiveSources() {
        assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":null,\"error\":\"denied\""))
        assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":null,\"result\":false"))
    }
    @Test fun unrelatedActionsCannotSupplyCurrentSources() {
        assertEquals(TraceSourcesRead.Invalid,decodeTraceSources(JsonParser.parseString("{\"action\":\"userinfo\",\"traceSources\":null}").asJsonObject))
    }
}
""",
'app/src/androidTest/java/iecxiv/meshfabric/ui/management/TraceSourcesChannelTest.kt':r"""package iecxiv.meshfabric.ui.management
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
""",
'docs/reference/meshcentral-1.3.1-trace-sources.md':r"""# MeshCentral 1.3.1 trace source state compatibility

References inspected: https://unpkg.com/meshcentral@1.3.1/meshcentral.js and https://unpkg.com/meshcentral@1.3.1/meshuser.js .

The server initializes debugRemoteSources to null. For a full server administrator, when domain trace access is allowed, its initial traceinfo packet carries that value. Explicit traceSources:null means a received state with no active sources; it is distinct from an absent field. Arrays must consist entirely of nonblank strings within the existing count/name bounds. Malformed or error replies never become an empty state. Unknown valid source names are preserved.

Incoming traceinfo is accepted only after administrator identity on the current connection. Both direct packets and wrapped traceinfo events share the decoder. No initial or timeout/reconnect traceinfo request is sent: that command changes global sources, rather than performing a read. Only a deliberate source toggle may send a traceinfo write; existing base/desired comparison and server-readback confirmation remain in place.

After 20 seconds without an initial source state in the trace view, show an unavailable-state error and manual reconnect instead of waiting forever. This does not assert that access was denied, and does not disable the independently available console. Invalid state disables the selector until a valid state or manual reconnect. Connection generation and identity guard stale messages. No permissions or domain restrictions are bypassed. No automatic write retries, trace exports or diagnostic payload logging are added.

Synthetic JVM and Android channel tests cover null, empty/populated arrays, malformed/missing/error data, readback, non-admin access, stale sockets and absence of unsolicited trace writes. Real server behavior and physical-device visual review still require verification.
"""
}
from pathlib import Path
import json,subprocess
p=Path('app/src/main/java/iecxiv/meshfabric/ui/management/ServerAdminPanel.kt')
assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()=='17e4489ca151f329b64f24cc60e944173cc0e66f'
changed={str(p)}
for path,content in FILES.items():
    f=Path(path);assert not f.exists(),path;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(content);changed.add(path)
s=p.read_text()
def edit(old,new):
    global s
    assert s.count(old)==1,old;s=s.replace(old,new,1)
edit('import androidx.compose.ui.platform.LocalConfiguration','import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.testTag')
edit('private data class AdminState','internal data class AdminState')
edit('val desired:Set<String> = emptySet(),val notice:String="")','val desired:Set<String> = emptySet(),val notice:String="",val sourcesIssue:String="")')
edit('private data class TraceProposal','internal data class TraceProposal')
edit('private class ServerAdminChannel(private val source:MeshCentralClient) {','internal class ServerAdminChannel(private val source:MeshCentralClient,private val open:(MeshCentralClient,WebSocketListener)->WebSocket={s,l->\n    OkHttpHelper.createClient(s.ignoreSsl).newWebSocket(Request.Builder().url(OkHttpHelper.toWebSocketUrl(s.serverUrl,"/control.ashx?moreargs=1")).header("Cookie",s.authCookie).build(),l)\n}) {')
edit("""socket=OkHttpHelper.createClient(source.ignoreSsl).newWebSocket(Request.Builder()
                .url(OkHttpHelper.toWebSocketUrl(source.serverUrl,"/control.ashx?moreargs=1"))
                .header("Cookie",source.authCookie).build(),object:WebSocketListener() {""",'socket=open(source,object:WebSocketListener() {')
edit('if(epoch!=generation) { webSocket.cancel(); return }','if(epoch!=generation || source.isUserLogout || identity!=currentIdentity()) { webSocket.cancel(); if(epoch==generation) fail(epoch); return }')
edit('if(epoch!=generation) return\n                    if(text.length>2000000)','if(epoch!=generation) return\n                    if(source.isUserLogout || identity!=currentIdentity()) {fail(epoch);return}\n                    if(text.length>2000000)')
edit('if(user.optString("_id").isNotBlank()) state.update { it.copy(ready=true,admin=fullServerAdmin(user.optLong("siteadmin",0)),failed=false) }','if(user.optString("_id").isNotBlank()) {\n                                val allowed=fullServerAdmin(user.optLong("siteadmin",0))\n                                state.update {it.copy(ready=true,admin=allowed,failed=false,sources=if(allowed) it.sources else null,sourcesIssue=if(allowed) it.sourcesIssue else "")}\n                            }')
edit("""        val array=packet.optJSONArray("traceSources") ?: return
        if(array.length()>256) return
        val sources=(0 until array.length()).mapNotNull { array.optString(it).takeIf { name->name.isNotBlank() && name.length<=100 } }.toSet()
        state.update { it.copy(sources=sources,pending=if(it.pending=="trace") "" else it.pending,""","""        if(!state.value.ready || !state.value.admin || source.isUserLogout || identity!=currentIdentity()) return
        val decoded=runCatching {decodeTraceSources(com.google.gson.JsonParser.parseString(packet.toString()).asJsonObject)}.getOrDefault(TraceSourcesRead.Invalid)
        val sources=when(decoded) {
            is TraceSourcesRead.Known->decoded.sources
            TraceSourcesRead.Missing->return
            TraceSourcesRead.Invalid->{state.update {it.copy(sources=null,sourcesIssue="invalid")};return}
        }
        state.update { it.copy(sources=sources,sourcesIssue="",pending=if(it.pending=="trace") "" else it.pending,""")
edit('    @Synchronized fun dismissNotice()','    @Synchronized fun sourcesTimeout() {\n        if(state.value.ready && state.value.admin && state.value.sources==null && state.value.sourcesIssue.isEmpty()) state.update {it.copy(sourcesIssue="timeout")}\n    }\n    @Synchronized fun dismissNotice()')
edit('pending="",sources=null,notice=','pending="",sources=null,sourcesIssue="",notice=')
edit('ServerAdminPanel(source:MeshCentralClient?,traceView:Boolean)','ServerAdminPanel(source:MeshCentralClient?,traceView:Boolean,channelOverride:ServerAdminChannel?=null)')
edit('val channel=remember(source) { source?.let { ServerAdminChannel(it) } }','val channel=remember(source,source?.serverUrl,source?.authCookie,source?.ignoreSsl,channelOverride) { channelOverride ?: source?.let { ServerAdminChannel(it) } }')
edit('    val enabled=state.ready && state.admin && state.pending.isEmpty()','    LaunchedEffect(channel,retry,traceView,state.ready,state.admin,state.sources,state.sourcesIssue) {\n        if(traceView && state.ready && state.admin && state.sources==null && state.sourcesIssue.isEmpty()) {delay(20000);channel?.sourcesTimeout()}\n    }\n    val enabled=state.ready && state.admin && state.pending.isEmpty()')
edit('if(state.failed) TextButton(onClick={ retry++ })','if(state.failed || (traceView && state.sourcesIssue.isNotBlank())) TextButton(onClick={ retry++ },modifier=Modifier.testTag("server-trace-reconnect"))')
edit('enabled=enabled && current!=null,modifier=Modifier.fillMaxWidth())','enabled=enabled && current!=null,modifier=Modifier.fillMaxWidth().testTag("server-trace-sources-select"))')
edit('DropdownMenuItem(enabled=enabled,text={ Column {','DropdownMenuItem(enabled=enabled,modifier=Modifier.testTag("trace-source-$name"),text={ Column {')
edit("""if(current==null) Text(label("Esperando fuentes actuales del servidor. No se modificarán hasta recibirlas.","Waiting for current server sources. No changes allowed until received."),style=MaterialTheme.typography.bodySmall) else {""","""if(current==null) Text(when {
                    state.sourcesIssue=="invalid"->label("El estado de fuentes recibido no es compatible. Reconecta para volver a recibirlo; no se ha aplicado ningún cambio automático.","Received source state is invalid. Reconnect to receive it again; no automatic change was applied.")
                    state.sourcesIssue=="timeout"->label("No se han recibido las fuentes actuales. Comprueba permisos y restricciones de rastreo y reconecta. No se han enviado cambios automáticos de fuentes.","Current sources were not received. Check trace permissions and restrictions, then reconnect. No automatic source changes were sent.")
                    state.ready && !state.admin->label("Fuentes no disponibles: se requiere administrador completo del servidor.","Sources unavailable: full server administrator required.")
                    else->label("Esperando fuentes actuales del servidor. No se modificarán hasta recibirlas.","Waiting for current server sources. No changes allowed until received.")
                },modifier=Modifier.testTag("server-trace-sources-status"),style=MaterialTheme.typography.bodySmall,color=if(state.sourcesIssue.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) else {""")
assert s.count('put("action","traceinfo")')==1
p.write_text(s)
assert len(changed)==5,changed
subprocess.run(['git','add','--',*sorted(changed)],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==changed
Path('validation/trace-sources-expected.json').write_text(json.dumps(sorted(changed),indent=2)+'\n')
