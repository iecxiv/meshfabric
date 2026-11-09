package iecxiv.meshfabric.ui.management
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
@Composable internal fun ProfileManagementConnectionStatus(state:ProfileState,modifier:Modifier=Modifier,onReconnect:()->Unit) {
    ManagementConnectionStatus(ready=state.ready,connecting=!state.ready && state.notice=="connecting",reconnectEnabled=!state.busy,modifier=modifier,onReconnect=onReconnect)
}
