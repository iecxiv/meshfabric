package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalAckWatchdogTest {
    @Test fun idleViewNeverTimesOut() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); assertFalse(w.poll(999)) }
    @Test fun visiblePendingBatchGetsFullDeadline() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 5); assertFalse(w.poll(24)); assertTrue(w.poll(25)) }
    @Test fun backgroundSuspendsDeadline() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); w.setActive(false, 5); assertFalse(w.poll(999)); assertNull(w.remaining(999)) }
    @Test fun resumeGetsFreshFullDeadline() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); w.setActive(false, 19); w.setActive(true, 1000); assertFalse(w.poll(1019)); assertTrue(w.poll(1020)) }
    @Test fun matchingAckClearsDeadline() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); assertTrue(w.acknowledge("a")); assertNull(w.remaining(50)); assertFalse(w.poll(50)) }
    @Test fun wrongAckDoesNotClearPendingBatch() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); assertFalse(w.acknowledge("old")); assertTrue(w.poll(20)) }
    @Test fun timeoutIsReportedOnlyOnce() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); assertTrue(w.poll(20)); assertFalse(w.poll(30)) }
    @Test fun lateAckCannotRecoverTimedOutView() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); assertTrue(w.poll(20)); assertFalse(w.acknowledge("a")) }
    @Test fun duplicateActiveEventsDoNotExtendDeadline() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); w.setActive(true, 19); assertTrue(w.poll(20)) }
    @Test fun hiddenBatchStartsDeadlineOnlyOnResume() { val w = TerminalAckWatchdog(20); w.sent("a", 0); assertFalse(w.poll(999)); w.setActive(true, 1000); assertEquals(20L, w.remaining(1000)); assertTrue(w.poll(1020)) }
    @Test fun nextBatchGetsOwnDeadline() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); assertTrue(w.acknowledge("a")); w.sent("b", 10); assertFalse(w.poll(29)); assertTrue(w.poll(30)) }
    @Test fun resetInvalidatesOldAck() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); w.reset(); assertFalse(w.acknowledge("a")); assertFalse(w.poll(999)) }
    @Test fun sessionsHaveIndependentDeadlines() { val a = TerminalAckWatchdog(20); val b = TerminalAckWatchdog(20); a.setActive(true, 0); b.setActive(true, 0); a.sent("a", 0); b.sent("b", 10); assertTrue(a.poll(20)); assertFalse(b.poll(20)) }
    @Test fun backgroundAckCanFinishWithoutFalseTimeout() { val w = TerminalAckWatchdog(20); w.setActive(true, 0); w.sent("a", 0); w.setActive(false, 5); assertTrue(w.acknowledge("a")); w.setActive(true, 1000); assertFalse(w.poll(2000)) }
}
