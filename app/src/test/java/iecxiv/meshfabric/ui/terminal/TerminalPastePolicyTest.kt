package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalPastePolicyTest {
    @Test fun plainTextAcceptedWithoutTrimming() { assertNull(TerminalPastePolicy.issue(" ac1b ")) }
    @Test fun emptyRejected() { assertEquals(TerminalPasteIssue.EMPTY, TerminalPastePolicy.issue("")) }
    @Test fun nullRejected() { assertEquals(TerminalPasteIssue.EMPTY, TerminalPastePolicy.issue(null)) }
    @Test fun oversizeRejectedNotTruncated() { assertEquals(TerminalPasteIssue.TOO_LARGE, TerminalPastePolicy.issue("a".repeat(16385))) }
    @Test fun boundaryAccepted() { assertNull(TerminalPastePolicy.issue("a".repeat(16384))) }
    @Test fun escapesRejected() { assertEquals(TerminalPasteIssue.CONTROL, TerminalPastePolicy.issue(27.toChar().toString() + "[201~")) }
    @Test fun nulRejected() { assertEquals(TerminalPasteIssue.CONTROL, TerminalPastePolicy.issue(0.toChar().toString())) }
    @Test fun c1Rejected() { assertEquals(TerminalPasteIssue.CONTROL, TerminalPastePolicy.issue(0x85.toChar().toString())) }
    @Test fun bidiControlsRejected() { assertEquals(TerminalPasteIssue.CONTROL, TerminalPastePolicy.issue(0x202E.toChar().toString())) }
    @Test fun emojiAccepted() { assertNull(TerminalPastePolicy.issue(String(Character.toChars(0x1F600)))) }
    @Test fun unpairedSurrogateRejected() { assertEquals(TerminalPasteIssue.UNICODE, TerminalPastePolicy.issue(0xD800.toChar().toString())) }
    @Test fun newlinesNeedAcknowledgement() {
        assertTrue(TerminalPastePolicy.multiline("one" + 10.toChar() + "two"))
        assertTrue(TerminalPastePolicy.multiline("one" + 13.toChar()))
        assertFalse(TerminalPastePolicy.multiline("one two"))
        assertNull(TerminalPastePolicy.issue("one" + 10.toChar() + "two" + 9.toChar()))
    }
}
