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
    private fun compact(scale:Float,width:Int,es:Boolean=true) {
        val ready=mutableStateOf(true);val connecting=mutableStateOf(false);var retries=0
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale(if(es) "es" else "en"))}
            val density=Density(LocalDensity.current.density,scale)
            CompositionLocalProvider(LocalConfiguration provides config,LocalDensity provides density) {MaterialTheme {Column(Modifier.width(width.dp)) {
                ManagementConnectionStatus(ready.value,connecting.value,onReconnect={retries++})
                Box(Modifier.fillMaxWidth().height(20.dp).testTag("following-content"))
                Box(Modifier.fillMaxWidth().testTag("text-sized-reference").padding(vertical=8.dp)) {
                    managementConnectionTexts(es).forEach {Text(it,Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall)}
                }
            }}}
        }
        val initial=compose.onNodeWithTag("management-connection-row").fetchSemanticsNode().boundsInRoot
        val following=compose.onNodeWithTag("following-content").fetchSemanticsNode().boundsInRoot
        val reference=compose.onNodeWithTag("text-sized-reference").fetchSemanticsNode().boundsInRoot
        assertEquals(reference.height,initial.height,0.5f)
        for((r,c) in listOf(false to true,false to false,true to false,true to true)) {
            compose.runOnIdle {ready.value=r;connecting.value=c}
            val actual=compose.onNodeWithTag("management-connection-row").fetchSemanticsNode().boundsInRoot
            val next=compose.onNodeWithTag("following-content").fetchSemanticsNode().boundsInRoot
            assertEquals(initial.height,actual.height,0.5f);assertEquals(initial.width,actual.width,0.5f)
            assertEquals(following.top,next.top,0.5f)
            if(!r && !c) compose.onNodeWithTag("management-reconnect").assertHasClickAction().assertIsEnabled().performClick()
            else compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
        }
        assertEquals(1,retries)
        compose.onNodeWithTag("management-reconnect-slot").assertDoesNotExist()
    }
    @Test fun connectedUsesOnlyTextAndEventStyleVerticalPadding()=compact(1f,320)
    @Test fun largeTextWrapsNaturallyWithoutHiddenButtonReservation()=compact(1.8f,240)
    @Test fun englishTransitionsKeepTheSameBounds()=compact(1f,320,false)
    @Test fun englishLargeFontTransitionsKeepTheSameBounds()=compact(1.8f,240,false)
    @Test fun reconnectOnlyAppearsWhenDisconnected() {
        val ready=mutableStateOf(false);val connecting=mutableStateOf(true);var retries=0
        compose.setContent {MaterialTheme {ManagementConnectionStatus(ready.value,connecting.value,onReconnect={retries++})}}
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
        compose.runOnIdle {connecting.value=false}
        compose.onNodeWithTag("management-reconnect").assertHasClickAction().assertIsEnabled().performClick()
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
