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
class DeviceCenteredSubviewLayoutTest {
    @get:Rule val rule=createComposeRule()
    @Test fun shortBodyIsCenteredAndFooterDoesNotMoveWithLongContent() {
        val long=mutableStateOf(false)
        rule.setContent {MaterialTheme {DeviceGeneralFrame(MeshNode(id="node//layout",name="Device"),{},inline=true,body={if(long.value) repeat(80) {Text("Line $it",Modifier.testTag("line_$it"))} else Text("Short",Modifier.testTag("short_body"))},footer={TextButton(onClick={}) {Text("Back")}})}}
        val viewport=rule.onNodeWithTag("centered_body_viewport").fetchSemanticsNode().boundsInRoot
        val body=rule.onNodeWithTag("short_body").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.center.y,body.center.y,2f)
        val footer=rule.onNodeWithTag("device_general_footer").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {long.value=true}
        assertEquals(footer.top,rule.onNodeWithTag("device_general_footer").fetchSemanticsNode().boundsInRoot.top,2f)
        rule.onNodeWithTag("line_79").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("device_general_footer").assertIsDisplayed()
    }
}
