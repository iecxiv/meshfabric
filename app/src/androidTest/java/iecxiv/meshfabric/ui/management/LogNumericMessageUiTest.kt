package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.gson.JsonParser
import org.junit.Rule
import org.junit.Test
class LogNumericMessageUiTest {
    @get:Rule val compose=createComposeRule()
    private fun snapshot():ServerEventsSnapshot {
        val gate=ServerEventsReadGate();val scope=ServerEventsScope("server","actor",1,"rights")
        val attempt=gate.begin(scope,"test")
        gate.receive(attempt,scope,JsonParser.parseString("{\"action\":\"events\",\"tag\":\"test\",\"events\":[{\"msg\":123},{\"msg\":\"text message\"}]}").asJsonObject)
        return gate.state
    }
    @Test fun numericAndTextMessagesRemainVisible() {
        val state=snapshot()
        compose.setContent {MaterialTheme {ServerEventsPanel(state,"",{},true,{})}}
        compose.onNodeWithText("123").assertIsDisplayed()
        compose.onNodeWithText("text message").assertIsDisplayed()
        compose.onNodeWithTag("log-events-state").assertDoesNotExist()
    }
    @Test fun numericLiteralCanBeSearched() {
        val state=snapshot()
        compose.setContent {MaterialTheme {SearchableEventsTestPanel(state)}}
        compose.onNodeWithTag("log-events-search-toggle").performClick()
        compose.onNodeWithTag("log-events-search").performTextInput("123")
        compose.onNode(hasText("123") and hasSetTextAction().not()).assertIsDisplayed()
        compose.onNodeWithText("text message").assertDoesNotExist()
        compose.onNodeWithTag("log-events-no-match").assertDoesNotExist()
    }
}
