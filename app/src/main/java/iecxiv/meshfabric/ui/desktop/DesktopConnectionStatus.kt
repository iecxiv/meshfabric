package iecxiv.meshfabric.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R

@Composable internal fun desktopConnectionStatusText(
    viewModel: RemoteDesktopViewModel,
    nodeId: String,
    connected: Boolean
): String {
    val session = remember(viewModel, nodeId) {
        viewModel.desktopClient.getSession(viewModel.meshClient, nodeId)
    }
    val viewerCount by session.viewerCount.collectAsState()
    if (!connected) return stringResource(R.string.status_connecting)
    val connectedText = stringResource(R.string.status_connected)
    val count = viewerCount
    return if (count != null && count > 1) {
        stringResource(R.string.desktop_connected_viewers, connectedText, count)
    } else connectedText
}
