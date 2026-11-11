package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
class ManagementConnectionStatusTest {
    @get:Rule val compose=createComposeRule()
    private val connectedText="Canal de gestión conectado. El servidor valida los permisos."
    private fun compact(scale:Float,width:Int) {
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
            val density=Density(LocalDensity.current.density,scale)
            CompositionLocalProvider(LocalConfiguration provides config,LocalDensity provides density) {MaterialTheme {Column(Modifier.width(width.dp)) {
                ManagementConnectionStatus(true,false,onReconnect={})
                Text(connectedText,Modifier.fillMaxWidth().testTag("event-style-reference").padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall)
            }}}
        }
        val actual=compose.onNodeWithTag("management-connection-row").fetchSemanticsNode().boundsInRoot
        val expected=compose.onNodeWithTag("event-style-reference").fetchSemanticsNode().boundsInRoot
        assertEquals(expected.height,actual.height,0.5f)
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
        compose.onNodeWithTag("management-reconnect-slot").assertDoesNotExist()
        compose.onAllNodesWithText("Reconectar").assertCountEquals(0)
    }
    @Test fun connectedUsesOnlyTextAndEventStyleVerticalPadding()=compact(1f,320)
    @Test fun largeTextWrapsNaturallyWithoutHiddenButtonReservation()=compact(1.8f,240)
    @Test fun reconnectOnlyAppearsWhenDisconnected() {
        val ready=mutableStateOf(false);val connecting=mutableStateOf(true);var retries=0
        compose.setContent {MaterialTheme {ManagementConnectionStatus(ready.value,connecting.value,onReconnect={retries++})}}
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
        compose.runOnIdle {connecting.value=false}
        compose.onNodeWithTag("management-reconnect").assertHeightIsAtLeast(48.dp).assertIsEnabled().performClick()
        assertEquals(1,retries)
        compose.runOnIdle {ready.value=true}
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
    }
    @Test fun reconnectCanRemainDisabledForUncertainDeletion() {
        var retries=0
        compose.setContent {MaterialTheme {ManagementConnectionStatus(false,false,false,onReconnect={retries++})}}
        compose.onNodeWithTag("management-reconnect").assertIsNotEnabled();assertEquals(0,retries)
    }
    @Test fun channelsAndOperationalWarningRemainIndependent() {
        compose.setContent {MaterialTheme {Column {ManagementConnectionStatus(true,false,onReconnect={});ManagementConnectionStatus(false,false,onReconnect={});Text("Resultado sin confirmar",Modifier.testTag("operation-warning"))}}}
        compose.onAllNodesWithTag("management-reconnect").assertCountEquals(1)
        compose.onNodeWithTag("operation-warning").assertIsDisplayed()
    }
    @Test fun englishReadyTakesPrecedenceWithoutReconnectSlot() {
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("en"))}
            CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {ManagementConnectionStatus(true,true,onReconnect={})}}
        }
        compose.onNodeWithText("Management channel connected. The server validates permissions.").assertIsDisplayed()
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
    }
}
