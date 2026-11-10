FILES={
'app/src/main/java/iecxiv/meshfabric/ui/management/EventsHeaderPresentation.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import java.text.DateFormat
import java.util.Date
@Composable internal fun ServerEventsSearchHeader(query:String,onQuery:(String)->Unit,expanded:Boolean,onExpanded:(Boolean)->Unit,header:@Composable (@Composable ()->Unit)->Unit,tabs:@Composable ()->Unit={}) {
    val requester=remember {FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    Column {
        header {
            IconButton(onClick={
                if(expanded) {onQuery("");focus.clearFocus(force=true);keyboard?.hide()}
                onExpanded(!expanded)
            },modifier=Modifier.size(38.dp).testTag("log-events-search-toggle")) {
                Icon(if(expanded) Icons.Default.Close else Icons.Default.Search,stringResource(R.string.search),Modifier.size(20.dp))
            }
        }
        tabs()
        AnimatedVisibility(expanded) {
            LaunchedEffect(expanded) {if(expanded) {withFrameNanos {};requester.requestFocus();keyboard?.show()}}
            OutlinedTextField(query,onQuery,singleLine=true,placeholder={Text(stringResource(R.string.log_search))},
                modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).focusRequester(requester).testTag("log-events-search"),
                trailingIcon={if(query.isNotEmpty()) IconButton(onClick={onQuery("")},modifier=Modifier.testTag("log-events-search-clear")) {Icon(Icons.Default.Close,stringResource(R.string.clear_search))}})
        }
    }
}
@Composable internal fun ServerEventDayHeader(group:ServerEventDay) {
    val scheme=MaterialTheme.colorScheme
    val label=group.date?.let {DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(group.entries.first().value.time!!))} ?: stringResource(R.string.log_date_unknown)
    Surface(Modifier.fillMaxWidth().testTag("log-date-${group.date ?: "unknown"}"),shape=MaterialTheme.shapes.medium,color=scheme.primaryContainer) {
        Text(label,Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold,color=scheme.onPrimaryContainer)
    }
}
""",
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventDays.kt':r"""package iecxiv.meshfabric.ui.management
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
internal data class ServerEventDay(val date:LocalDate?,val entries:List<IndexedValue<ServerEventRecord>>)
internal fun serverEventDays(rows:List<ServerEventRecord>,zone:ZoneId=ZoneId.systemDefault()):List<ServerEventDay> =
    rows.withIndex().sortedWith(compareByDescending<IndexedValue<ServerEventRecord>> {it.value.time ?: Long.MIN_VALUE})
        .groupBy {it.value.time?.let {time->Instant.ofEpochMilli(time).atZone(zone).toLocalDate()}}
        .entries.sortedWith(compareByDescending {it.key}).map {ServerEventDay(it.key,it.value)}
""",
'app/src/test/java/iecxiv/meshfabric/ui/management/ServerEventDaysTest.kt':r"""package iecxiv.meshfabric.ui.management
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
class ServerEventDaysTest {
    private fun row(time:Long?)=ServerEventRecord("manual","event","","","",time)
    private val utc=ZoneId.of("UTC")
    @Test fun knownDaysAreNewestFirst() {
        val groups=serverEventDays(listOf(row(0),row(86400000)),utc)
        assertEquals(listOf(LocalDate.of(1970,1,2),LocalDate.of(1970,1,1)),groups.map {it.date})
    }
    @Test fun sameDayHasOneHeaderAndNewestEventFirst() {
        val groups=serverEventDays(listOf(row(0),row(1000)),utc)
        assertEquals(1,groups.size);assertEquals(listOf(1000L,0L),groups.single().entries.map {it.value.time})
    }
    @Test fun missingDatesStayUnknownAndLast() {
        val groups=serverEventDays(listOf(row(null),row(0)),utc)
        assertEquals(2,groups.size);assertNull(groups.last().date);assertNull(groups.last().entries.single().value.time)
    }
    @Test fun timezoneDeterminesTheEventDayNotTheReceiveDay() {
        val time=Instant.parse("2026-11-10T00:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026,11,9),serverEventDays(listOf(row(time)),ZoneId.of("America/Los_Angeles")).single().date)
        assertEquals(LocalDate.of(2026,11,10),serverEventDays(listOf(row(time)),utc).single().date)
    }
    @Test fun duplicateRecordsRetainDistinctOriginalIndices() {
        val event=row(0);val groups=serverEventDays(listOf(event,event),utc)
        assertEquals(listOf(0,1),groups.single().entries.map {it.index});assertEquals(2,groups.single().entries.size)
    }
}
""",
'app/src/androidTest/java/iecxiv/meshfabric/ui/management/EventsHeaderDatesTest.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
@Composable internal fun SearchableEventsTestPanel(state:ServerEventsSnapshot,inventory:ServerEventInventory=ServerEventInventory()) {
    var query by rememberSaveable {mutableStateOf("")}
    var expanded by rememberSaveable {mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        ServerEventsSearchHeader(query,{query=it},expanded,{expanded=it},header={actions->Row {actions()}})
        ServerEventsPanel(state,query,{query=it},true,{},Modifier.weight(1f),inventory=inventory)
    }
}
class EventsHeaderDatesTest {
    @get:Rule val compose=createComposeRule()
    private fun time(day:Int)=LocalDate.of(2026,11,day).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun row(message:String,day:Int?)=ServerEventRecord("manual",message,"operator","node//private-device-id","surface",day?.let {time(it)},meshId="mesh//private-mesh-id",groupName="agent")
    private fun state(vararg rows:ServerEventRecord)=ServerEventsSnapshot(ServerEventsPhase.READY,rows.toList())
    @Test fun searchStartsHiddenAndHeaderCloseClearsTheFilter() {
        compose.setContent {MaterialTheme {SearchableEventsTestPanel(state(row("needle",10),row("other",10)))}}
        compose.onNodeWithTag("log-events-search").assertDoesNotExist();compose.onNodeWithTag("log-events-search-toggle").performClick()
        compose.onNodeWithTag("log-events-search").performTextInput("needle");compose.onNodeWithText("other").assertDoesNotExist()
        compose.onNodeWithTag("log-events-search-toggle").performClick();compose.onNodeWithTag("log-events-search").assertDoesNotExist()
        compose.onNodeWithTag("log-events-list").performScrollToNode(hasText("other"));compose.onNodeWithText("other").assertExists()
    }
    @Test fun knownAndUnknownDatesHaveSeparateBands() {
        compose.setContent {MaterialTheme {SearchableEventsTestPanel(state(row("known",10),row("unknown",null)))}}
        compose.onNodeWithTag("log-date-2026-11-10").assertExists()
        compose.onNodeWithTag("log-events-list").performScrollToNode(hasTestTag("log-date-unknown"));compose.onNodeWithTag("log-date-unknown").assertExists()
    }
    @Test fun filteringRemovesUnusedDateBands() {
        compose.setContent {MaterialTheme {SearchableEventsTestPanel(state(row("needle",10),row("other",9)))}}
        compose.onNodeWithTag("log-events-search-toggle").performClick();compose.onNodeWithTag("log-events-search").performTextInput("needle")
        compose.onNodeWithTag("log-date-2026-11-10").assertExists();compose.onNodeWithTag("log-date-2026-11-09").assertDoesNotExist()
    }
    @Test fun hiddenIdentifiersRemainSearchableAndVisibleInDetails() {
        compose.setContent {MaterialTheme {SearchableEventsTestPanel(state(row("entry",10)))}}
        compose.onNodeWithTag("log-event-node-0",useUnmergedTree=true).assertDoesNotExist();compose.onNodeWithTag("log-event-mesh-0",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("log-events-search-toggle").performClick();compose.onNodeWithTag("log-events-search").performTextInput("private-device-id")
        compose.onNodeWithTag("log-events-list").performScrollToNode(hasTestTag("log-event-row-0"));compose.onNodeWithTag("log-event-row-0").performClick()
        compose.onNodeWithText("node//private-device-id").assertExists();compose.onNodeWithText("mesh//private-mesh-id").assertExists()
    }
    @Test fun eventSearchStateSurvivesTabRoundTrip() {
        val tab=mutableIntStateOf(0)
        compose.setContent {MaterialTheme {LogSectionContent(tab.intValue) {if(it==0) SearchableEventsTestPanel(state(row("needle",10),row("other",10))) else Text("activity-placeholder")}}}
        compose.onNodeWithTag("log-events-search-toggle").performClick();compose.onNodeWithTag("log-events-search").performTextInput("needle")
        compose.runOnIdle {tab.intValue=1};compose.onNodeWithText("activity-placeholder").assertExists()
        compose.runOnIdle {tab.intValue=0};compose.onNodeWithTag("log-events-search").assertTextContains("needle");compose.onNodeWithText("other").assertDoesNotExist()
    }
}
"""
}
from pathlib import Path
import json,subprocess,xml.etree.ElementTree as ET
changed=set()
def edit(path,old,new):
    p=Path(path);s=p.read_text();assert s.count(old)==1,(str(p),old);p.write_text(s.replace(old,new,1));changed.add(str(p))
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management');screen=root/'LogScreen.kt'
assert subprocess.check_output(['git','hash-object',str(screen)],text=True).strip()=='8355f3de45a4f460e66acabdffad9dfa6efe4c4f'
for p,sha in [('app/src/main/java/iecxiv/meshfabric/ui/management/ActivityTimeline.kt','b6cbb80c5add9d4fa06f514a331683ee5b9a9e06'),('app/src/main/java/iecxiv/meshfabric/ui/components/MeshFabricAppBar.kt','cd3127d602df77baaf74550b87f2333877e282d2')]:
    assert subprocess.check_output(['git','hash-object',p],text=True).strip()==sha
for path,content in FILES.items():
    p=Path(path);assert not p.exists(),path;p.write_text(content);changed.add(path)
edit(screen,'    var query by rememberSaveable {mutableStateOf("")}','    var query by rememberSaveable {mutableStateOf("")}\n    var showSearch by rememberSaveable {mutableStateOf(false)}')
edit(screen,'Scaffold(topBar={Column {MeshFabricAppBar(viewModel,onOpenSettings);header()}})','Scaffold(topBar={ServerEventsSearchHeader(query,{query=it},showSearch,{showSearch=it},header={actions->MeshFabricAppBar(viewModel,onOpenSettings,actions={actions()})},tabs=header)})')
edit(screen,'        OutlinedTextField(query,onQuery,label={Text(stringResource(R.string.log_search))},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("log-events-search"))\n','')
edit(screen,'LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {itemsIndexed(rows) {index,row->','LazyColumn(Modifier.weight(1f).testTag("log-events-list"),verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(vertical=6.dp)) {\n                serverEventDays(rows).forEach {group->\n                    item(key="date-${group.date ?: "unknown"}") {ServerEventDayHeader(group)}\n                    itemsIndexed(group.entries) {_,entry->\n                        val index=entry.index\n                        val row=entry.value')
edit(screen,'            }}\n        }','            }}}\n        }')
edit(screen,'                    if(row.nodeId.isNotBlank()) Text(stringResource(R.string.log_detail_node)+": "+row.nodeId,Modifier.testTag("log-event-node-$index"),style=MaterialTheme.typography.labelSmall)\n','')
edit(screen,'                    if(row.meshId.isNotBlank()) Text(stringResource(R.string.log_detail_mesh)+": "+row.meshId,Modifier.testTag("log-event-mesh-$index"),style=MaterialTheme.typography.labelSmall)\n','')
test=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/LogScreenTest.kt')
edit(test,'        val query=mutableStateOf("")\n','')
edit(test,'ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.READY,listOf(row)),query.value,{query.value=it},true,{})','SearchableEventsTestPanel(ServerEventsSnapshot(ServerEventsPhase.READY,listOf(row)))')
edit(test,'        compose.onNodeWithTag("log-events-search").performTextInput("not-present")','        compose.onNodeWithTag("log-events-search-toggle").performClick()\n        compose.onNodeWithTag("log-events-search").performTextInput("not-present")')
test=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management/EventContentUiTest.kt')
edit(test,'fun cardAndDetailsShareDeviceGroupAndBothIdentifiers()','fun cardHidesIdentifiersWhileDetailsKeepThem()')
edit(test,'compose.onNodeWithTag("log-event-node-0",useUnmergedTree=true).assertExists();compose.onNodeWithTag("log-event-mesh-0",useUnmergedTree=true).assertExists()','compose.onNodeWithTag("log-event-node-0",useUnmergedTree=true).assertDoesNotExist();compose.onNodeWithTag("log-event-mesh-0",useUnmergedTree=true).assertDoesNotExist()')
edit(test,'compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface(agent)")\n        compose.onNodeWithTag("log-event-command")','compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface(agent)")\n        compose.onNodeWithText("node//device").assertExists();compose.onNodeWithText("mesh//agent").assertExists()\n        compose.onNodeWithTag("log-event-command")')
edit(test,'compose.setContent {var query by remember {mutableStateOf("")};MaterialTheme {ServerEventsPanel(state(event()),query,{query=it},true,{})}}','compose.setContent {MaterialTheme {SearchableEventsTestPanel(state(event()))}}\n        compose.onNodeWithTag("log-events-search-toggle").performClick()')
for directory,index in [('values',0),('values-en',0),('values-es',1)]:
    p=Path('app/src/main/res')/directory/'log.xml';tree=ET.parse(p);r=tree.getroot();assert r.find("string[@name='log_date_unknown']") is None
    ET.SubElement(r,'string',{'name':'log_date_unknown'}).text=('Date not available','Fecha no disponible')[index]
    ET.indent(tree,space='    ');tree.write(p,encoding='utf-8',xml_declaration=True);p.write_text(p.read_text()+'\n');changed.add(str(p))
p=Path('docs/log.md');s=p.read_text();old='Cards and read-only dialogs share device(group), with available node and mesh IDs separately.';assert s.count(old)==1
s=s.replace(old,'Cards and read-only dialogs share device(group); node and mesh IDs are shown only in details, not as card metadata.')
s+='\nEvents search is collapsed by default, opened from the app-bar search icon, and displayed below the section tabs with focus/keyboard behavior matching Activity. Header close clears the query and hides the keyboard; the field clear button keeps search open. Search still includes hidden node/mesh identifiers and command content. Query/visibility are saved per Events tab within the existing account/server scope. The permanent in-panel search field is removed.\nVisible filtered events are grouped by their event timestamp in the current local timezone, newest day first, using Activity date-band shape, padding, typography and primaryContainer/onPrimaryContainer theme colors. Missing event times form a separate localized unknown-date band and are never assigned the receive date. Duplicate events are preserved. Device/group, commands and the existing details dialog are otherwise unchanged. Tests are updated to open search from the header and require identifiers absent from cards but present in details.\n'
p.write_text(s);changed.add(str(p))
assert len(changed)==11,changed
subprocess.run(['git','add','--',*sorted(changed)],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==changed
assert not subprocess.check_output(['git','diff','--cached','--name-only','--',str(root/'ServerEventDetails.kt')],text=True).strip()
Path('validation/events-header-expected.json').write_text(json.dumps(sorted(changed),indent=2)+'\n')
