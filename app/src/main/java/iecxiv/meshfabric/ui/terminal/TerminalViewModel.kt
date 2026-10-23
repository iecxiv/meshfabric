package iecxiv.meshfabric.ui.terminal

import androidx.lifecycle.ViewModel
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.TerminalConnectionStatus
import iecxiv.meshfabric.data.remote.TerminalRelayClient
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class TerminalViewModel(
    val meshClient: MeshCentralClient,
    val terminalClient: TerminalRelayClient
) : ViewModel() {

    fun isConnected(nodeId: String): StateFlow<Boolean> {
        return terminalClient.getConnectionState(nodeId)
    }

    fun connectionStatus(nodeId: String): StateFlow<TerminalConnectionStatus> {
        return terminalClient.getConnectionStatus(nodeId)
    }

    fun incomingDataFlow(nodeId: String): SharedFlow<String> {
        return terminalClient.getIncomingDataFlow(nodeId)
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
        terminalClient.disconnectSession(nodeId)
    }

    override fun onCleared() {
        super.onCleared()
        terminalClient.disconnectAll()
    }
}
