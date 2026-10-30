package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalHistoryPanelTest {
    @Test fun initialOrRestoredRecordDoesNotAutoOpen() {
        assertFalse(TerminalHistoryPanelGate().consume("record", 0))
        assertFalse(terminalHistoryBarVisible(false, false, false))
    }
    @Test fun incidentOpensOnlyOnceAcrossScreenVisits() {
        val gate = TerminalHistoryPanelGate()
        assertTrue(gate.consume("record", 1))
        assertFalse(gate.consume("record", 1))
        assertFalse(gate.consume("record", 0))
    }
    @Test fun laterIncidentCanOpenAgain() {
        val gate = TerminalHistoryPanelGate()
        assertTrue(gate.consume("record", 1))
        assertTrue(gate.consume("record", 2))
        assertFalse(gate.consume("record", 1))
    }
    @Test fun sessionsHaveIndependentIncidentOwnership() {
        val gate = TerminalHistoryPanelGate()
        assertTrue(gate.consume("first", 1))
        assertTrue(gate.consume("second", 1))
        assertFalse(gate.consume("first", 1))
    }
    @Test fun cleanupResetsIncidentOwnership() {
        val gate = TerminalHistoryPanelGate()
        assertTrue(gate.consume("record", 1))
        gate.clear()
        assertTrue(gate.consume("record", 1))
    }
    @Test fun manualHistoryAndReconnectKeepPrimaryActionVisible() {
        assertTrue(terminalHistoryBarVisible(false, true, false))
        assertTrue(terminalHistoryBarVisible(false, false, true))
        assertTrue(terminalHistoryBarVisible(true, false, false))
    }
}
