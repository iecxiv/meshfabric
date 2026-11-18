package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceOtherOptionsUiTest {
    @get:Rule val compose=createComposeRule()
    private val node=MeshNode(id="node//options",name="Equipo",meshid="mesh//options",conn=1)
    @Composable private fun card(id:String=node.id) {
        DeviceCard(node.copy(id=id),onOpenDesktop={},onOpenTerminal={},onOpenFiles={})
    }
    @Test fun toggleAndDefaultAvailability() {
        compose.setContent { MaterialTheme { card() } }
        compose.onNodeWithTag("device_options_row").assertDoesNotExist()
        compose.onNodeWithTag("device_options_toggle").performClick()
        compose.onNodeWithTag("device_options_row").assertIsDisplayed()
        compose.onNodeWithTag("device_option_general").assertIsEnabled()
        compose.onNodeWithTag("device_option_details").assertIsEnabled()
        compose.onNodeWithTag("device_option_events").assertIsEnabled()
        compose.onNodeWithTag("device_option_console").assertIsNotEnabled()
        compose.onNodeWithTag("device_option_software").assertDoesNotExist()
        compose.onNodeWithTag("device_options_toggle").performClick()
        compose.onNodeWithTag("device_options_row").assertDoesNotExist()
    }
    @Test fun independentCards() {
        compose.setContent { MaterialTheme { Column { card("node//a");card("node//b") } } }
        compose.onAllNodesWithTag("device_options_toggle")[0].performClick()
        compose.onAllNodesWithTag("device_options_row").assertCountEquals(1)
        compose.onAllNodesWithTag("device_options_toggle")[1].performClick()
        compose.onAllNodesWithTag("device_options_row").assertCountEquals(2)
    }
    @Test fun softwareRequiresVerifiedPermissionAndHandler() {
        val allowed=mutableStateOf(false)
        compose.setContent { MaterialTheme {
            CompositionLocalProvider(LocalDeviceOptionActions provides DeviceOptionActions(software={},softwareAllowed={ allowed.value })) { DeviceOptionsRow(node) }
        } }
        compose.onNodeWithTag("device_option_software").assertDoesNotExist()
        compose.runOnIdle { allowed.value=true }
        compose.onNodeWithTag("device_option_software").assertIsEnabled()
        compose.runOnIdle { allowed.value=false }
        compose.onNodeWithTag("device_option_software").assertDoesNotExist()
    }
    @Test fun generalOpensReadOnlyView() {
        compose.setContent { MaterialTheme { DeviceOptionsRow(node) } }
        compose.onNodeWithTag("device_option_general").performClick()
        compose.onNodeWithText("Equipo").assertIsDisplayed()
    }
    @Test fun eventsOpensNativeDialogAndClosesWithoutConnection() {
        var subtitle=""
        compose.setContent {
            subtitle=if(LocalConfiguration.current.locales[0].language=="es") "Eventos" else "Events"
            MaterialTheme {DeviceOptionsRow(node)}
        }
        compose.onNodeWithTag("device_option_events").performClick()
        compose.onNodeWithTag("device_events_subtitle",useUnmergedTree=true).assertTextEquals(subtitle).assertIsDisplayed()
        compose.onNodeWithTag("device_events_refresh").assertIsNotEnabled()
        compose.onNodeWithTag("device_events_close").performClick()
        compose.onNodeWithTag("device_events_surface").assertDoesNotExist()
    }
    private fun compareEventsHeader(dark:Boolean) {
        val events=mutableStateOf(false)
        compose.setContent {
            MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
                if(events.value) DeviceEventsDialog(node) {} else DeviceGeneralDialog(node) {}
            }
        }
        val width=compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot.width
        val title=compose.onNodeWithText(node.name).fetchSemanticsNode().boundsInRoot
        val subtitle=compose.onNodeWithTag("device_general_subtitle",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {events.value=true}
        assertEquals(width,compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot.width,1f)
        val actual=compose.onNodeWithText(node.name).fetchSemanticsNode().boundsInRoot
        val actualSubtitle=compose.onNodeWithTag("device_events_subtitle",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertEquals(title.width,actual.width,1f)
        assertEquals(title.height,actual.height,1f)
        assertEquals(subtitle.height,actualSubtitle.height,1f)
        compose.onNodeWithTag("device_events_close").assertIsDisplayed()
        compose.onNodeWithTag("device_events_refresh").assertIsDisplayed()
    }
    @Test fun eventsMatchesGeneralLightHeader()=compareEventsHeader(false)
    @Test fun eventsMatchesGeneralDarkHeader()=compareEventsHeader(true)
}
