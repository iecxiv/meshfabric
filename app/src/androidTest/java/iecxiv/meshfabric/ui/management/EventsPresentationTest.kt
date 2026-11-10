package iecxiv.meshfabric.ui.management
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
