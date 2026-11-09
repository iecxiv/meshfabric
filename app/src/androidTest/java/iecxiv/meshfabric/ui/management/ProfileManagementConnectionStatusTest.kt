package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale
class ProfileManagementConnectionStatusTest {
    @get:Rule val compose=createComposeRule()
    @Test fun profileChannelStatesStayIndependentAndReconnectOnlyOnClick() {
        val state=mutableStateOf(ProfileState(notice="connecting"));var retries=0
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
            CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                ProfileManagementConnectionStatus(state.value,onReconnect={retries++})
            }}
        }
        compose.onNodeWithText("Conectando con el canal de gestión...").assertIsDisplayed()
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
        compose.runOnIdle {state.value=ProfileState(ready=true,notice="readFailed",readError=true)}
        compose.onNodeWithText("Canal de gestión conectado. El servidor valida los permisos.").assertIsDisplayed()
        compose.onNodeWithTag("management-reconnect").assertDoesNotExist()
        compose.runOnIdle {state.value=ProfileState(notice="unknown")}
        compose.onNodeWithText("Canal de gestión desconectado.").assertIsDisplayed()
        compose.onNodeWithTag("management-reconnect").assertHeightIsAtLeast(48.dp).assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(1,retries)}
    }
    @Test fun busyStateDoesNotPermitReconnect() {
        compose.setContent {MaterialTheme {ProfileManagementConnectionStatus(ProfileState(busy=true),onReconnect={error("Unexpected reconnect")})}}
        compose.onNodeWithTag("management-reconnect").assertIsNotEnabled()
    }
}
