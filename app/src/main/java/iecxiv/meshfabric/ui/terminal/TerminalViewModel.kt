package iecxiv.meshfabric.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.TerminalConnectionStatus
import iecxiv.meshfabric.data.remote.TerminalRelayClient
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class TerminalViewModel(
    val meshClient: MeshCentralClient,
    val terminalClient: TerminalRelayClient
) : ViewModel() {

    internal val manualReconnect = TerminalManualReconnect(meshClient, terminalClient, viewModelScope)

    private val renderers = RetainedTerminalStore<String, TerminalRenderSession>(
        limit = 4, attached = { it.isAttached }, dispose = { it.destroy() }
    )

    internal fun renderTerminal(context: android.content.Context, nodeId: String): TerminalRenderSession {
        val session = terminalClient.getSession(meshClient, nodeId)
        return renderSession(context, session)
    }
    internal fun renderSession(context: android.content.Context, session: iecxiv.meshfabric.data.remote.TerminalSession): TerminalRenderSession {
        return renderers.acquire(session.localSessionId, { it.session === session && !it.isDestroyed }) {
            TerminalRenderSession(context.applicationContext, session, viewModelScope)
        }
    }
    internal fun releaseRenderer(renderer: TerminalRenderSession, token: Long) {
        renderer.detach(token)
        renderers.trim()
    }
    internal fun releaseParent(renderer: TerminalRenderSession, parent: android.widget.FrameLayout) {
        renderer.releaseParent(parent)
        renderers.trim()
    }
    fun clearRendering() { manualReconnect.clear(); renderers.clear() }
    internal fun resetRenderer(expected: TerminalRenderSession): Boolean =
        renderers.removeIf(expected.session.localSessionId, expected)

    fun isConnected(nodeId: String): StateFlow<Boolean> {
        return terminalClient.getSession(meshClient, nodeId).connectionState
    }

    fun connectionStatus(nodeId: String): StateFlow<TerminalConnectionStatus> {
        return terminalClient.getSession(meshClient, nodeId).connectionStatus
    }

    fun incomingDataFlow(nodeId: String): SharedFlow<String> {
        return terminalClient.getSession(meshClient, nodeId).incomingDataFlow
    }

    fun getFullHistory(nodeId: String): String {
        return terminalClient.getFullHistory(nodeId)
    }

    fun startSession(nodeId: String) {
        terminalClient.connect(
            meshClient = meshClient,
            nodeId = nodeId
        )
    }

    fun sendInput(nodeId: String, data: String) {
        terminalClient.sendInput(nodeId, data)
    }

    fun sendControlKey(nodeId: String, char: Char) {
        terminalClient.sendControlKey(nodeId, char)
    }

    fun sendResize(nodeId: String, cols: Int, rows: Int) {
        terminalClient.sendResize(nodeId, cols, rows)
    }

    fun stopSession(nodeId: String) {
        manualReconnect.cancel(nodeId)
        terminalClient.getSession(meshClient, nodeId).disconnectKeepingHistory()
        terminalClient.updateSessionsStatus()
    }

    override fun onCleared() {
        clearRendering()
        super.onCleared()
        terminalClient.disconnectAll()
    }
}
