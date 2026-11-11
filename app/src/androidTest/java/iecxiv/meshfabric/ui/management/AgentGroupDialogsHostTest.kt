package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
class AgentGroupDialogsHostTest {
    @get:Rule val compose=createComposeRule()
    private fun show(enrollment:Boolean) {compose.setContent {var parent by remember {mutableStateOf(true)};var identity by remember {mutableStateOf("first")};MaterialTheme {Column {TextButton(onClick={parent=false},modifier=Modifier.testTag("hide-parent")) {Text("Hide parent")};TextButton(onClick={identity="second"},modifier=Modifier.testTag("identity")) {Text("Change identity")};AgentGroupDialogsHost(identity,render={target,_->Text(target.mesh+":"+target.enrollment,Modifier.testTag("agent-dialog"))}) {if(parent) {val opener=LocalAgentGroupDialogOpener.current;TextButton(onClick={opener?.invoke(AgentGroupDialog("mesh//group",enrollment))},modifier=Modifier.testTag("open")) {Text("Open")}}}}}}}
    @Test fun inviteSurvivesRemovalOfGroupContent() {show(false);compose.onNodeWithTag("open").performClick();compose.onNodeWithTag("hide-parent").performClick();compose.onNodeWithTag("open").assertDoesNotExist();compose.onNodeWithTag("agent-dialog").assertTextEquals("mesh//group:false")}
    @Test fun enrollmentSurvivesRemovalOfGroupContent() {show(true);compose.onNodeWithTag("open").performClick();compose.onNodeWithTag("hide-parent").performClick();compose.onNodeWithTag("agent-dialog").assertTextEquals("mesh//group:true")}
    @Test fun identityChangeClearsDialog() {show(false);compose.onNodeWithTag("open").performClick();compose.onNodeWithTag("identity").performClick();compose.onNodeWithTag("agent-dialog").assertDoesNotExist()}
}
