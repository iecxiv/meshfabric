package iecxiv.meshfabric
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.devices.DeviceCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class SessionResumeAndDeviceCardTest {
    @get:Rule val rule = createComposeRule()
    private val node = MeshNode(id="node//resume",name="Resume device",conn=1)
    private fun icon() = rule.onNodeWithTag("device_icon_${node.id}",useUnmergedTree=true)
    private fun title() = rule.onNodeWithTag("device_title_${node.id}",useUnmergedTree=true)
    private fun card() = rule.onNodeWithTag("device_card_${node.id}",useUnmergedTree=true)
    private fun options() = rule.onNodeWithTag("device_options_row",useUnmergedTree=true)
    @Test fun cardTapTargetsToggleButButtonsDoNotToggle() {
        var opened=0
        rule.setContent { MaterialTheme { DeviceCard(node,onOpenDesktop={opened++},onOpenTerminal={opened++},onOpenFiles={opened++}) } }
        options().assertDoesNotExist()
        icon().performTouchInput { click() }; options().assertIsDisplayed()
        title().performTouchInput { click() }; options().assertDoesNotExist()
        card().performTouchInput { click(Offset(2f,center.y)) }; options().assertIsDisplayed()
        card().performTouchInput { click(Offset(2f,center.y)) }; options().assertDoesNotExist()
        val context=ApplicationProvider.getApplicationContext<Application>()
        listOf(R.string.desktop,R.string.terminal,R.string.files).forEach { label ->
            rule.onNodeWithText(context.getString(label)).performClick(); options().assertDoesNotExist()
        }
        rule.runOnIdle { assertEquals(3,opened) }
        rule.onNodeWithTag("device_options_toggle").performClick(); options().assertIsDisplayed()
        rule.onNodeWithTag("device_options_toggle").performClick(); options().assertDoesNotExist()
    }
    @Test fun scrollingDoesNotToggleAndSelectionRemainsAvailable() {
        val selecting=mutableStateOf(false)
        var selections=0
        rule.setContent { MaterialTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(200.dp))
            DeviceCard(node,onOpenDesktop={},onOpenTerminal={},onOpenFiles={},selectionMode=selecting.value,
                onToggleSelection={selections++},onStartSelection={selecting.value=true})
            Spacer(Modifier.height(1400.dp))
        } } }
        icon().performTouchInput { down(center); moveBy(Offset(0f,-120f),delayMillis=180); up() }
        options().assertDoesNotExist()
        card().performScrollTo()
        title().performTouchInput { longClick() }
        rule.runOnIdle { assertTrue(selecting.value) }
        icon().performTouchInput { click() }
        rule.runOnIdle { assertEquals(1,selections) }; options().assertDoesNotExist()
    }
    @Test fun scrollingExpandedCardDoesNotCollapseIt() {
        lateinit var scroll: androidx.compose.foundation.ScrollState
        rule.setContent { MaterialTheme {
            scroll=rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Spacer(Modifier.height(200.dp))
                DeviceCard(node,onOpenDesktop={},onOpenTerminal={},onOpenFiles={})
                Spacer(Modifier.height(1400.dp))
            }
        } }
        icon().performTouchInput { click() }; options().assertIsDisplayed()
        icon().performTouchInput { down(center); moveBy(Offset(0f,-120f),delayMillis=180); up() }
        rule.runOnIdle { assertTrue(scroll.value>0) }
        options().assertExists()
    }
    @Test fun allRemoteDestinationsSurviveNavigationStateRestoration() {
        val runtime=RemoteSessionRuntime(ApplicationProvider.getApplicationContext())
        val showing=mutableStateOf(true)
        lateinit var controller: NavHostController
        rule.setContent { if(showing.value) {
            controller=rememberSessionNavController(runtime)
            NavHost(controller,startDestination="devices") {
                composable("devices") { Text("Home fixture") }
                listOf("desktop","terminal","files").forEach { screen ->
                    composable("$screen/remote") { Text("$screen fixture") }
                }
            }
        } }
        listOf("desktop","terminal","files").forEach { screen ->
            rule.runOnIdle { controller.navigate("$screen/remote") }
            rule.runOnIdle { showing.value=false }
            rule.waitForIdle()
            rule.runOnIdle { showing.value=true }
            rule.onNodeWithText("$screen fixture").assertIsDisplayed()
            rule.runOnIdle {
                assertEquals("$screen/remote",controller.currentDestination?.route)
                controller.navigate("devices") { popUpTo("devices") { inclusive=true } }
            }
        }
    }
    @Test fun actualActivityRecreationRetainsClientsAndViewModels() {
        lateinit var before: RemoteSessionRuntime
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { before=ViewModelProvider(it)[RemoteSessionRuntime::class.java] }
            scenario.recreate()
            scenario.onActivity {
                val after=ViewModelProvider(it)[RemoteSessionRuntime::class.java]
                assertSame(before,after)
                assertSame(before.meshClient,after.meshClient)
                assertSame(before.desktopClient,after.desktopClient)
                assertSame(before.terminalClient,after.terminalClient)
                assertSame(before.fileClient,after.fileClient)
                assertSame(before.desktopViewModel,after.desktopViewModel)
                assertSame(before.terminalViewModel,after.terminalViewModel)
                assertSame(before.fileManagerViewModel,after.fileManagerViewModel)
            }
        }
    }
}
