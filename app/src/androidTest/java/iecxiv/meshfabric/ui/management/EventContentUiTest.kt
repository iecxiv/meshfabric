package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
class EventContentUiTest {
    @get:Rule val compose=createComposeRule()
    private fun event()=ServerEventRecord("runcommands","Running commands","operator","node//device","surface",1L,details=mapOf("cmds" to "echo ready\nwhoami","cmdType" to "2","runAsUser" to "1"),meshId="mesh//agent",groupName="agent")
    private fun state(row:ServerEventRecord)=ServerEventsSnapshot(ServerEventsPhase.READY,listOf(row))
    @Test fun cardHidesIdentifiersWhileDetailsKeepThem() {
        compose.setContent {MaterialTheme {ServerEventsPanel(state(event()),"",{},true,{})}}
        compose.onNodeWithTag("log-event-device-0",useUnmergedTree=true).assertTextEquals("surface (agent)")
        compose.onNodeWithTag("log-event-node-0",useUnmergedTree=true).assertDoesNotExist();compose.onNodeWithTag("log-event-mesh-0",useUnmergedTree=true).assertDoesNotExist()
        compose.onNodeWithTag("log-event-row-0").performClick()
        compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface")
        compose.onNodeWithText("node//device").assertExists();compose.onNodeWithText("mesh//agent").assertExists()
        compose.onNodeWithTag("log-event-command").assertTextEquals("echo ready\nwhoami")
        compose.onNodeWithTag("log-event-details-message").assertTextEquals("Running commands")
    }
    @Test fun longCommandIsNotTruncatedInReadOnlyDetails() {
        val command="echo "+"x".repeat(5000)+"\nEND"
        compose.setContent {MaterialTheme {ServerEventsPanel(state(event().copy(details=mapOf("cmds" to command))),"",{},true,{})}}
        compose.onNodeWithTag("log-event-row-0").performClick();compose.onNodeWithTag("log-event-command").assertTextEquals(command)
    }
    @Test fun groupAndCommandAreSearchable() {
        compose.setContent {MaterialTheme {SearchableEventsTestPanel(state(event()))}}
        compose.onNodeWithTag("log-events-search-toggle").performClick()
        compose.onNodeWithTag("log-events-search").performTextInput("whoami");compose.onNodeWithTag("log-event-row-0").assertExists()
        compose.onNodeWithTag("log-events-search").performTextClearance();compose.onNodeWithTag("log-events-search").performTextInput("agent");compose.onNodeWithTag("log-event-row-0").assertExists()
    }
    @Test fun inventoryChangeRemovesOpenResolvedDetails() {
        val inventory=mutableStateOf(ServerEventInventory(mapOf("node//device" to EventDevice("node//device","surface","mesh//agent")),mapOf("mesh//agent" to "agent")))
        compose.setContent {MaterialTheme {ServerEventsPanel(state(event().copy(nodeName="",groupName="",meshId="")),"",{},true,{},inventory=inventory.value)}}
        compose.onNodeWithTag("log-event-row-0").performClick();compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface")
        compose.runOnIdle {inventory.value=ServerEventInventory()};compose.onNodeWithTag("log-event-details").assertDoesNotExist()
    }
}
