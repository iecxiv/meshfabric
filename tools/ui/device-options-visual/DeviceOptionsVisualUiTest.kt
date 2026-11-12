package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.model.SessionStatus
import iecxiv.meshfabric.ui.management.LocalDeviceActionMenu
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeviceOptionsVisualUiTest {
    @get:Rule val compose=createComposeRule()
    private val node=MeshNode(id="node//visual",name="Equipo",meshid="mesh//visual",conn=1)
    private fun heights(dark:Boolean) {
        compose.setContent { MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
            Column(Modifier.width(360.dp)) {
                DeviceOptionsRow(node)
                SessionActionButton("Terminal",Icons.Default.Terminal,SessionStatus.NONE,{},Modifier.testTag("session_reference"))
            }
        } }
        val reference=compose.onNodeWithTag("session_reference").getUnclippedBoundsInRoot().height.value
        for(key in listOf("general","events","details","console")) {
            val height=compose.onNodeWithTag("device_option_$key").getUnclippedBoundsInRoot().height.value
            assertEquals(key,reference,height,0.5f)
        }
    }
    @Test fun matchingHeightsLight()=heights(false)
    @Test fun matchingHeightsDark()=heights(true)
    @Test fun headerSpacingAndIndependentClicks() {
        var menuClicks=0
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalDeviceActionMenu provides { _ ->
                IconButton(onClick={ menuClicks++ },modifier=Modifier.testTag("menu_reference")) { Icon(Icons.Default.MoreVert,null) }
            }) {
                DeviceCard(node,onOpenDesktop={},onOpenTerminal={},onOpenFiles={})
            }
        } }
        val toggle=compose.onNodeWithTag("device_options_toggle").getUnclippedBoundsInRoot()
        val menu=compose.onNodeWithTag("menu_reference").getUnclippedBoundsInRoot()
        assertEquals(32f,menu.center.x.value-toggle.center.x.value,0.5f)
        compose.onNodeWithTag("device_options_toggle").performClick()
        compose.onNodeWithTag("device_options_row").assertIsDisplayed()
        assertEquals(0,menuClicks)
        compose.onNodeWithTag("menu_reference").performClick()
        assertEquals(1,menuClicks)
        compose.onNodeWithTag("device_options_row").assertIsDisplayed()
    }
}
