NEW_FILES={
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventsControls.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
internal fun serverEventImage(type:ServerEventType):ImageVector=when(type) {
    ServerEventType.ALL->Icons.Default.History
    ServerEventType.AGENT->Icons.Default.Computer
    ServerEventType.RELAY->Icons.Default.SwapHoriz
    ServerEventType.MANUAL->Icons.Default.Description
    ServerEventType.COMMAND->Icons.Default.Code
    ServerEventType.UPLOAD->Icons.Default.CloudUpload
    ServerEventType.CHANGE->Icons.Default.Edit
    ServerEventType.REMOVE->Icons.Default.Delete
}
@Composable private fun EventFilterButton(label:String,tag:String,enabled:Boolean,icon:ImageVector?=null,onClick:()->Unit) {
    val scheme=MaterialTheme.colorScheme
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().testTag(tag),shape=MaterialTheme.shapes.medium,
        colors=ButtonDefaults.outlinedButtonColors(containerColor=scheme.surfaceVariant,contentColor=scheme.onSurface),border=BorderStroke(1.dp,scheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            if(icon!=null) Icon(icon,null,Modifier.size(20.dp).align(Alignment.CenterStart))
            Text(label,Modifier.fillMaxWidth().padding(horizontal=24.dp),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown,null,Modifier.size(20.dp).align(Alignment.CenterEnd))
        }
    }
}
@Composable internal fun ServerEventsControls(selection:ServerEventsQuery,enabled:Boolean,onSelection:(ServerEventsQuery)->Unit) {
    var typesOpen by remember {mutableStateOf(false)}
    var limitsOpen by remember {mutableStateOf(false)}
    val labels=stringArrayResource(R.array.log_event_types)
    val scheme=MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        val selectorWidth=minOf((maxWidth-8.dp)/2,280.dp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(selectorWidth)) {
                EventFilterButton(labels[selection.type.ordinal],"log-event-type",enabled,serverEventImage(selection.type)) {typesOpen=true}
                DropdownMenu(expanded=typesOpen,onDismissRequest={typesOpen=false},modifier=Modifier.width(selectorWidth).heightIn(max=280.dp).testTag("log-event-type-menu"),shape=MaterialTheme.shapes.medium,containerColor=scheme.surfaceVariant,tonalElevation=2.dp,border=BorderStroke(1.dp,scheme.outlineVariant)) {
                    ServerEventType.entries.forEach {type->DropdownMenuItem(text={Text(labels[type.ordinal],style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)},leadingIcon={Icon(serverEventImage(type),null,Modifier.size(20.dp).testTag("log-event-type-icon-"+type.wire.ifBlank {"all"}))},modifier=Modifier.testTag("log-event-type-"+type.wire.ifBlank {"all"}),onClick={typesOpen=false;onSelection(selection.copy(type=type))})}
                }
            }
            Box(Modifier.width(selectorWidth)) {
                val label=selection.limit?.let {stringResource(R.string.log_event_latest,it)} ?: stringResource(R.string.log_event_unlimited)
                EventFilterButton(label,"log-event-count",enabled) {limitsOpen=true}
                DropdownMenu(expanded=limitsOpen,onDismissRequest={limitsOpen=false},modifier=Modifier.width(selectorWidth).heightIn(max=280.dp).testTag("log-event-count-menu"),shape=MaterialTheme.shapes.medium,containerColor=scheme.surfaceVariant,tonalElevation=2.dp,border=BorderStroke(1.dp,scheme.outlineVariant)) {
                    serverEventLimitChoices.forEach {value->DropdownMenuItem(text={Text(if(value==0) stringResource(R.string.log_event_unlimited) else stringResource(R.string.log_event_latest,value),Modifier.fillMaxWidth().testTag("log-event-limit-text-$value"),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)},modifier=Modifier.testTag("log-event-limit-$value"),onClick={limitsOpen=false;onSelection(selection.copy(limit=value.takeUnless {it==0}))})}
                }
            }
        }
    }
    if(selection.limit==null) Text(stringResource(R.string.log_event_safety),style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun ServerEventIcon(action:String) {
    val type=ServerEventType.fromAction(action)
    val description=if(type==ServerEventType.ALL) stringResource(R.string.log_event) else stringArrayResource(R.array.log_event_types)[type.ordinal]
    Icon(serverEventImage(type),description,Modifier.size(20.dp).testTag("log-event-icon-"+type.wire.ifBlank {"other"}),tint=MaterialTheme.colorScheme.primary)
}
""",
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventDetails.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.google.gson.JsonObject
import iecxiv.meshfabric.R
import java.text.DateFormat
import java.util.Date
internal fun serverEventDetails(item:JsonObject):Map<String,String> = listOf("msgid","protocol","duration","ip","raddr","bytesin","bytesout").mapNotNull {key->
    item.get(key)?.takeIf {it.isJsonPrimitive}?.asString?.let {key to it}
}.toMap()
@Composable internal fun ServerEventDetailsDialog(row:ServerEventRecord,onDismiss:()->Unit) {
    val missing=stringResource(R.string.log_detail_missing)
    AlertDialog(onDismissRequest=onDismiss,modifier=Modifier.testTag("log-event-details"),properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),
        title={Text(stringResource(R.string.log_detail_title))},
        text={SelectionContainer {Column(Modifier.fillMaxWidth().heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            ServerEventIcon(row.action)
            ManagementInfoRow(stringResource(R.string.log_detail_date),row.time?.let {DateFormat.getDateTimeInstance(DateFormat.FULL,DateFormat.LONG).format(Date(it))} ?: missing)
            ManagementInfoRow(stringResource(R.string.log_detail_type),row.action.ifBlank {missing})
            ManagementInfoRow(stringResource(R.string.log_detail_actor),row.actor.ifBlank {missing})
            ManagementInfoRow(stringResource(R.string.log_detail_device),row.nodeName.ifBlank {missing})
            if(row.nodeId.isNotBlank()) ManagementInfoRow(stringResource(R.string.log_detail_node),row.nodeId)
            Text(stringResource(R.string.log_detail_message),style=MaterialTheme.typography.labelMedium)
            Text(row.message.ifBlank {missing},Modifier.testTag("log-event-details-message"),style=MaterialTheme.typography.bodyMedium)
            row.details.forEach {(key,value)->ManagementInfoRow(key,value)}
        }}},confirmButton={TextButton(onClick=onDismiss,modifier=Modifier.testTag("log-event-details-close")) {Text(stringResource(R.string.log_detail_close))}})
}
""",
'app/src/test/java/iecxiv/meshfabric/ui/management/ServerEventDetailsPolicyTest.kt':r"""package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ServerEventDetailsPolicyTest {
    private fun decode(limit:Int?):ServerEventsSnapshot {
        val gate=ServerEventsReadGate();val scope=ServerEventsScope("server","actor",1,"rights")
        val attempt=gate.begin(scope,"test",ServerEventsQuery(limit=limit))
        gate.receive(attempt,scope,JsonParser.parseString("{\"action\":\"events\",\"tag\":\"test\",\"events\":[{\"msg\":\""+"x".repeat(5000)+"\"}]}").asJsonObject)
        return gate.state
    }
    @Test fun unlimitedMessageHasNoFixedDescriptionLength() {
        val state=decode(null);assertEquals(ServerEventsPhase.READY,state.phase);assertEquals("x".repeat(5000),state.records.single().message)
    }
    @Test fun finiteQueryKeepsItsExistingMessageProtection() {
        val state=decode(60);assertEquals(ServerEventsPhase.INVALID,state.phase);assertEquals(ServerEventsIssueKind.FIELD_LENGTH,state.issue?.kind)
    }
    @Test fun detailsUseOnlyExplicitSafeFields() {
        val details=serverEventDetails(JsonParser.parseString("{\"msgid\":107,\"protocol\":2,\"password\":\"secret\",\"cookie\":\"cookie-secret\",\"token\":\"token-secret\",\"auth\":\"auth-secret\"}").asJsonObject)
        assertEquals(mapOf("msgid" to "107","protocol" to "2"),details);assertFalse(details.toString().contains("secret"))
    }
    @Test fun nestedUnknownPayloadIsNotExposed() {
        assertTrue(serverEventDetails(JsonParser.parseString("{\"ip\":{\"secret\":\"hidden\"},\"unknown\":\"hidden\"}").asJsonObject).isEmpty())
    }
}
""",
'app/src/androidTest/java/iecxiv/meshfabric/ui/management/EventsPresentationTest.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class EventsPresentationTest {
    @get:Rule val compose=createComposeRule()
    private fun state(message:String="entry")=ServerEventsSnapshot(ServerEventsPhase.READY,listOf(ServerEventRecord("relaylog",message,"Operator","node/private","Device",1L)))
    @Test fun menusMatchAnchorWidthsAndCountTextIsCentered() {
        val selection=mutableStateOf(ServerEventsQuery())
        compose.setContent {MaterialTheme {Column {ServerEventsControls(selection.value,true,{selection.value=it})}}}
        compose.onNodeWithTag("log-event-type").performClick()
        val typeWidth=compose.onNodeWithTag("log-event-type").fetchSemanticsNode().boundsInRoot.width
        val typeMenuWidth=compose.onNodeWithTag("log-event-type-menu").fetchSemanticsNode().boundsInRoot.width
        assertEquals(typeWidth,typeMenuWidth,2f)
        compose.onNodeWithTag("log-event-type-icon-manual",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("log-event-type-manual").performClick()
        compose.onNodeWithTag("log-event-count").performClick()
        assertEquals(compose.onNodeWithTag("log-event-count").fetchSemanticsNode().boundsInRoot.width,compose.onNodeWithTag("log-event-count-menu").fetchSemanticsNode().boundsInRoot.width,2f)
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("log-event-limit-text-60",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.isNotEmpty());assertEquals(TextAlign.Center,layouts.first().layoutInput.style.textAlign)
    }
    @Test fun wholeRowOpensReadOnlyDetailsAndCanBeClosed() {
        compose.setContent {MaterialTheme {ServerEventsPanel(state(),"",{},true,{})}}
        compose.onNodeWithTag("log-event-row-0").performClick()
        compose.onNodeWithTag("log-event-details").assertIsDisplayed()
        compose.onNodeWithTag("log-event-details-message").assertTextEquals("entry")
        compose.onNodeWithTag("log-event-details-close").performClick()
        compose.onNodeWithTag("log-event-details").assertDoesNotExist()
    }
    @Test fun invalidationRemovesOpenDetails() {
        val snapshot=mutableStateOf(state())
        compose.setContent {MaterialTheme {ServerEventsPanel(snapshot.value,"",{},true,{})}}
        compose.onNodeWithTag("log-event-row-0").performClick()
        compose.onNodeWithTag("log-event-details").assertIsDisplayed()
        compose.runOnIdle {snapshot.value=ServerEventsSnapshot(ServerEventsPhase.INVALIDATED)}
        compose.onNodeWithTag("log-event-details").assertDoesNotExist()
    }
    @Test fun detailsKeepLongMessageWithoutEllipsis() {
        val message="long ".repeat(1000)+"END"
        compose.setContent {MaterialTheme {ServerEventsPanel(state(message),"",{},true,{})}}
        compose.onNodeWithTag("log-event-row-0").performClick()
        compose.onNodeWithTag("log-event-details-message").assertTextEquals(message)
    }
}
"""
}
READER_TESTS=r"""    @Test fun unlimitedAcceptsTheObserved2489999CharacterFrame() {
        val h=Harness();try {
            h.start(ServerEventsQuery(limit=null))
            val tag=JsonParser.parseString(h.fake.sent.last()).asJsonObject.get("tag").asString
            val prefix="{\"action\":\"events\",\"tag\":\""+tag+"\",\"events\":[{\"msg\":\""
            val suffix="\"}]}"
            val message="x".repeat(2489999-prefix.length-suffix.length)
            val frame=prefix+message+suffix;assertEquals(2489999,frame.length)
            h.listener.onMessage(h.fake,frame)
            assertEquals(ServerEventsPhase.READY,h.reader.state.value.phase)
            assertEquals(message,h.reader.state.value.records.single().message)
        } finally {h.reader.close()}
    }
"""
from pathlib import Path
import json,subprocess,xml.etree.ElementTree as ET
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management');changed=set()
def edit(path,old,new):
    p=Path(path);text=p.read_text();assert text.count(old)==1,(str(p),old);p.write_text(text.replace(old,new,1));changed.add(str(p))
policy=root/'ServerEventsReadPolicy.kt';reader=root/'LogEventsReader.kt';screen=root/'LogScreen.kt';controls=root/'ServerEventsControls.kt'
for p,sha in [(policy,'0c6b9a9600a6cde20d9e65cc18dd43bd1b3d27c1'),(reader,'b36ca6479df320af65d4f1e69c9e015a72ad74c2'),(screen,'33320a3cebac256a11b2d75a2d7e0c259bfc68f9'),(controls,'8b1288fc399d06ac876eadab022efa344be5bef4')]:
    assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()==sha
edit(policy,'val nodeName:String,val time:Long?)','val nodeName:String,val time:Long?,val details:Map<String,String> = emptyMap())')
edit(policy,'eventText(key:String):String','eventText(key:String,unboundedMessage:Boolean=false):String')
edit(policy,'if(it.length>4096)','if(!(key=="msg" && unboundedMessage) && it.length>4096)')
edit(policy,'value.asJsonArray.size()>(selected.limit ?: SERVER_EVENTS_SAFETY_LIMIT)','(selected.limit?.let {value.asJsonArray.size()>it}==true)')
edit(policy,'item.eventText("msg")','item.eventText("msg",selected.limit==null)')
edit(policy,'item.eventText("nodename"),eventTime(item))','item.eventText("nodename"),eventTime(item),serverEventDetails(item))')
edit(root/'ServerEventsQuery.kt','internal const val SERVER_EVENTS_SAFETY_LIMIT=10000\n','')
edit(reader,'if(text.length>2_000_000)','if(requested.limit!=null && text.length>2_000_000)')
edit(screen,'import androidx.compose.foundation.layout.*','import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.clickable')
edit(screen,'    val status=when(state.phase) {','    var detail by remember(state.records,state.phase,selection) {mutableStateOf<ServerEventRecord?>(null)}\n    val status=when(state.phase) {')
edit(screen,'Text(stringResource(R.string.log_scope),style=MaterialTheme.typography.bodySmall)','detail?.let {ServerEventDetailsDialog(it) {detail=null}}\n        Text(stringResource(R.string.log_scope),style=MaterialTheme.typography.bodySmall)')
edit(screen,'itemsIndexed(rows) {_,row->','itemsIndexed(rows) {index,row->')
edit(screen,'Card(Modifier.fillMaxWidth()) {Row','Card(Modifier.fillMaxWidth().testTag("log-event-row-$index").clickable {detail=row}) {Row')
for name,content in NEW_FILES.items():
    p=Path(name)
    if(p!=controls): assert not p.exists(),name
    p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content);changed.add(name)
test=Path('app/src/test/java/iecxiv/meshfabric/ui/management/LogEventsReaderTest.kt')
text=test.read_text();assert text.endswith('}\n');test.write_text(text[:-2]+READER_TESTS+'}\n');changed.add(str(test))
qtest=Path('app/src/test/java/iecxiv/meshfabric/ui/management/ServerEventsQueryTest.kt')
old="""    @Test fun unlimitedSafetyBoundRejectsRatherThanTruncates() {
        val state=decode(ServerEventsQuery(limit=null),SERVER_EVENTS_SAFETY_LIMIT+1)
        assertEquals(ServerEventsPhase.INVALID,state.phase);assertTrue(state.records.isEmpty())
        assertEquals(ServerEventsIssueKind.HISTORY_SHAPE,state.issue?.kind)
    }"""
new="""    @Test fun unlimitedHasNoTenThousandRecordCeiling() {
        val state=decode(ServerEventsQuery(limit=null),10001)
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals(10001,state.records.size)
    }"""
edit(qtest,old,new)
uitest=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/ServerEventsControlsTest.kt')
edit(uitest,'onNodeWithTag("log-event-icon-relaylog")','onNodeWithTag("log-event-icon-relaylog",useUnmergedTree=true)')
edit(uitest,'onNodeWithTag("log-event-icon-other")','onNodeWithTag("log-event-icon-other",useUnmergedTree=true)')
labels={
'log_detail_title':('Event details','Detalles del evento'),
'log_detail_missing':('Not available','No disponible'),
'log_detail_date':('Date and time','Fecha y hora'),
'log_detail_type':('Event type','Tipo de evento'),
'log_detail_actor':('User','Usuario'),
'log_detail_device':('Device','Dispositivo'),
'log_detail_node':('Device identifier','Identificador del dispositivo'),
'log_detail_message':('Full message','Mensaje completo'),
'log_detail_close':('Close','Cerrar')}
for directory,index in [('values',0),('values-en',0),('values-es',1)]:
    p=Path('app/src/main/res')/directory/'log.xml';tree=ET.parse(p);r=tree.getroot()
    warning=r.find("string[@name='log_event_safety']");assert warning is not None
    warning.text=('Unlimited imposes no fixed app count or message-size ceiling. Large histories may take longer and consume more memory. Change the selection to cancel.' if index==0 else 'Sin límite no impone un máximo fijo de registros ni de tamaño de mensaje en la app. Los historiales grandes pueden tardar y consumir más memoria. Cambia la selección para cancelar.')
    for key,pair in labels.items():
        assert r.find("string[@name='"+key+"']") is None
        ET.SubElement(r,'string',{'name':key}).text=pair[index]
    ET.indent(tree,space='    ');tree.write(p,encoding='utf-8',xml_declaration=True);p.write_text(p.read_text()+'\n');changed.add(str(p))
doc=Path('docs/log.md');text=doc.read_text()
old='Unlimited omits limit in the server request; the app rejects rather than truncates responses over 10000 records or 2000000 characters. Text fields remain bounded to 4096 characters.'
assert text.count(old)==1
text=text.replace(old,'Unlimited omits limit in the server request and imposes no artificial count, frame-size or msg-description-length ceiling. Finite selections retain their selected count and existing frame/text protections. Identity metadata and JSON types remain validated. Actual device resources, timeouts and server permissions still apply; failures are not presented as complete histories.')
text+='\nSelectors use the Activity selector shape, colors, typography and exact anchor/menu width. Type options include icons; count options are text-only and centered. Clickable rows open selectable, scrollable read-only details. Only explicit additional scalar fields msgid/protocol/duration/ip/raddr/bytesin/bytesout are retained, not arbitrary raw payload or credential fields. Details are reset with snapshot/selection invalidation and account/server changes. Real-server and physical-device visual checks remain required.\n'
doc.write_text(text);changed.add(str(doc))
assert len(changed)==15,len(changed)
subprocess.run(['git','add','--',*sorted(changed)],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==changed
Path('validation/presentation-expected.json').write_text(json.dumps(sorted(changed),indent=2)+'\n')
