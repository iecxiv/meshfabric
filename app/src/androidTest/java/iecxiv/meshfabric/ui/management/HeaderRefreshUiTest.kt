package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class HeaderRefreshUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun eventRefreshPrecedesSearchAndKeepsQuery() {
        var clicks=0
        compose.setContent {MaterialTheme {run {var query by remember {mutableStateOf("needle")};ServerEventsSearchHeader(query,{query=it},true,{},header={actions->Row {actions()}},refreshEnabled=true,onRefresh={clicks++})}}}
        val refresh=compose.onNodeWithTag("log-events-refresh").fetchSemanticsNode().boundsInRoot
        val search=compose.onNodeWithTag("log-events-search-toggle").fetchSemanticsNode().boundsInRoot
        assertTrue(refresh.left<search.left);compose.onNodeWithTag("log-events-refresh").performClick();compose.runOnIdle {assertEquals(1,clicks)};compose.onNodeWithTag("log-events-search").assertTextContains("needle")
    }
    @Test fun eventHeaderRefreshHonorsDisabledState() {
        compose.setContent {MaterialTheme {ServerEventsSearchHeader("",{},false,{},header={actions->Row {actions()}},refreshEnabled=false,onRefresh={fail("Disabled refresh must not dispatch")})}}
        compose.onNodeWithTag("log-events-refresh").assertIsNotEnabled()
    }
    @Test fun managementRefreshAppearsOnlyOnTheThreeDirectoryTabs() {
        val tab=mutableIntStateOf(0);var clicks=0
        compose.setContent {MaterialTheme {ProfileRefreshHeader(tab.intValue,ProfileState(ready=true),header={actions->Row {actions()}},onRefresh={clicks++})}}
        compose.onNodeWithTag("profile-header-refresh").assertDoesNotExist()
        for(index in 1..3) {compose.runOnIdle {tab.intValue=index};compose.onNodeWithTag("profile-header-refresh").assertIsEnabled().performClick()}
        compose.runOnIdle {assertEquals(3,clicks);tab.intValue=0};compose.onNodeWithTag("profile-header-refresh").assertDoesNotExist()
    }
    @Test fun managementHeaderBlocksBusyAndDirectoryLoading() {
        val state=mutableStateOf(ProfileState(ready=true,busy=true))
        compose.setContent {MaterialTheme {ProfileRefreshHeader(3,state.value,header={actions->Row {actions()}},onRefresh={fail("Blocked refresh")})}}
        compose.onNodeWithTag("profile-header-refresh").assertIsNotEnabled()
        compose.runOnIdle {state.value=ProfileState(ready=true,userGroupPhase="loading")};compose.onNodeWithTag("profile-header-refresh").assertIsNotEnabled()
    }
    @Test fun eventNoticesDisappearWithoutLosingRecordsOrDetails() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val row=ServerEventRecord("manual","entry","operator","node//device","surface",1L,meshId="mesh//agent",groupName="agent",inventoryResolved=true)
        compose.setContent {MaterialTheme {ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.READY,List(60) {row}),"",{},true,{},selection=ServerEventsQuery(limit=60))}}
        compose.onNodeWithText(context.getString(R.string.log_limit)).assertDoesNotExist();compose.onNodeWithText(context.getString(R.string.log_detail_inventory)).assertDoesNotExist()
        compose.onNodeWithTag("log-event-row-0").performClick();compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface")
        compose.onNodeWithText(context.getString(R.string.log_detail_inventory)).assertDoesNotExist();compose.onNodeWithText("node//device").assertExists();compose.onNodeWithText("mesh//agent").assertExists()
    }
    @Test fun eventsPanelHasNoRefreshButtonOrManagementChannelRow() {
        compose.setContent {MaterialTheme {ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.READY),"",{},true,{})}}
        compose.onNodeWithTag("log-events-refresh").assertDoesNotExist();compose.onNodeWithTag("management-connection-row").assertDoesNotExist()
    }
}
