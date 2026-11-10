from pathlib import Path
import subprocess
assert subprocess.check_output(['git','hash-object','app/src/main/java/iecxiv/meshfabric/ui/management/LogEventsReader.kt'],text=True).strip()=='e04728fc6a458b7ec18313c60947e648396df643'
assert subprocess.check_output(['git','hash-object','app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventsReadPolicy.kt'],text=True).strip()=='bcce277576a2e0844dbe13cec2f84fc18d762ecc'
assert subprocess.check_output(['git','hash-object','app/src/main/java/iecxiv/meshfabric/ui/management/LogScreen.kt'],text=True).strip()=='42fbc5369bcfdda6e5417be0d6bd1946fbd20351'
NEW_FILES={
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventsDecodeIssue.kt':r"""package iecxiv.meshfabric.ui.management
import com.google.gson.JsonElement
internal enum class ServerEventsIssueKind { FRAME_SIZE, JSON, ENVELOPE, IDENTITY, HISTORY_SHAPE, RECORD_SHAPE, FIELD_TYPE, FIELD_LENGTH, INTERNAL }
internal enum class ServerEventsField(val wire:String) { ACTION("action"), TAG("tag"), MSG("msg"), USERNAME("username"), NODEID("nodeid"), NODENAME("nodename"), RESULT("result"), ERROR("error"), ERR("err"), EVENTS("events"), USERINFO("userinfo"), SITEADMIN("siteadmin"), UNKNOWN("") }
internal enum class ServerEventsValueType { MISSING, NULL, OBJECT, ARRAY, STRING, NUMBER, BOOLEAN }
internal data class ServerEventsIssue(val kind:ServerEventsIssueKind,val field:ServerEventsField=ServerEventsField.UNKNOWN,val type:ServerEventsValueType=ServerEventsValueType.MISSING,val length:Int?=null) {
    fun safeCode()=listOfNotNull(kind.name,field.takeUnless {it==ServerEventsField.UNKNOWN}?.name,type.name,length?.let {"length=$it"}).joinToString(";")
}
internal class ServerEventsDecodeException(val issue:ServerEventsIssue):IllegalArgumentException(issue.safeCode())
internal fun eventValueType(value:JsonElement?):ServerEventsValueType=when {
    value==null->ServerEventsValueType.MISSING
    value.isJsonNull->ServerEventsValueType.NULL
    value.isJsonObject->ServerEventsValueType.OBJECT
    value.isJsonArray->ServerEventsValueType.ARRAY
    value.asJsonPrimitive.isString->ServerEventsValueType.STRING
    value.asJsonPrimitive.isNumber->ServerEventsValueType.NUMBER
    else->ServerEventsValueType.BOOLEAN
}
internal fun eventDecodeFailure(kind:ServerEventsIssueKind,key:String="",value:JsonElement?=null,length:Int?=null):Nothing {
    throw ServerEventsDecodeException(ServerEventsIssue(kind,ServerEventsField.entries.firstOrNull {it.wire==key} ?: ServerEventsField.UNKNOWN,eventValueType(value),length))
}
""",
'app/src/test/java/iecxiv/meshfabric/ui/management/ServerEventsDiagnosticsTest.kt':r"""package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ServerEventsDiagnosticsTest {
    private fun receive(payload:String):ServerEventsSnapshot {
        val gate=ServerEventsReadGate();val scope=ServerEventsScope("server","actor",1,"rights")
        val attempt=gate.begin(scope,"test")
        assertTrue(gate.receive(attempt,scope,JsonParser.parseString(payload).asJsonObject))
        return gate.state
    }
    @Test fun incompatibleFieldReportsOnlySafeMetadata() {
        val result=receive("{\"action\":\"events\",\"tag\":\"test\",\"events\":[{\"msg\":{\"secret\":\"do-not-log\"}}]}")
        assertEquals(ServerEventsPhase.INVALID,result.phase)
        assertEquals(ServerEventsIssueKind.FIELD_TYPE,result.issue?.kind)
        assertEquals(ServerEventsField.MSG,result.issue?.field)
        assertEquals(ServerEventsValueType.OBJECT,result.issue?.type)
        assertFalse(result.issue!!.safeCode().contains("do-not-log"))
        assertTrue(result.records.isEmpty())
    }
    @Test fun fieldLengthDoesNotTruncateOrAcceptPartialHistory() {
        val text="x".repeat(4097)
        val result=receive("{\"action\":\"events\",\"tag\":\"test\",\"events\":[{\"msg\":\"ok\"},{\"msg\":\""+text+"\"}]}")
        assertEquals(ServerEventsIssueKind.FIELD_LENGTH,result.issue?.kind)
        assertEquals(4097,result.issue?.length)
        assertTrue(result.records.isEmpty())
    }
    @Test fun oversizedHistoryRetainsTheTwoHundredRecordLimit() {
        val rows=List(201) {"{}"}.joinToString(",")
        val result=receive("{\"action\":\"events\",\"tag\":\"test\",\"events\":["+rows+"]}")
        assertEquals(ServerEventsPhase.INVALID,result.phase)
        assertEquals(ServerEventsIssueKind.HISTORY_SHAPE,result.issue?.kind)
        assertEquals(201,result.issue?.length)
    }
}
""",
'app/src/androidTest/java/iecxiv/meshfabric/ui/management/LogEventsDiagnosticsUiTest.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import org.junit.Rule
import org.junit.Test
class LogEventsDiagnosticsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun oversizedFrameHasSpecificMessage() {
        compose.setContent {MaterialTheme {ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.INVALID,issue=ServerEventsIssue(ServerEventsIssueKind.FRAME_SIZE)),"",{},true,{})}}
        compose.onNodeWithTag("log-events-state").assertTextEquals(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.log_invalid_frame))
        compose.onNodeWithTag("log-events-diagnostic").assertTextContains("FRAME_SIZE",substring=true)
        compose.onNodeWithTag("log-events-empty").assertDoesNotExist()
    }
    @Test fun fieldFailureShowsMetadataWithoutHistoryContents() {
        compose.setContent {MaterialTheme {ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.INVALID,issue=ServerEventsIssue(ServerEventsIssueKind.FIELD_TYPE,ServerEventsField.MSG,ServerEventsValueType.OBJECT)),"",{},true,{})}}
        compose.onNodeWithTag("log-events-diagnostic").assertTextContains("FIELD_TYPE;MSG;OBJECT",substring=true)
        compose.onNodeWithTag("log-events-empty").assertDoesNotExist()
    }
}
"""
}
READER_TESTS=r"""    @Test fun oversizedFrameIsNotReportedAsInvalidHistory() {
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
"""
from pathlib import Path
import json,subprocess,xml.etree.ElementTree as ET
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management')
changed=[]
def edit(path,old,new):
    p=Path(path);text=p.read_text();assert text.count(old)==1,(str(p),old);p.write_text(text.replace(old,new,1));changed.append(str(p))
policy=root/'ServerEventsReadPolicy.kt'
edit(policy,'val records:List<ServerEventRecord> = emptyList())','val records:List<ServerEventRecord> = emptyList(),val issue:ServerEventsIssue?=null)')
edit(policy,'require(value.isJsonNull || (value.isJsonPrimitive && value.asJsonPrimitive.isString))','if(!(value.isJsonNull || (value.isJsonPrimitive && value.asJsonPrimitive.isString))) eventDecodeFailure(ServerEventsIssueKind.FIELD_TYPE,key,value)')
edit(policy,'require(it.length<=4096)','if(it.length>4096) eventDecodeFailure(ServerEventsIssueKind.FIELD_LENGTH,key,value,it.length)')
edit(policy,'val records=runCatching {','val decoded=runCatching {')
edit(policy,'require(value!=null && value.isJsonArray && value.asJsonArray.size()<=SERVER_EVENTS_LIMIT)','if(value==null || !value.isJsonArray || value.asJsonArray.size()>SERVER_EVENTS_LIMIT) eventDecodeFailure(ServerEventsIssueKind.HISTORY_SHAPE,"events",value,if(value?.isJsonArray==true) value.asJsonArray.size() else null)')
edit(policy,'require(element.isJsonObject)','if(!element.isJsonObject) eventDecodeFailure(ServerEventsIssueKind.RECORD_SHAPE,value=element)')
edit(policy,'}.getOrNull()\n        state=if(records==null) ServerEventsSnapshot(ServerEventsPhase.INVALID) else ServerEventsSnapshot(ServerEventsPhase.READY,records)','}\n        val issue=decoded.exceptionOrNull()?.let {(it as? ServerEventsDecodeException)?.issue ?: ServerEventsIssue(ServerEventsIssueKind.INTERNAL)}\n        state=if(issue!=null) ServerEventsSnapshot(ServerEventsPhase.INVALID,issue=issue) else ServerEventsSnapshot(ServerEventsPhase.READY,decoded.getOrThrow())')
reader=root/'LogEventsReader.kt'
edit(reader,'if(text.length>2_000_000) {stop(ServerEventsPhase.INVALID);return}\n                runCatching {receive(webSocket,JsonParser.parseString(text).asJsonObject)}.onFailure {stop(ServerEventsPhase.INVALID)}','if(text.length>2_000_000) {invalid(ServerEventsIssue(ServerEventsIssueKind.FRAME_SIZE,length=text.length));return}\n                val parsed=try {JsonParser.parseString(text)} catch(_:Exception) {invalid(ServerEventsIssue(ServerEventsIssueKind.JSON));return}\n                if(!parsed.isJsonObject) {invalid(ServerEventsIssue(ServerEventsIssueKind.ENVELOPE,type=eventValueType(parsed)));return}\n                val packet=parsed.asJsonObject\n                try {receive(webSocket,packet)} catch(e:ServerEventsDecodeException) {invalid(e.issue)} catch(_:Exception) {\n                    val identity=packet.get("action")?.let {it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString=="userinfo"}==true\n                    invalid(ServerEventsIssue(if(identity) ServerEventsIssueKind.IDENTITY else ServerEventsIssueKind.INTERNAL))\n                }')
edit(reader,'val user=p.getAsJsonObject("userinfo") ?: error("Missing identity")','val rawUser=p.get("userinfo")\n                if(rawUser==null || !rawUser.isJsonObject) eventDecodeFailure(ServerEventsIssueKind.IDENTITY,"userinfo",rawUser)\n                val user=rawUser.asJsonObject')
edit(reader,'@Synchronized fun timeout()', 'private fun invalid(issue:ServerEventsIssue) {stop(ServerEventsPhase.INVALID);mutable.value=ServerEventsSnapshot(ServerEventsPhase.INVALID,issue=issue)}\n    @Synchronized fun timeout()')
screen=root/'LogScreen.kt'
edit(screen,'ServerEventsPhase.INVALID->R.string.log_invalid','ServerEventsPhase.INVALID->when(state.issue?.kind) {\n            ServerEventsIssueKind.FRAME_SIZE->R.string.log_invalid_frame\n            ServerEventsIssueKind.JSON,ServerEventsIssueKind.ENVELOPE->R.string.log_invalid_json\n            ServerEventsIssueKind.IDENTITY->R.string.log_invalid_identity\n            ServerEventsIssueKind.HISTORY_SHAPE,ServerEventsIssueKind.RECORD_SHAPE,ServerEventsIssueKind.FIELD_TYPE,ServerEventsIssueKind.FIELD_LENGTH->R.string.log_invalid_history\n            ServerEventsIssueKind.INTERNAL->R.string.log_invalid_internal\n            null->R.string.log_invalid\n        }')
edit(screen,'if(state.phase==ServerEventsPhase.LOADING) LinearProgressIndicator', 'if(state.phase==ServerEventsPhase.INVALID && state.issue!=null) Text(stringResource(R.string.log_diagnostic,state.issue.safeCode()),Modifier.testTag("log-events-diagnostic"),style=MaterialTheme.typography.bodySmall)\n        if(state.phase==ServerEventsPhase.LOADING) LinearProgressIndicator')
labels={
'log_invalid_frame':('The received message exceeds the safety size limit.','El mensaje recibido supera el límite de tamaño de seguridad.'),
'log_invalid_json':('The control channel returned an unreadable message.','El canal de control ha devuelto un mensaje que no se puede interpretar.'),
'log_invalid_identity':('The account identity response could not be processed.','No se ha podido procesar la respuesta de identidad de la cuenta.'),
'log_invalid_history':('The event history contains an unsupported field or structure.','El historial de eventos contiene un campo o una estructura no compatible.'),
'log_invalid_internal':('An internal error occurred while processing the control channel.','Se ha producido un error interno al procesar el canal de control.'),
'log_diagnostic':('Technical detail: %1$s','Detalle técnico: %1$s')}
for directory,index in [('values',0),('values-en',0),('values-es',1)]:
    p=Path('app/src/main/res')/directory/'log.xml';text=p.read_text();assert text.count('</resources>')==1
    extra=''.join('    <string name="'+key+'">'+value[index]+'</string>\n' for key,value in labels.items())
    assert all(key not in text for key in labels)
    p.write_text(text.replace('</resources>',extra+'</resources>'));ET.parse(p);changed.append(str(p))
test=Path('app/src/test/java/iecxiv/meshfabric/ui/management/LogEventsReaderTest.kt')
text=test.read_text();assert text.endswith('}\n');test.write_text(text[:-2]+READER_TESTS+'}\n');changed.append(str(test))
for name,content in NEW_FILES.items():
    p=Path(name);assert not p.exists();p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content);changed.append(name)
doc=Path('docs/log.md');doc.write_text(doc.read_text()+'\nDecode diagnostics distinguish frame size, JSON/envelope, account identity, history shape, field type/length and internal processing failures. The technical detail contains only fixed enum codes, JSON type and optional length/count, never payload, account, token or event content. Limits remain 200 records, 4096 characters per text field and 2000000 characters per frame. Invalid batches are not silently truncated or partially accepted. Exact live-server incompatibility remains unverified until the new technical detail is observed.\n');changed.append(str(doc))
changed=sorted(set(changed));assert len(changed)==11
subprocess.run(['git','add','--',*changed],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==set(changed)
Path('validation/fix-expected.json').write_text(json.dumps(changed,indent=2)+'\n')
