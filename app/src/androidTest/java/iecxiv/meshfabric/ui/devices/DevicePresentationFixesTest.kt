package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.platform.testTag
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.management.ServerEventDetailsDialog
import iecxiv.meshfabric.ui.management.ServerEventRecord
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DevicePresentationFixesTest {
    @get:Rule val rule=createComposeRule()
    @Test fun decorationShowsDeviceStateAndOperatingSystem() {
        rule.setContent {MaterialTheme {DeviceStatusDecoration(MeshNode(id="node//a",name="Device",conn=1,osdesc="Linux"),Modifier.fillMaxWidth(),"console")}}
        rule.onNodeWithTag("console_badge").assertIsDisplayed()
        rule.onNodeWithTag("console_os").assertTextEquals("Linux")
    }
    @Test fun missingInventoryIsNotPresentedAsDisconnectedDevice() {
        rule.setContent {MaterialTheme {DeviceStatusDecoration(null,Modifier.fillMaxWidth(),"console")}}
        rule.onNodeWithTag("console_badge").assert(hasText("Status not reported") or hasText("Estado no informado"))
        rule.onNodeWithTag("console_os").assertDoesNotExist()
    }
    @Test fun eventDetailsUsesWiderDialog() {
        val wide=mutableStateOf(false)
        val event=ServerEventRecord("manual","Event","user","node//a","Device",1800000000000L)
        rule.setContent {MaterialTheme {if(wide.value) ServerEventDetailsDialog(event) {} else AlertDialog(onDismissRequest={},title={Text("Details")},text={Text("Event")},confirmButton={},modifier=Modifier.testTag("reference_dialog"))}}
        val narrow=rule.onNodeWithTag("reference_dialog").fetchSemanticsNode().boundsInRoot.width
        rule.runOnIdle {wide.value=true}
        val actual=rule.onNodeWithTag("log-event-details").fetchSemanticsNode().boundsInRoot.width
        assertTrue("Event details should be wider",actual>narrow+1f)
        rule.onNodeWithTag("log-event-details-close").assertIsDisplayed()
    }
}
