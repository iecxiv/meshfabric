package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ServerConsoleSubmissionGateTest {
    @Test fun acceptsEachIdAtMostOnce() {
        val gate=ServerConsoleSubmissionGate()
        assertTrue(gate.claim(1,"help",true))
        assertFalse(gate.claim(1,"help",true))
        assertFalse(gate.claim(0,"help",true))
        assertTrue(gate.claim(2,"info",true))
    }
    @Test fun rejectedIdsCannotBeReplayedAfterPermissionsChange() {
        val gate=ServerConsoleSubmissionGate()
        assertFalse(gate.claim(1,"help",false))
        assertFalse(gate.claim(1,"help",true))
        assertTrue(gate.claim(2,"help",true))
    }
    @Test fun rejectsBlankOversizedAndControlBearingCommands() {
        listOf("","  ","x".repeat(1001),"help\ninfo","help\r","help\tinfo","\u001b[31mhelp","a\u0085b","a\u2028b","a\u2029b").forEach {
            assertFalse(validServerConsoleCommand(it))
        }
        assertTrue(validServerConsoleCommand("  help  "))
        assertTrue(validServerConsoleCommand("x".repeat(1000)))
    }
    @Test fun countsUtf16UnitsWithoutSplittingUnicode() {
        val face="\uD83D\uDE00"
        assertTrue(validServerConsoleCommand(face.repeat(500)))
        assertFalse(validServerConsoleCommand(face.repeat(501)))
    }
    @Test fun closingPermanentlyRejectsLateCommands() {
        val gate=ServerConsoleSubmissionGate()
        gate.close()
        assertFalse(gate.claim(1,"help",true))
        assertTrue(ServerConsoleSubmissionGate().claim(1,"help",true))
    }
}
