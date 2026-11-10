package iecxiv.meshfabric.ui.management
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
