package iecxiv.meshfabric.ui.management
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
