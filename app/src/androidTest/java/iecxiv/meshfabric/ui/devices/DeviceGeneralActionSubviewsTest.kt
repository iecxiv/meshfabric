package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DeviceGeneralActionSubviewsTest {
    @get:Rule val rule=createComposeRule()
    @Test fun fiveActionsUseSingleSubviewHeaderAndReturnToGeneral() {
        val action=mutableStateOf<DeviceGeneralActionKind?>(null)
        val node=MeshNode(id="node//actions",name="Device",meshid="mesh//a",osdesc="Linux")
        rule.setContent {MaterialTheme {DeviceGeneralActionHost(node,action.value,onDismiss={},onActionBack={action.value=null})}}
        val tags=listOf("device_actions_close","device_notes_close","device_manual_event_close","device_run_close","device_message_close")
        for(kind in DeviceGeneralActionKind.entries) {
            rule.runOnIdle {action.value=kind}
            rule.onNode(isDialog()).assertDoesNotExist()
            rule.onAllNodesWithTag("subview-header").assertCountEquals(1)
            rule.onNodeWithTag(tags[kind.ordinal]).performClick()
            rule.runOnIdle {assertNull(action.value)}
        }
    }
    @Test fun layersKeepParentDraftWhileHidingItsSemantics() {
        val open=mutableStateOf(false)
        rule.setContent {MaterialTheme {
            var draft by remember {mutableStateOf("")}
            DeviceGeneralActionLayers(open.value,background={OutlinedTextField(draft,{draft=it},modifier=Modifier.testTag("parent_draft"))},content={Text("Action")})
        }}
        rule.onNodeWithTag("parent_draft").performTextInput("Preserved draft")
        rule.runOnIdle {open.value=true}
        rule.onNodeWithTag("parent_draft").assertDoesNotExist()
        rule.runOnIdle {open.value=false}
        rule.onNodeWithTag("parent_draft").assertTextContains("Preserved draft")
    }
    @Test fun actionConfirmationIsSubviewNotDialog() {
        var closed=0
        rule.setContent {MaterialTheme {DeviceGeneralActionSurface(true,{closed++},title={Text("Review")},text={Text("Details")},confirmButton={TextButton(onClick={}) {Text("Confirm")}})}}
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onAllNodesWithTag("subview-header").assertCountEquals(1)
        rule.onNodeWithTag("user-action-back").performClick()
        rule.runOnIdle {assertEquals(1,closed)}
    }
}
