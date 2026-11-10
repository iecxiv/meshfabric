package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
class LogScreenTest {
    @get:Rule val compose=createComposeRule()
    @Test fun activityStateSurvivesEventsRoundTrip() {
        val tab=mutableIntStateOf(0)
        compose.setContent {MaterialTheme {Column {LogSectionContent(tab.intValue) {selected->
            if(selected==0) {var text by rememberSaveable {mutableStateOf("")};OutlinedTextField(text,{text=it},Modifier.testTag("activity-draft"))} else Text("events-content")
        }}}}
        compose.onNodeWithTag("activity-draft").performTextInput("saved-search")
        compose.runOnIdle {tab.intValue=1};compose.onNodeWithText("events-content").assertIsDisplayed()
        compose.runOnIdle {tab.intValue=0};compose.onNodeWithTag("activity-draft").assertTextContains("saved-search")
    }
    @Test fun loadingAndTimeoutAreNeverEmptyHistory() {
        val state=mutableStateOf(ServerEventsSnapshot(ServerEventsPhase.LOADING))
        compose.setContent {MaterialTheme {SearchableEventsTestPanel(state.value)}}
        compose.onNodeWithTag("log-events-empty").assertDoesNotExist();compose.onNodeWithTag("log-events-refresh").assertIsNotEnabled()
        compose.runOnIdle {state.value=ServerEventsSnapshot(ServerEventsPhase.TIMEOUT)}
        compose.onNodeWithTag("log-events-state").assertIsDisplayed();compose.onNodeWithTag("log-events-empty").assertDoesNotExist()
        compose.runOnIdle {state.value=ServerEventsSnapshot(ServerEventsPhase.READY)}
        compose.onNodeWithTag("log-events-empty").assertIsDisplayed()
    }
    @Test fun receivedEventsCanBeSearchedWithoutShowingInternalIds() {
        val row=ServerEventRecord("runcommands","example event","Operator","node/a/private","",null)
        compose.setContent {MaterialTheme {SearchableEventsTestPanel(ServerEventsSnapshot(ServerEventsPhase.READY,listOf(row)))}}
        compose.onNodeWithText("example event").assertIsDisplayed();compose.onNodeWithText("node/a/private").assertDoesNotExist()
        compose.onNodeWithTag("log-events-search-toggle").performClick()
        compose.onNodeWithTag("log-events-search").performTextInput("not-present")
        compose.onNodeWithTag("log-events-no-match").assertIsDisplayed();compose.onNodeWithText("example event").assertDoesNotExist()
    }
    @Test fun invalidationRemovesOldRows() {
        val state=mutableStateOf(ServerEventsSnapshot(ServerEventsPhase.READY,listOf(ServerEventRecord("manual","old event","","","",null))))
        compose.setContent {MaterialTheme {ServerEventsPanel(state.value,"",{},true,{})}}
        compose.onNodeWithText("old event").assertIsDisplayed()
        compose.runOnIdle {state.value=ServerEventsSnapshot(ServerEventsPhase.INVALIDATED)}
        compose.onNodeWithText("old event").assertDoesNotExist();compose.onNodeWithTag("log-events-empty").assertDoesNotExist()
    }
}
