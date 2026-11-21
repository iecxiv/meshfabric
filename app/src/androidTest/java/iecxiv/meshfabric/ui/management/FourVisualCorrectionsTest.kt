package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.devices.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class FourVisualCorrectionsTest {
    @get:Rule val rule=createComposeRule()
    @Test fun groupTabsRemainPinnedWhileSwitchingAndScrolling() {
        val tab=mutableIntStateOf(0)
        rule.setContent {MaterialTheme {CompositionLocalProvider(LocalPinnedSubviewHeader provides {
            Box(Modifier.fillMaxWidth().testTag("group_tabs")) {DeviceGroupDetailsTabs(tab.intValue) {tab.intValue=it}}
        }) {ProfileUserDetailSurface(true,{},title={Text("Group")},text={
            assertNull(LocalPinnedSubviewHeader.current)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {repeat(if(tab.intValue==1) 80 else 1) {Text("Line $it",Modifier.testTag("group_line_$it"))}}
        },confirmButton={TextButton(onClick={},modifier=Modifier.testTag("group_footer")) {Text("Refresh")}},dismissButton={})}}}
        val top=rule.onNodeWithTag("group_tabs").fetchSemanticsNode().boundsInRoot.top
        val footer=rule.onNodeWithTag("group_footer").fetchSemanticsNode().boundsInRoot.top
        rule.onNode(hasText("Devices") or hasText("Dispositivos")).performClick()
        rule.onNodeWithTag("group_line_79").performScrollTo().assertIsDisplayed()
        assertEquals(top,rule.onNodeWithTag("group_tabs").fetchSemanticsNode().boundsInRoot.top,2f)
        rule.onNode(hasText("Access") or hasText("Accesos")).performClick()
        assertEquals(top,rule.onNodeWithTag("group_tabs").fetchSemanticsNode().boundsInRoot.top,2f)
        rule.onNode(hasText("Summary") or hasText("Resumen")).performClick()
        assertEquals(top,rule.onNodeWithTag("group_tabs").fetchSemanticsNode().boundsInRoot.top,2f)
        assertEquals(footer,rule.onNodeWithTag("group_footer").fetchSemanticsNode().boundsInRoot.top,2f)
    }
    @Test fun eventTitleAndIconShareOneRowWithStandardTypography() {
        val event=ServerEventRecord("manual","Event","user","node//a","Device",1800000000000L)
        rule.setContent {MaterialTheme {ServerEventDetailsDialog(event) {}}}
        rule.onNode(isDialog()).assertExists()
        val icon=rule.onNodeWithTag("event_details_title_icon").fetchSemanticsNode().boundsInRoot
        val title=rule.onNodeWithTag("event_details_title_text").fetchSemanticsNode().boundsInRoot
        assertEquals(icon.center.y,title.center.y,2f)
        assertTrue(icon.right<=title.left)
        val label=InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.log_detail_title)
        val layouts=mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertEquals(18f,layouts.single().layoutInput.style.fontSize.value,0.01f)
        assertEquals(FontWeight.Bold,layouts.single().layoutInput.style.fontWeight)
    }
    @Test fun connectedConsoleHasNoInvisibleButtonOrReservedRowHeight() {
        val phase=mutableStateOf("ready");var reconnect=0
        rule.setContent {MaterialTheme {DeviceConsoleChannelStatus(phase.value,true) {reconnect++}}}
        val row=rule.onNodeWithTag("agent_console_status_row").fetchSemanticsNode().boundsInRoot
        val text=rule.onNodeWithTag("agent_console_status").fetchSemanticsNode().boundsInRoot
        assertEquals(text.height,row.height,2f)
        rule.onNodeWithTag("agent_console_reconnect").assertDoesNotExist()
        rule.runOnIdle {phase.value="closed"}
        rule.onNodeWithTag("agent_console_reconnect").assertIsDisplayed().performClick()
        rule.runOnIdle {assertEquals(1,reconnect)}
    }
    @Test fun copyHintAppearsBelowDeviceDetailsDivider() {
        rule.setContent {MaterialTheme {CompositionLocalProvider(LocalDeviceDetailsInline provides true) {DeviceDetailsDialog(MeshNode(id="node//a",name="Device")) {}}}}
        val divider=rule.onNodeWithTag("device_details_header_divider").fetchSemanticsNode().boundsInRoot
        val hint=rule.onNodeWithTag("device_details_copy_hint").fetchSemanticsNode().boundsInRoot
        assertTrue(hint.top>=divider.bottom)
        rule.onNodeWithTag("device_details_copy_hint").assertIsDisplayed()
    }
}
