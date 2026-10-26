package iecxiv.meshfabric.ui.terminal

import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.TerminalConnectionStatus
import iecxiv.meshfabric.data.remote.TerminalRelayClient
import org.junit.Assert.*
import org.junit.Test

class TerminalViewModelFlowTest {
    @Test fun gettersReturnRealSessionFlowsBeforeStartSession() {
        val mesh=MeshCentralClient()
        val terminal=TerminalRelayClient()
        val viewModel=TerminalViewModel(mesh,terminal)
        val connected=viewModel.isConnected("node//test")
        val status=viewModel.connectionStatus("node//test")
        val incoming=viewModel.incomingDataFlow("node//test")
        val session=terminal.getSession(mesh,"node//test")
        assertSame(session.connectionState,connected)
        assertSame(session.connectionStatus,status)
        assertSame(session.incomingDataFlow,incoming)
    }

    @Test fun previouslyObtainedFlowsObserveSessionUpdates() {
        val mesh=MeshCentralClient()
        val terminal=TerminalRelayClient()
        val viewModel=TerminalViewModel(mesh,terminal)
        val connected=viewModel.isConnected("node//test")
        val status=viewModel.connectionStatus("node//test")
        val session=terminal.getSession(mesh,"node//test")
        session.updateState(true)
        assertTrue(connected.value)
        assertEquals(TerminalConnectionStatus.CONNECTED,status.value)
        session.updateState(false)
        assertFalse(connected.value)
        assertEquals(TerminalConnectionStatus.DISCONNECTED,status.value)
    }

    @Test fun repeatedReadsAndEquivalentIdsShareTheSameFlow() {
        val viewModel=TerminalViewModel(MeshCentralClient(),TerminalRelayClient())
        assertSame(viewModel.isConnected("node//test"),viewModel.isConnected("test"))
        assertSame(viewModel.connectionStatus("node//test"),viewModel.connectionStatus("test"))
        assertSame(viewModel.incomingDataFlow("node//test"),viewModel.incomingDataFlow("test"))
        assertNotSame(viewModel.isConnected("node//test"),viewModel.isConnected("node//other"))
    }
}
