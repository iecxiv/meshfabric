package iecxiv.meshfabric.ui.management

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R

@Composable internal fun ServerSectionTabs(selected:Int,onSelect:(Int)->Unit) {
    ManagementSectionTabs(listOf(
        stringResource(R.string.my_server_general),
        stringResource(R.string.my_server_statistics),
        stringResource(R.string.my_server_console),
        stringResource(R.string.my_server_trace)
    ),selected,onSelect)
}
