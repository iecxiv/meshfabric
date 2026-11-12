package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Rule
import org.junit.Test

class DeviceOtherOptionsUiTest {
    @get:Rule val compose=createComposeRule()
    private fun card(id:String="node//options") {
        DeviceCard(MeshNode(id=id,name="Equipo",meshid="mesh//options",conn=1),
            onOpenDesktop={},onOpenTerminal={},onOpenFiles={})
    }
    @Test fun initiallyCollapsedAndToggleable() {
        compose.setContent { MaterialTheme { card() } }
        compose.onNodeWithTag("device_other_options_row").assertDoesNotExist()
        compose.onNodeWithTag("device_other_options_toggle").performClick()
        compose.onNodeWithTag("device_other_options_row").assertIsDisplayed()
        compose.onNodeWithTag("device_other_details").assertIsEnabled()
        compose.onNodeWithTag("device_other_events").assertIsNotEnabled()
        compose.onNodeWithTag("device_other_console").assertIsNotEnabled()
        compose.onNodeWithTag("device_other_options_toggle").performClick()
        compose.onNodeWithTag("device_other_options_row").assertDoesNotExist()
    }
    @Test fun cardsExpandIndependently() {
        compose.setContent { MaterialTheme { Column { card("node//a");card("node//b") } } }
        compose.onAllNodesWithTag("device_other_options_toggle")[0].performClick()
        compose.onAllNodesWithTag("device_other_options_row").assertCountEquals(1)
        compose.onAllNodesWithTag("device_other_options_toggle")[1].performClick()
        compose.onAllNodesWithTag("device_other_options_row").assertCountEquals(2)
    }
}
