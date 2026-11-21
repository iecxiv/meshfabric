package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DeviceReadSubviewsTest {
    @get:Rule val rule=createComposeRule()
    private val node=MeshNode(id="node//read",name="Device",meshid="mesh//group")
    @Test fun eventsIsInlineAndBackUsesCallback() {
        var backs=0
        rule.setContent {MaterialTheme {DeviceReadSubview(node,events=true,onDismiss={backs++})}}
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onNodeWithTag("device_events_search").assertIsDisplayed()
        rule.onNodeWithTag("device_events_refresh").assertIsNotEnabled()
        rule.onAllNodesWithTag("subview-header").assertCountEquals(1)
        rule.onNodeWithTag("device_events_close").performClick()
        rule.runOnIdle {assertEquals(1,backs)}
    }
    @Test fun detailsIsInlineAndBackUsesCallback() {
        var backs=0
        rule.setContent {MaterialTheme {DeviceReadSubview(node,events=false,onDismiss={backs++})}}
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onAllNodesWithTag("subview-header").assertCountEquals(1)
        rule.onNodeWithTag("subview-back").performClick()
        rule.runOnIdle {assertEquals(1,backs)}
    }
    @Test fun deviceButtonsRouteWithoutOpeningFallbackDialogs() {
        var events="";var details=""
        rule.setContent {MaterialTheme {CompositionLocalProvider(LocalDeviceEventsSubviewOpen provides {events=it.id},LocalDeviceDetailsSubviewOpen provides {details=it.id}) {Column(Modifier.fillMaxWidth()) {DeviceOptionsRow(node)}}}}
        rule.onNodeWithTag("device_option_events").performScrollTo().performClick()
        rule.onNodeWithTag("device_option_details").performScrollTo().performClick()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.runOnIdle {assertEquals(node.id,events);assertEquals(node.id,details)}
    }
}
