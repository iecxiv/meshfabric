package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.components.AppRefreshIconButton
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
    AppRefreshIconButton(onRefresh=onRefresh,enabled=enabled,modifier=Modifier.testTag(tag),contentDescription=stringResource(R.string.log_refresh))
}
@Composable internal fun ProfileRefreshHeader(tab:Int,state:ProfileState,header:@Composable (@Composable ()->Unit)->Unit,onRefresh:()->Unit) {
    header {if(tab in 1..3) HeaderRefreshButton(profileHeaderRefreshAction(tab,state)!=UserGroupsRefreshAction.NONE,"profile-header-refresh",onRefresh)}
}
