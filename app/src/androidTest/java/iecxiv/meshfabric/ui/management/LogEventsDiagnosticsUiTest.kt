package iecxiv.meshfabric.ui.management
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
