package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.management.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
class DeviceEventsPresentationTest {
    @get:Rule val rule=createComposeRule()
    private val node=MeshNode(id="node//events",name="Device")
    private val selection=ServerEventsQuery(nodeId=node.id)
    private val ready=ServerEventsSnapshot(ServerEventsPhase.READY,listOf(ServerEventRecord("manual","Event","user",node.id,node.name,1800000000000L)),selection=selection)
    @Composable private fun Content(query:String,onQuery:(String)->Unit,open:Boolean,onOpen:(Boolean)->Unit) {
        DeviceEventsContent(node,true,query,onQuery,open,onOpen,ready,true,selection,{},ServerEventInventory(),{}, {})
    }
    @Test fun searchStartsClosedOpensAndCloseClearsQuery() {
        val query=mutableStateOf("");val open=mutableStateOf(false)
        rule.setContent {MaterialTheme {Content(query.value,{query.value=it},open.value,{open.value=it})}}
        rule.onNodeWithTag("log-events-search").assertDoesNotExist()
        rule.onNodeWithTag("log-events-search-toggle").performClick()
        rule.onNodeWithTag("log-events-search").assertIsFocused().performTextInput("Event")
        rule.onNodeWithTag("log-events-search-toggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("log-events-search").assertDoesNotExist()
        rule.runOnIdle {assertFalse(open.value);assertEquals("",query.value)}
        rule.onNode(isDialog()).assertDoesNotExist()
    }
    @Test fun eventListEndsAtFooterDividerWithoutExtraBottomPadding() {
        rule.setContent {MaterialTheme {Content("",{},false,{})}}
        val list=rule.onNodeWithTag("log-events-list").fetchSemanticsNode().boundsInRoot
        val divider=rule.onNodeWithTag("device_events_footer_divider").fetchSemanticsNode().boundsInRoot
        assertTrue("Extra gap above footer: ${divider.top-list.bottom}",abs(divider.top-list.bottom)<=1f)
    }
    @Test fun searchFieldDimensionsMatchGlobalSearchHeader() {
        val device=mutableStateOf(false)
        rule.setContent {MaterialTheme {if(device.value) Content("",{},true,{}) else Column(Modifier.fillMaxSize()) {ServerEventsSearchHeader("",{},true,{},header={actions->Row {actions()}})}}}
        val reference=rule.onNodeWithTag("log-events-search").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {device.value=true}
        val actual=rule.onNodeWithTag("log-events-search").fetchSemanticsNode().boundsInRoot
        assertEquals(reference.width,actual.width,1f);assertEquals(reference.height,actual.height,1f)
    }
}
