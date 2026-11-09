package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
@RunWith(AndroidJUnit4::class) class ManagementConnectionStatusTest {
    @get:Rule val compose=createComposeRule()
    private val connectedText="Canal de gestión conectado. El servidor valida los permisos."
    private fun stableTransitions(width:Int,fontScale:Float) {
        val ready=mutableStateOf(false);val connecting=mutableStateOf(true);var retries=0
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
            val density=Density(LocalDensity.current.density,fontScale)
            CompositionLocalProvider(LocalConfiguration provides config,LocalDensity provides density) {MaterialTheme {
                Column(Modifier.width(width.dp)) {
                    ManagementConnectionStatus(ready.value,connecting.value,onReconnect={retries++})
                    Box(Modifier.fillMaxWidth().height(24.dp).testTag("account-cards"))
                }
            }}
        }
        compose.onNodeWithText("Conectando con el canal de gestión...").assertIsDisplayed()
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
        compose.onAllNodesWithText(connectedText).assertCountEquals(0)
        val top=compose.onNodeWithTag("account-cards").fetchSemanticsNode().boundsInRoot.top
        val slot=compose.onNodeWithTag("management-reconnect-slot").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {connecting.value=false};compose.waitForIdle()
        compose.onNodeWithText("Canal de gestión desconectado.").assertIsDisplayed()
        compose.onNodeWithTag("management-reconnect").assertIsEnabled().performClick()
        assertEquals(1,retries)
        assertEquals(top,compose.onNodeWithTag("account-cards").fetchSemanticsNode().boundsInRoot.top,0.5f)
        compose.runOnIdle {ready.value=true};compose.waitForIdle()
        compose.onNodeWithText(connectedText).assertIsDisplayed()
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
        compose.onAllNodesWithText("Reconectar").assertCountEquals(0)
        assertEquals(top,compose.onNodeWithTag("account-cards").fetchSemanticsNode().boundsInRoot.top,0.5f)
        val after=compose.onNodeWithTag("management-reconnect-slot").fetchSemanticsNode().boundsInRoot
        assertEquals(slot.width,after.width,0.5f);assertEquals(slot.height,after.height,0.5f)
        assertEquals(1,retries)
    }
    @Test fun cardsDoNotJumpAcrossConnectingDisconnectedAndConnected()=stableTransitions(320,1f)
    @Test fun narrowLayoutAndLargeTextKeepTheSameCardPosition()=stableTransitions(240,1.8f)
    @Test fun reconnectCanRemainDisabledForAnUncertainAccountDeletion() {
        var retries=0
        compose.setContent {MaterialTheme {ManagementConnectionStatus(false,false,reconnectEnabled=false,onReconnect={retries++})}}
        compose.onNodeWithTag("management-reconnect").assertExists().assertIsNotEnabled();assertEquals(0,retries)
    }
    @Test fun channelsStayIndependentAndOperationalWarningIsNotHidden() {
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
            CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {Column {
                ManagementConnectionStatus(true,false,onReconnect={})
                ManagementConnectionStatus(false,false,onReconnect={})
                Text("Resultado sin confirmar",Modifier.testTag("operation-warning"))
            }}}
        }
        compose.onNodeWithText(connectedText).assertIsDisplayed()
        compose.onNodeWithText("Canal de gestión desconectado.").assertIsDisplayed()
        compose.onAllNodesWithTag("management-reconnect").assertCountEquals(1)
        compose.onNodeWithTag("operation-warning").assertIsDisplayed()
    }
    @Test fun englishStatusUsesTheSameComponentAndReadyTakesPrecedence() {
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("en"))}
            CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {ManagementConnectionStatus(true,true,onReconnect={})}}
        }
        compose.onNodeWithText("Management channel connected. The server validates permissions.").assertIsDisplayed()
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
    }
}
