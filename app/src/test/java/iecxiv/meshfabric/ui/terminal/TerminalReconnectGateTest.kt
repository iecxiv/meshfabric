package iecxiv.meshfabric.ui.terminal
import org.junit.Assert.*
import org.junit.Test
class TerminalReconnectGateTest {
    @Test fun oneAttemptPerNode() {
        val gate = TerminalReconnectGate()
        assertNotNull(gate.begin("a"))
        assertNull(gate.begin("a"))
        assertNotNull(gate.begin("b"))
    }
    @Test fun cancelledCallbackCannotCompleteNewAttempt() {
        val gate = TerminalReconnectGate()
        val old = gate.begin("a")!!
        assertTrue(gate.finish("a", old))
        val next = gate.begin("a")!!
        assertFalse(gate.finish("a", old))
        assertTrue(gate.current("a", next))
    }
    @Test fun terminalOutcomeIsAcceptedOnce() {
        val gate = TerminalReconnectGate()
        val token = gate.begin("a")!!
        assertTrue(gate.finish("a", token))
        assertFalse(gate.finish("a", token))
    }
    @Test fun archiveReplayStartsAtRetainedHeadButLiveGapsRemainErrors() {
        val journal = iecxiv.meshfabric.data.remote.TerminalOutputJournal(4)
        journal.append("123456")
        assertEquals("3456", journal.readHistory(null).text)
        assertFalse(journal.readHistory(null).gap)
        assertTrue(journal.read(null).gap)
        assertTrue(journal.read(iecxiv.meshfabric.data.remote.TerminalOutputCursor(0, 0)).gap)
    }
}
