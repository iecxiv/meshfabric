package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
class ServerDialogContinuityUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun realResultDialogKeepsWrapSettingThroughPauseAndResume() {
        val raw=mutableStateOf("{}");val identity=mutableStateOf("same-session")
        compose.setContent {MaterialTheme {
            val result=rememberServerReadDialog(raw.value,if(raw.value.isEmpty()) "" else "serverconfig",identity.value)
            key(identity.value) {result.value?.let {opened->ServerReadResultDialog(opened.raw,opened.action) {result.value=null;raw.value=""}}}
        }}
        compose.onNode(isToggleable()).assertIsOn().performClick().assertIsOff()
        compose.runOnIdle {raw.value=""}
        compose.onNode(isToggleable()).assertIsOff()
        compose.runOnIdle {raw.value="{}"}
        compose.onNode(isToggleable()).assertIsOff()
    }
    @Test fun identityChangeClearsResultAndExplicitCloseStaysClosed() {
        val raw=mutableStateOf("{}");val identity=mutableStateOf("a")
        compose.setContent {MaterialTheme {
            val result=rememberServerReadDialog(raw.value,if(raw.value.isEmpty()) "" else "serverconfig",identity.value)
            key(identity.value) {result.value?.let {opened->ServerReadResultDialog(opened.raw,opened.action) {result.value=null;raw.value=""}}}
        }}
        compose.onNode(isToggleable()).assertExists()
        compose.runOnIdle {raw.value="";identity.value="b"}
        compose.onNode(isToggleable()).assertDoesNotExist()
        compose.runOnIdle {raw.value="{}"}
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithText("A+").performClick()
        compose.onNode(hasText("Cerrar") or hasText("Close")).performClick()
        compose.onNode(isToggleable()).assertDoesNotExist()
    }
}
