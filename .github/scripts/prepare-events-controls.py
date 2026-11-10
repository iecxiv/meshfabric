NEW_FILES={
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventsQuery.kt':r"""package iecxiv.meshfabric.ui.management
internal const val SERVER_EVENTS_SAFETY_LIMIT=10000
internal val serverEventLimitChoices=listOf(60,120,250,500,1000,0)
internal enum class ServerEventType(val wire:String) {
    ALL(""), AGENT("agentlog"), RELAY("relaylog"), MANUAL("manual"), COMMAND("runcommands"), UPLOAD("batchupload"), CHANGE("changenode"), REMOVE("removenode");
    companion object { fun fromAction(action:String)=entries.firstOrNull {it.wire==action} ?: ALL }
}
internal data class ServerEventsQuery(val type:ServerEventType=ServerEventType.ALL,val limit:Int?=60) {
    init {require(limit==null || limit in listOf(60,120,200,250,500,1000))}
}
internal val legacyServerEventsQuery=ServerEventsQuery(limit=SERVER_EVENTS_LIMIT)
""",
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventsControls.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
@Composable internal fun ServerEventsControls(selection:ServerEventsQuery,enabled:Boolean,onSelection:(ServerEventsQuery)->Unit) {
    var typesOpen by remember {mutableStateOf(false)}
    var limitsOpen by remember {mutableStateOf(false)}
    val labels=stringArrayResource(R.array.log_event_types)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick={typesOpen=true},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("log-event-type")) {Text(stringResource(R.string.log_event_filter)+": "+labels[selection.type.ordinal])}
            DropdownMenu(expanded=typesOpen,onDismissRequest={typesOpen=false}) {
                ServerEventType.entries.forEach {type->DropdownMenuItem(text={Text(labels[type.ordinal])},modifier=Modifier.testTag("log-event-type-"+type.wire.ifBlank {"all"}),onClick={typesOpen=false;onSelection(selection.copy(type=type))})}
            }
        }
        Box(Modifier.weight(1f)) {
            val label=selection.limit?.let {stringResource(R.string.log_event_latest,it)} ?: stringResource(R.string.log_event_unlimited)
            OutlinedButton(onClick={limitsOpen=true},enabled=enabled,modifier=Modifier.fillMaxWidth().testTag("log-event-count")) {Text(stringResource(R.string.log_event_show)+": "+label)}
            DropdownMenu(expanded=limitsOpen,onDismissRequest={limitsOpen=false}) {
                serverEventLimitChoices.forEach {value->DropdownMenuItem(text={Text(if(value==0) stringResource(R.string.log_event_unlimited) else stringResource(R.string.log_event_latest,value))},modifier=Modifier.testTag("log-event-limit-$value"),onClick={limitsOpen=false;onSelection(selection.copy(limit=value.takeUnless {it==0}))})}
            }
        }
    }
    if(selection.limit==null) Text(stringResource(R.string.log_event_safety),style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun ServerEventIcon(action:String) {
    val type=ServerEventType.fromAction(action)
    val icon=when(type) {
        ServerEventType.ALL->Icons.Default.History
        ServerEventType.AGENT->Icons.Default.Computer
        ServerEventType.RELAY->Icons.Default.SwapHoriz
        ServerEventType.MANUAL->Icons.Default.Description
        ServerEventType.COMMAND->Icons.Default.Code
        ServerEventType.UPLOAD->Icons.Default.CloudUpload
        ServerEventType.CHANGE->Icons.Default.Edit
        ServerEventType.REMOVE->Icons.Default.Delete
    }
    val description=if(type==ServerEventType.ALL) stringResource(R.string.log_event) else stringArrayResource(R.array.log_event_types)[type.ordinal]
    Icon(icon,description,Modifier.size(20.dp).testTag("log-event-icon-"+type.wire.ifBlank {"other"}),tint=MaterialTheme.colorScheme.primary)
}
""",
'app/src/test/java/iecxiv/meshfabric/ui/management/ServerEventsQueryTest.kt':r"""package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ServerEventsQueryTest {
    private val scope=ServerEventsScope("server","actor",1,"rights")
    private fun packet(count:Int,tag:String="test")=JsonParser.parseString("{\"action\":\"events\",\"tag\":\""+tag+"\",\"events\":["+List(count) {"{\"msg\":123}"}.joinToString(",")+"]}").asJsonObject
    private fun decode(query:ServerEventsQuery,count:Int):ServerEventsSnapshot {
        val gate=ServerEventsReadGate();val attempt=gate.begin(scope,"test",query)
        assertTrue(gate.receive(attempt,scope,packet(count)));return gate.state
    }
    @Test fun visibleChoicesMatchRequestedCounts() {
        assertEquals(listOf(60,120,250,500,1000,0),serverEventLimitChoices)
        for(count in serverEventLimitChoices.filter {it>0}) assertEquals(count,serverEventsRequest("test",ServerEventsQuery(limit=count)).get("limit").asInt)
    }
    @Test fun everyTypeUsesOnlyItsProtocolFilter() {
        for(type in ServerEventType.entries) {
            val p=serverEventsRequest("test",ServerEventsQuery(type,120))
            if(type==ServerEventType.ALL) assertFalse(p.has("filter")) else assertEquals(type.wire,p.get("filter").asString)
            assertEquals("test",p.get("tag").asString)
        }
    }
    @Test fun unlimitedOmitsLimitButKeepsFilter() {
        val p=serverEventsRequest("test",ServerEventsQuery(ServerEventType.RELAY,null))
        assertFalse(p.has("limit"));assertEquals("relaylog",p.get("filter").asString)
    }
    @Test fun selectedFiniteBoundRejectsAnOversizedBatch() {
        val state=decode(ServerEventsQuery(limit=60),61)
        assertEquals(ServerEventsPhase.INVALID,state.phase);assertTrue(state.records.isEmpty())
    }
    @Test fun largerFiniteSelectionAcceptsMoreThanLegacyTwoHundred() {
        val state=decode(ServerEventsQuery(limit=1000),500)
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals(500,state.records.size)
    }
    @Test fun unlimitedAcceptsMoreThanOneThousandWithoutTruncation() {
        val state=decode(ServerEventsQuery(limit=null),1001)
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals(1001,state.records.size)
    }
    @Test fun unlimitedSafetyBoundRejectsRatherThanTruncates() {
        val state=decode(ServerEventsQuery(limit=null),SERVER_EVENTS_SAFETY_LIMIT+1)
        assertEquals(ServerEventsPhase.INVALID,state.phase);assertTrue(state.records.isEmpty())
        assertEquals(ServerEventsIssueKind.HISTORY_SHAPE,state.issue?.kind)
    }
    @Test fun changedSelectionRejectsPreviousAttemptAndTag() {
        val gate=ServerEventsReadGate();val first=gate.begin(scope,"old",ServerEventsQuery(limit=60))
        val next=gate.begin(scope,"new",ServerEventsQuery(ServerEventType.MANUAL,1000))
        assertFalse(gate.receive(first,scope,packet(1,"old")))
        assertFalse(gate.receive(next,scope,packet(1,"old")))
        assertTrue(gate.receive(next,scope,packet(1,"new")));assertEquals(ServerEventsPhase.READY,gate.state.phase)
    }
}
""",
'app/src/androidTest/java/iecxiv/meshfabric/ui/management/ServerEventsControlsTest.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class ServerEventsControlsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun typeSelectionPreservesRequestedCount() {
        val selected=mutableStateOf(ServerEventsQuery(limit=120))
        compose.setContent {MaterialTheme {ServerEventsControls(selected.value,true,{selected.value=it})}}
        compose.onNodeWithTag("log-event-type").performClick()
        compose.onNodeWithTag("log-event-type-manual").performClick()
        compose.runOnIdle {assertEquals(ServerEventType.MANUAL,selected.value.type);assertEquals(120,selected.value.limit)}
    }
    @Test fun unlimitedSelectionPreservesTypeAndHasNoRequestLimit() {
        val selected=mutableStateOf(ServerEventsQuery(ServerEventType.AGENT,60))
        compose.setContent {MaterialTheme {ServerEventsControls(selected.value,true,{selected.value=it})}}
        compose.onNodeWithTag("log-event-count").performClick()
        compose.onNodeWithTag("log-event-limit-0").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(ServerEventType.AGENT,selected.value.type);assertNull(selected.value.limit);assertFalse(serverEventsRequest("test",selected.value).has("limit"))}
    }
    @Test fun knownAndUnknownEventsHaveIconsWithoutDroppingRows() {
        val state=ServerEventsSnapshot(ServerEventsPhase.READY,listOf(ServerEventRecord("relaylog","relay entry","","","",null),ServerEventRecord("future-type","unknown entry","","","",null)))
        compose.setContent {MaterialTheme {ServerEventsPanel(state,"",{},true,{})}}
        compose.onNodeWithTag("log-event-icon-relaylog").assertIsDisplayed()
        compose.onNodeWithTag("log-event-icon-other").assertIsDisplayed()
        compose.onNodeWithText("relay entry").assertIsDisplayed();compose.onNodeWithText("unknown entry").assertIsDisplayed()
    }
}
"""
}
READER_TESTS=r"""    @Test fun selectedFilterAndLimitAreSentAfterIdentityCheck() {
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
"""
from pathlib import Path
import json,subprocess,xml.etree.ElementTree as ET
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management')
changed=set()
def edit(path,old,new):
    p=Path(path);text=p.read_text();assert text.count(old)==1,(str(p),old);p.write_text(text.replace(old,new,1));changed.add(str(p))
policy=root/'ServerEventsReadPolicy.kt'
reader=root/'LogEventsReader.kt'
screen=root/'LogScreen.kt'
for p,sha in [(policy,'9e6747c5e1a24b3a897f608a803325f2a9475e53'),(reader,'88a7487ff37b2458412af964b89ad9b0bafffed3'),(screen,'3ec0da2eb5d33e415769170ab3d718eaa5dc8232')]:
    assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()==sha
edit(policy,'val issue:ServerEventsIssue?=null)','val issue:ServerEventsIssue?=null,val selection:ServerEventsQuery?=null)')
edit(policy,'serverEventsRequest(tag:String):JsonObject','serverEventsRequest(tag:String,selection:ServerEventsQuery=legacyServerEventsQuery):JsonObject')
edit(policy,'addProperty("limit",SERVER_EVENTS_LIMIT)','if(selection.limit!=null) addProperty("limit",selection.limit);if(selection.type!=ServerEventType.ALL) addProperty("filter",selection.type.wire)')
edit(policy,'private var tag=""','private var tag=""\n    private var selected=legacyServerEventsQuery')
edit(policy,'begin(scope:ServerEventsScope,requestTag:String):Long','begin(scope:ServerEventsScope,requestTag:String,selection:ServerEventsQuery=legacyServerEventsQuery):Long')
edit(policy,'serverEventsRequest(requestTag)','serverEventsRequest(requestTag,selection);selected=selection')
edit(policy,'value.asJsonArray.size()>SERVER_EVENTS_LIMIT','value.asJsonArray.size()>(selected.limit ?: SERVER_EVENTS_SAFETY_LIMIT)')
edit(reader,'private var queryScope:ServerEventsScope?=null','private var queryScope:ServerEventsScope?=null\n    private var requested=legacyServerEventsQuery')
edit(reader,'@Synchronized fun load() {','@Synchronized fun load(selection:ServerEventsQuery=legacyServerEventsQuery) {\n        requested=selection')
edit(reader,'gate.begin(scope,tag)','gate.begin(scope,tag,requested)')
edit(reader,'serverEventsRequest(tag).toString()','serverEventsRequest(tag,requested).toString()')
edit(reader,'mutable.value=gate.state','mutable.value=gate.state.copy(selection=requested)')
text=reader.read_text();text=text.replace('ServerEventsSnapshot(ServerEventsPhase.INVALIDATED)','ServerEventsSnapshot(ServerEventsPhase.INVALIDATED,selection=requested)').replace('ServerEventsSnapshot(ServerEventsPhase.LOADING)','ServerEventsSnapshot(ServerEventsPhase.LOADING,selection=requested)').replace('ServerEventsSnapshot(ServerEventsPhase.INVALID,issue=issue)','ServerEventsSnapshot(ServerEventsPhase.INVALID,issue=issue,selection=requested)').replace('ServerEventsSnapshot(phase)','ServerEventsSnapshot(phase,selection=requested)');reader.write_text(text)
edit(reader,'@Synchronized fun pause()=stop(ServerEventsPhase.INVALIDATED)','@Synchronized fun pause(selection:ServerEventsQuery?=null) {if(selection==null || selection==requested) stop(ServerEventsPhase.INVALIDATED)}')
edit(screen,'val connected by source.connectionState.collectAsState()','val connected by source.connectionState.collectAsState()\n    var selectedType by rememberSaveable {mutableStateOf(ServerEventType.ALL.name)}\n    var selectedLimit by rememberSaveable {mutableIntStateOf(60)}\n    val selection=ServerEventsQuery(ServerEventType.entries.firstOrNull {it.name==selectedType} ?: ServerEventType.ALL,selectedLimit.takeUnless {it==0})')
edit(screen,'LaunchedEffect(reader,lifecycle) {lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {try {reader.load();awaitCancellation()} finally {reader.pause()}}}','LaunchedEffect(reader,lifecycle,selection) {lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {try {reader.load(selection);awaitCancellation()} finally {reader.pause(selection)}}}')
edit(screen,'ServerEventsPanel(state,query,{query=it},connected && !actor?.id.isNullOrBlank(),reader::load,Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))','val visible=if(state.selection==selection) state else ServerEventsSnapshot(ServerEventsPhase.LOADING,selection=selection)\n        ServerEventsPanel(visible,query,{query=it},connected && !actor?.id.isNullOrBlank(),{reader.load(selection)},Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),selection=selection,onSelection={selectedType=it.type.name;selectedLimit=it.limit ?: 0})')
edit(screen,'modifier:Modifier=Modifier) {\n    val status=','modifier:Modifier=Modifier,selection:ServerEventsQuery=legacyServerEventsQuery,onSelection:((ServerEventsQuery)->Unit)?=null) {\n    val status=')
edit(screen,'Text(stringResource(R.string.log_scope),style=MaterialTheme.typography.bodySmall)','Text(stringResource(R.string.log_scope),style=MaterialTheme.typography.bodySmall)\n        if(onSelection!=null) ServerEventsControls(selection,enabled,onSelection)')
edit(screen,'state.records.size==SERVER_EVENTS_LIMIT','selection.limit!=null && state.records.size==selection.limit')
edit(screen,'Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {','Card(Modifier.fillMaxWidth()) {Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {\n                    ServerEventIcon(row.action)\n                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {')
edit(screen,'                }}\n            }}','                }}}\n            }}')
for name,content in NEW_FILES.items():
    p=Path(name);assert not p.exists();p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content);changed.add(name)
test=Path('app/src/test/java/iecxiv/meshfabric/ui/management/LogEventsReaderTest.kt')
assert subprocess.check_output(['git','hash-object',str(test)],text=True).strip()=='d62d6e799980ae54fccf06f766e300bdd48f8234'
edit(test,'fun start() {reader.load();','fun start(selection:ServerEventsQuery=legacyServerEventsQuery) {reader.load(selection);')
text=test.read_text();assert text.endswith('}\n');test.write_text(text[:-2]+READER_TESTS+'}\n')
labels={
'log_event_filter':('Type','Tipo'),
'log_event_show':('Show','Mostrar'),
'log_event_latest':('Latest %1$d','Últimos %1$d'),
'log_event_unlimited':('Unlimited','Sin límite'),
'log_event_safety':('Unlimited sends no count limit to the server. For safety, the app rejects responses exceeding 10000 records or 2000000 characters; it never silently truncates them.','Sin límite no solicita un máximo al servidor. Por seguridad, la app rechaza respuestas de más de 10000 registros o 2000000 caracteres; nunca las recorta silenciosamente.')}
types=[['All events','Agent logs','Relay logs','Manual logs','Run command logs','Batch upload logs','Device changes','Device removals'],['Todos','Registros del agente','Registros de relay','Registros manuales','Ejecución de comandos','Carga por lotes','Cambios de dispositivo','Eliminación de dispositivo']]
for directory,index in [('values',0),('values-en',0),('values-es',1)]:
    p=Path('app/src/main/res')/directory/'log.xml';tree=ET.parse(p);r=tree.getroot()
    scope=r.find("string[@name='log_scope']");assert scope is not None
    scope.text=('Events visible to this account; not a complete server audit log.' if index==0 else 'Eventos visibles para esta cuenta; no es una auditoría completa del servidor.')
    for key,pair in labels.items():
        assert r.find("string[@name='"+key+"']") is None
        ET.SubElement(r,'string',{'name':key}).text=pair[index]
    assert r.find("string-array[@name='log_event_types']") is None
    array=ET.SubElement(r,'string-array',{'name':'log_event_types'})
    for value in types[index]: ET.SubElement(array,'item').text=value
    ET.indent(tree,space='    ');tree.write(p,encoding='utf-8',xml_declaration=True);p.write_text(p.read_text()+'\n');changed.add(str(p))
doc=Path('docs/log.md')
doc.write_text('# Registro / Log\n\nEvents queries the server with a unique tag, selected event-action filter and selected count. UI choices are 60 (default), 120, 250, 500, 1000 and Unlimited. Unlimited omits limit in the server request; the app rejects rather than truncates responses over 10000 records or 2000000 characters. Text fields remain bounded to 4096 characters. Legacy helper calls retain 200 for compatibility, not as a UI ceiling.\n\nChanging selection starts a new bound request and retires prior replies. Search filters received records locally. Account/server changes, pause and observed access changes clear data. Server authorization remains authoritative; the history is not a complete audit log. Numeric msg values retain their literal representation. Unknown event types remain visible with a generic icon.\n\nRead-only history; no event deletion or automatic mutation retries. Decode errors, explicit denial, timeout and empty results remain distinct. Automated checks do not replace live-server compatibility or physical-device visual review. Management-channel placement and previous-login dialogs are outside this change. Full validation is required before final dev integration.\n');changed.add(str(doc))
assert len(changed)==12,len(changed)
subprocess.run(['git','add','--',*sorted(changed)],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==changed
Path('validation/options-expected.json').write_text(json.dumps(sorted(changed),indent=2)+'\n')
