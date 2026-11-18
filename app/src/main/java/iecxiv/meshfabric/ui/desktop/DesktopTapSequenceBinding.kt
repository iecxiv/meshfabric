package iecxiv.meshfabric.ui.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import okio.ByteString

internal fun createDesktopTapSequence(
    scope: CoroutineScope,
    viewModel: RemoteDesktopViewModel,
    nodeId: String,
    ready: () -> Boolean
): DesktopTapSequence {
    val session = viewModel.desktopClient.getSession(viewModel.meshClient, nodeId)
    fun current(): Any? = synchronized(session) {
        if (ready() && !viewModel.meshClient.isUserLogout && viewModel.meshClient.connectionState.value &&
            session.isConnected() && !session.isUserDisconnect) session.webSocket else null
    }
    val sequence = DesktopTapSequence(
        binding = ::current,
        schedule = { wait, fire ->
            val job = scope.launch { delay(wait); fire() }
            val cancel: () -> Unit = { job.cancel() }
            cancel
        },
        emit = { origin, target, count ->
            synchronized(session) {
                val socket = origin as? WebSocket
                if (socket == null || current() !== socket) false
                else desktopTapPackets(target, count).all { socket.send(ByteString.of(*it)) }
            }
        }
    )
    viewModel.desktopTapSequences.put(nodeId, sequence)?.cancel()
    return sequence
}
