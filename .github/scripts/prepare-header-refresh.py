FILES={
'app/src/main/java/iecxiv/meshfabric/ui/management/HeaderRefresh.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
internal fun eventsHeaderRefreshEnabled(connected:Boolean,actorPresent:Boolean,currentSelection:Boolean,phase:ServerEventsPhase)=connected && actorPresent && currentSelection && phase!=ServerEventsPhase.LOADING
internal fun profileHeaderRefreshAction(tab:Int,s:ProfileState):UserGroupsRefreshAction=when {
    tab !in 1..3->UserGroupsRefreshAction.NONE
    tab==3->userGroupsRefreshAction(s.ready,s.busy,s.loading,s.userGroupPhase,s.notice)
    s.busy || s.loading->UserGroupsRefreshAction.NONE
    !s.ready->UserGroupsRefreshAction.RECONNECT
    else->UserGroupsRefreshAction.REFRESH
}
internal fun performProfileHeaderRefresh(tab:Int,session:ProfileSession) {
    when(profileHeaderRefreshAction(tab,session.state.value)) {
        UserGroupsRefreshAction.NONE->Unit
        UserGroupsRefreshAction.RECONNECT->session.start()
        UserGroupsRefreshAction.REFRESH->{session.refresh();if(tab==3) session.loadUserGroupDirectory()}
    }
}
@Composable internal fun HeaderRefreshButton(enabled:Boolean,tag:String,onRefresh:()->Unit) {
    IconButton(onClick=onRefresh,enabled=enabled,modifier=Modifier.size(38.dp).testTag(tag)) {
        Icon(Icons.Default.Refresh,stringResource(R.string.log_refresh),Modifier.size(20.dp))
    }
}
@Composable internal fun ProfileRefreshHeader(tab:Int,state:ProfileState,header:@Composable (@Composable ()->Unit)->Unit,onRefresh:()->Unit) {
    header {if(tab in 1..3) HeaderRefreshButton(profileHeaderRefreshAction(tab,state)!=UserGroupsRefreshAction.NONE,"profile-header-refresh",onRefresh)}
}
""",
'app/src/test/java/iecxiv/meshfabric/ui/management/HeaderRefreshPolicyTest.kt':r"""package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class HeaderRefreshPolicyTest {
    @Test fun myAccountAndUnknownTabsHaveNoRefresh() {
        for(tab in listOf(-1,0,4)) assertEquals(UserGroupsRefreshAction.NONE,profileHeaderRefreshAction(tab,ProfileState(ready=true)))
    }
    @Test fun deviceGroupsAndUsersPreserveRefreshAndReconnect() {
        for(tab in 1..2) {assertEquals(UserGroupsRefreshAction.REFRESH,profileHeaderRefreshAction(tab,ProfileState(ready=true)));assertEquals(UserGroupsRefreshAction.RECONNECT,profileHeaderRefreshAction(tab,ProfileState()))}
    }
    @Test fun busyOrLoadingBlocksEveryManagementTab() {
        for(tab in 1..3) {assertEquals(UserGroupsRefreshAction.NONE,profileHeaderRefreshAction(tab,ProfileState(ready=true,busy=true)));assertEquals(UserGroupsRefreshAction.NONE,profileHeaderRefreshAction(tab,ProfileState(ready=true,loading=true)))}
    }
    @Test fun userGroupsPreserveDirectorySpecificGating() {
        assertEquals(UserGroupsRefreshAction.NONE,profileHeaderRefreshAction(3,ProfileState(ready=true,userGroupPhase="loading")))
        assertEquals(UserGroupsRefreshAction.NONE,profileHeaderRefreshAction(3,ProfileState(notice="connecting")))
        assertEquals(UserGroupsRefreshAction.REFRESH,profileHeaderRefreshAction(3,ProfileState(ready=true,userGroupPhase="failed")))
        assertEquals(UserGroupsRefreshAction.RECONNECT,profileHeaderRefreshAction(3,ProfileState()))
    }
    @Test fun eventsRequireConnectionActorAndCurrentSelection() {
        assertTrue(eventsHeaderRefreshEnabled(true,true,true,ServerEventsPhase.READY))
        assertFalse(eventsHeaderRefreshEnabled(false,true,true,ServerEventsPhase.READY))
        assertFalse(eventsHeaderRefreshEnabled(true,false,true,ServerEventsPhase.READY))
        assertFalse(eventsHeaderRefreshEnabled(true,true,false,ServerEventsPhase.READY))
    }
    @Test fun eventsCannotRefreshWhileLoadingButCanRetryErrors() {
        assertFalse(eventsHeaderRefreshEnabled(true,true,true,ServerEventsPhase.LOADING))
        for(phase in listOf(ServerEventsPhase.ERROR,ServerEventsPhase.TIMEOUT,ServerEventsPhase.INVALID)) assertTrue(eventsHeaderRefreshEnabled(true,true,true,phase))
    }
}
""",
'app/src/androidTest/java/iecxiv/meshfabric/ui/management/HeaderRefreshUiTest.kt':r"""package iecxiv.meshfabric.ui.management
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
        compose.onNodeWithTag("log-event-row-0").performClick();compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface(agent)")
        compose.onNodeWithText(context.getString(R.string.log_detail_inventory)).assertDoesNotExist();compose.onNodeWithText("node//device").assertExists();compose.onNodeWithText("mesh//agent").assertExists()
    }
    @Test fun eventsPanelHasNoRefreshButtonOrManagementChannelRow() {
        compose.setContent {MaterialTheme {ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.READY),"",{},true,{})}}
        compose.onNodeWithTag("log-events-refresh").assertDoesNotExist();compose.onNodeWithTag("management-connection-row").assertDoesNotExist()
    }
}
"""
}
from pathlib import Path
import json,subprocess
changed=set()
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management')
def edit(path,old,new):
    p=Path(path);s=p.read_text();assert s.count(old)==1,(str(p),old);p.write_text(s.replace(old,new,1));changed.add(str(p))
for name,sha in [('LogScreen.kt','7d4c099fbae9696656d5882ee14234c92082634c'),('EventsHeaderPresentation.kt','456aea86ba53ed662459b23328ea22bf57a6fb24'),('ProfileScreen.kt','3a820b43faed99b30aa4d53e5a8490c38148b409'),('MeshFabricHome.kt','0ef6f3022764d8b2175b8cef5b475d4ee45c0261'),('UserGroupsDirectoryPane.kt','8a9a5c9f79a461ac529357d228c958f903b905ec'),('ProfileUserGroupsFeedback.kt','ece4e66dbe8661d7e977cfa6c2e9dd7dddcae43d')]:
    assert subprocess.check_output(['git','hash-object',str(root/name)],text=True).strip()==sha,name
for path,content in FILES.items():
    p=Path(path);assert not p.exists(),path;p.write_text(content);changed.add(path)
screen=root/'LogScreen.kt';header=root/'EventsHeaderPresentation.kt';profile=root/'ProfileScreen.kt';home=root/'MeshFabricHome.kt';groups=root/'UserGroupsDirectoryPane.kt'
edit(screen,'tabs=header)})','tabs=header,refreshEnabled=eventsHeaderRefreshEnabled(connected,!actor?.id.isNullOrBlank(),state.selection==selection,state.phase),onRefresh={reader.load(selection)})})')
edit(screen,'        TextButton(onClick=onRefresh,enabled=enabled && state.phase!=ServerEventsPhase.LOADING,modifier=Modifier.testTag("log-events-refresh")) {Text(stringResource(R.string.log_refresh))}\n','')
edit(screen,'            if(selection.limit!=null && state.records.size==selection.limit) Text(stringResource(R.string.log_limit),style=MaterialTheme.typography.bodySmall)\n','')
edit(screen,'                    if(row.inventoryResolved) Text(stringResource(R.string.log_detail_inventory),style=MaterialTheme.typography.bodySmall)\n','')
edit(root/'ServerEventDetails.kt','            if(row.inventoryResolved) Text(stringResource(R.string.log_detail_inventory),style=MaterialTheme.typography.bodySmall)\n','')
edit(header,'import androidx.compose.ui.Modifier','import androidx.compose.ui.Modifier\nimport androidx.compose.ui.Alignment')
edit(header,'tabs:@Composable ()->Unit={}) {','tabs:@Composable ()->Unit={},refreshEnabled:Boolean=false,onRefresh:(()->Unit)?=null) {')
edit(header,'        header {\n            IconButton','        header {\n            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy((-6).dp)) {\n            if(onRefresh!=null) HeaderRefreshButton(refreshEnabled,"log-events-refresh",onRefresh)\n            IconButton')
edit(header,'        }\n        tabs()','        }}\n        tabs()')
edit(profile,'ProfileScreen(source:MeshCentralClient,modifier:Modifier=Modifier)','ProfileScreen(source:MeshCentralClient,modifier:Modifier=Modifier,header:@Composable (@Composable ()->Unit)->Unit={})')
edit(profile,'Column(modifier) {\nManagementSectionTabs','Column(modifier) {\nProfileRefreshHeader(tab,state,header) {performProfileHeaderRefresh(tab,session)}\nManagementSectionTabs')
edit(profile,'TextButton(enabled=!state.loading && !state.busy,onClick={ if(state.ready) session.refresh() else session.start() }) { Text(t("Actualizar","Refresh")) }\n','')
edit(home,'topBar={ if(page in 2..4) MeshFabricAppBar(viewModel,onOpenSettings) }','topBar={ if(page==2 || page==4) MeshFabricAppBar(viewModel,onOpenSettings) }')
edit(home,'ProfileScreen(viewModel.meshClient,Modifier.fillMaxSize())','ProfileScreen(viewModel.meshClient,Modifier.fillMaxSize(),header={actions->MeshFabricAppBar(viewModel,onOpenSettings,actions={actions()})})')
edit(groups,'val refreshAction=userGroupsRefreshAction(s.ready,s.busy,s.loading,s.userGroupPhase,s.notice)\n','')
edit(groups,'TextButton(enabled=refreshAction!=UserGroupsRefreshAction.NONE,onClick={when(refreshAction) {UserGroupsRefreshAction.RECONNECT->session.start();UserGroupsRefreshAction.REFRESH->{session.refresh();session.loadUserGroupDirectory()};UserGroupsRefreshAction.NONE->Unit}}) {Text(if(s.ready) t("Actualizar","Refresh") else t("Reconectar","Reconnect"))}\n','')
tests=Path('app/src/androidTest/java/iecxiv/meshfabric/ui/management')
edit(tests/'EventsHeaderDatesTest.kt','header={actions->Row {actions()}})','header={actions->Row {actions()}},refreshEnabled=eventsHeaderRefreshEnabled(true,true,true,state.phase),onRefresh={})')
edit(tests/'LogScreenTest.kt','val state=mutableStateOf(ServerEventsSnapshot(ServerEventsPhase.LOADING))\n        compose.setContent {MaterialTheme {ServerEventsPanel(state.value,"",{},true,{})}}','val state=mutableStateOf(ServerEventsSnapshot(ServerEventsPhase.LOADING))\n        compose.setContent {MaterialTheme {SearchableEventsTestPanel(state.value)}}')
edit(tests/'UserGroupsActionsCardTest.kt','compose.onNode(hasText("Reconectar") and hasAnyAncestor(hasTestTag("user-groups-server-actions"))).assertExists()','compose.onNode(hasText("Reconectar") and hasAnyAncestor(hasTestTag("user-groups-server-actions"))).assertDoesNotExist()\n            compose.onNode(hasText("Actualizar") and hasAnyAncestor(hasTestTag("user-groups-server-actions"))).assertDoesNotExist()')
p=Path('docs/log.md');s=p.read_text();old='fallback is labelled as connection inventory, not historical evidence.';assert s.count(old)==1
s=s.replace(old,'fallback origin is tracked internally, not historical evidence; its explanatory UI notice is suppressed by request.')
s+='\nEvents refresh is an icon immediately before search in the app bar, not a content button. It loads the currently selected type/count without changing the active query, and is disabled while loading, disconnected, without an actor or with stale selection. The inventory-origin and finite-query-ceiling notices are hidden in cards/details; parsing, identity resolution, requested limits and unlimited behavior are unchanged. No management-channel status is added to Events.\nManagement device groups, users and user groups move their existing refresh/reconnect action from Server actions into the app bar owned by the same ProfileScreen session. My account has no refresh action. User groups preserve directory-specific gating and explicit loadUserGroupDirectory after refresh. Existing connection-status widgets, spacing constants, dialogs and Activity app bar are not modified.\n'
p.write_text(s);changed.add(str(p))
assert len(changed)==13,changed
subprocess.run(['git','add','--',*sorted(changed)],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==changed
for name in ['ManagementConnectionStatus.kt','ManagementConnectionSpacing.kt','ProfileManagementConnectionStatus.kt','ProfileSession.kt']:
    assert not subprocess.check_output(['git','diff','--cached','--name-only','--',str(root/name)],text=True).strip()
Path('validation/header-refresh-expected.json').write_text(json.dumps(sorted(changed),indent=2)+'\n')
