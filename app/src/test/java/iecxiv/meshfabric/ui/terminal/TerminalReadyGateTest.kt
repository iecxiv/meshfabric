package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalReadyGateTest {
    @Test fun callbackBeforePageCreationIsRejected() {
        assertFalse(TerminalReadyGate().accept(0L))
    }
    @Test fun firstReadySignalIsAccepted() {
        val gate = TerminalReadyGate()
        assertTrue(gate.accept(gate.begin()))
    }
    @Test fun repeatedReadySignalsAreIgnored() {
        val gate = TerminalReadyGate()
        val token = gate.begin()
        assertTrue(gate.accept(token))
        assertFalse(gate.accept(token))
    }
    @Test fun previousPageCannotInitializeNewPage() {
        val gate = TerminalReadyGate()
        val old = gate.begin()
        val current = gate.begin()
        assertFalse(gate.accept(old))
        assertTrue(gate.accept(current))
    }
    @Test fun disposalRejectsPendingReadySignal() {
        val gate = TerminalReadyGate()
        val token = gate.begin()
        gate.invalidate()
        assertFalse(gate.accept(token))
    }
    @Test fun newPageCanBecomeReadyAfterDisposal() {
        val gate = TerminalReadyGate()
        val old = gate.begin()
        gate.invalidate()
        val current = gate.begin()
        assertTrue(gate.accept(current))
        assertFalse(gate.accept(old))
    }
}
