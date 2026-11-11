package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
class AgentEnrollmentFormTest {
    @get:Rule val compose=createComposeRule()
    private fun show(family:Int=0) {compose.setContent {var o by remember {mutableStateOf(AgentEnrollmentOptions(family=family))};MaterialTheme {Column {AgentEnrollmentSelectors(o,true,true) {o=it}}}}}
    @Test fun platformChoices() {show();compose.onNodeWithTag("enroll-family").performClick();for(f in enrollmentFamilies) compose.onNodeWithTag("enroll-family-"+f).assertExists()}
    @Test fun binaryArchitectures() {show(5);compose.onNodeWithTag("enroll-architecture").performClick();for(a in enrollmentArchitectures.keys) compose.onNodeWithTag("enroll-architecture-"+a).assertExists()}
    @Test fun windowsModes() {show();compose.onNodeWithTag("enroll-mode").performClick();for(m in listOf(0,2,1)) compose.onNodeWithTag("enroll-mode-"+m).assertExists()}
    @Test fun linuxHasNoInteractiveSelector() {show(1);compose.onNodeWithTag("enroll-mode").assertDoesNotExist();compose.onNodeWithTag("enroll-assistant").assertDoesNotExist()}
    @Test fun assistantHasFiveModes() {show(7);compose.onNodeWithTag("enroll-mode").assertDoesNotExist();compose.onNodeWithTag("enroll-assistant").performClick();for(m in listOf(2,3,0,1,4)) compose.onNodeWithTag("enroll-assistant-"+m).assertExists()}
    @Test fun mobileHasNoInstallationMode() {show(6);compose.onNodeWithTag("enroll-mode").assertDoesNotExist();compose.onNodeWithTag("enroll-assistant").assertDoesNotExist()}
}
