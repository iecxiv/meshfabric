package iecxiv.meshfabric.ui.management
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
