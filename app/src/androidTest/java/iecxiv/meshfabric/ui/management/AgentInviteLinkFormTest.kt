package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class AgentInviteLinkFormTest {
    @get:Rule val compose=createComposeRule()
    private fun show(initial:AgentInviteLinkOptions=AgentInviteLinkOptions(),busy:Boolean=false,onGenerate:()->Unit={}) {compose.setContent {var o by remember {mutableStateOf(initial)};MaterialTheme {AgentInviteLinkForm("Grupo de prueba",o,true,busy,true,{o=it},onGenerate)}}}
    @Test fun sixExpirations() {show();compose.onNodeWithTag("invite-expiry").performClick();for(h in AgentInviteLinkOptions.expirations) compose.onNodeWithTag("invite-expiry-"+h).assertExists()}
    @Test fun sixAgentKinds() {show();compose.onNodeWithTag("invite-agent").performClick();for(a in AgentInviteLinkOptions.agentKinds) compose.onNodeWithTag("invite-agent-"+a).assertExists()}
    @Test fun threeModes() {show();compose.onNodeWithTag("invite-mode").performClick();for(f in AgentInviteLinkOptions.installationModes) compose.onNodeWithTag("invite-mode-"+f).assertExists()}
    @Test fun assistantAndAndroidHideType() {show(AgentInviteLinkOptions(flags=2));for(a in listOf(8,16)) {compose.onNodeWithTag("invite-agent").performClick();compose.onNodeWithTag("invite-agent-"+a).performScrollTo().performClick();compose.onNodeWithTag("invite-mode").assertDoesNotExist()};compose.onNodeWithTag("invite-agent").performClick();compose.onNodeWithTag("invite-agent-1").performScrollTo().performClick();compose.onNodeWithTag("invite-mode").assertTextContains("Segundo plano e interactivo")}
    @Test fun generationRequiresClickAndIsDisabledWhileBusy() {var calls=0;show(busy=true,onGenerate={calls++});compose.onNodeWithTag("invite-generate").assertIsNotEnabled();compose.runOnIdle {assertEquals(0,calls)}}
    @Test fun menuMatchesButtonWidth() {show();val width=compose.onNodeWithTag("invite-expiry").fetchSemanticsNode().boundsInRoot.width;compose.onNodeWithTag("invite-expiry").performClick();val menu=compose.onNodeWithTag("invite-expiry-menu").fetchSemanticsNode().boundsInRoot.width;assertEquals(width,menu,2f)}
}
