package iecxiv.meshfabric.data.remote

import iecxiv.meshfabric.ui.terminal.TerminalOutputDelivery
import org.junit.Assert.*
import org.junit.Test

class TerminalOutputPipelineTest {
    @Test fun startupAndLiveOutputHaveNoOverlap() {
        val journal = TerminalOutputJournal()
        val delivery = TerminalOutputDelivery()
        delivery.attach(1)
        journal.append("history")
        val first = delivery.prepare(1, journal.read(delivery.cursor))!!
        journal.append("live")
        assertNull(delivery.cursor)
        assertTrue(delivery.acknowledge(1, first.token))
        val second = delivery.prepare(1, journal.read(delivery.cursor))!!
        assertEquals("history", first.text)
        assertEquals("live", second.text)
    }
    @Test fun manyConflatedWakeupsStillReadAllPendingText() {
        val journal = TerminalOutputJournal()
        journal.append("a"); journal.append("b"); journal.append("c")
        assertEquals("abc", journal.read(null).text)
    }
    @Test fun onlyOneBatchCanBePending() {
        val journal = TerminalOutputJournal()
        val delivery = TerminalOutputDelivery()
        journal.append("abc"); delivery.attach(1)
        assertNotNull(delivery.prepare(1, journal.read(null)))
        assertNull(delivery.prepare(1, journal.read(null)))
    }
    @Test fun wrongAckCannotAdvanceCursor() {
        val journal = TerminalOutputJournal()
        val delivery = TerminalOutputDelivery()
        journal.append("abc"); delivery.attach(1)
        delivery.prepare(1, journal.read(null))!!
        assertFalse(delivery.acknowledge(1, "wrong"))
        assertNull(delivery.cursor)
        assertTrue(delivery.hasPending)
    }
    @Test fun repeatedAckIsIgnored() {
        val journal = TerminalOutputJournal()
        val delivery = TerminalOutputDelivery()
        journal.append("abc"); delivery.attach(1)
        val batch = delivery.prepare(1, journal.read(null))!!
        assertTrue(delivery.acknowledge(1, batch.token))
        assertFalse(delivery.acknowledge(1, batch.token))
    }
    @Test fun replacedViewRejectsOldAck() {
        val journal = TerminalOutputJournal()
        val delivery = TerminalOutputDelivery()
        journal.append("abc"); delivery.attach(1)
        val old = delivery.prepare(1, journal.read(null))!!
        delivery.attach(2)
        val current = delivery.prepare(2, journal.read(null))!!
        assertFalse(delivery.acknowledge(1, old.token))
        assertNull(delivery.cursor)
        assertTrue(delivery.acknowledge(2, current.token))
    }
    @Test fun disposalRejectsPendingAck() {
        val journal = TerminalOutputJournal()
        val delivery = TerminalOutputDelivery()
        journal.append("abc"); delivery.attach(1)
        val batch = delivery.prepare(1, journal.read(null))!!
        delivery.invalidate()
        assertFalse(delivery.acknowledge(1, batch.token))
    }
    @Test fun boundedJournalReportsGapInsteadOfSkipping() {
        val journal = TerminalOutputJournal(4)
        journal.append("abcdef")
        assertTrue(journal.read(TerminalOutputCursor(0, 0)).gap)
        assertTrue(journal.read(null).gap)
        assertEquals("cdef", journal.read(TerminalOutputCursor(0, 2)).text)
    }
    @Test fun futureCursorIsRejected() {
        val journal = TerminalOutputJournal()
        journal.append("abc")
        assertTrue(journal.read(TerminalOutputCursor(0, 4)).gap)
    }
    @Test fun clearingHistoryInvalidatesOldCursor() {
        val journal = TerminalOutputJournal()
        journal.append("old")
        val old = journal.read(null)
        journal.clear(); journal.append("new")
        assertTrue(journal.read(TerminalOutputCursor(old.epoch, old.end)).gap)
        assertEquals("new", journal.read(null).text)
    }
    @Test fun utf16PairIsNotSplitBetweenBatches() {
        val journal = TerminalOutputJournal()
        journal.append("a😀b")
        val first = journal.read(null, 2)
        val second = journal.read(TerminalOutputCursor(first.epoch, first.end), 2)
        val third = journal.read(TerminalOutputCursor(second.epoch, second.end), 2)
        assertEquals("a", first.text)
        assertEquals("😀", second.text)
        assertEquals("b", third.text)
    }
    @Test fun independentSessionsDoNotMixOutput() {
        val first = TerminalOutputJournal()
        val second = TerminalOutputJournal()
        first.append("one"); second.append("two")
        assertEquals("one", first.read(null).text)
        assertEquals("two", second.read(null).text)
    }
    @Test fun emptyJournalDoesNotCreatePendingBatch() {
        val delivery = TerminalOutputDelivery()
        delivery.attach(1)
        assertNull(delivery.prepare(1, TerminalOutputJournal().read(null)))
        assertFalse(delivery.hasPending)
    }
    @Test fun journalGapAfterSlowAckIsDetected() {
        val journal = TerminalOutputJournal(4)
        val delivery = TerminalOutputDelivery()
        delivery.attach(1); journal.append("ab")
        val batch = delivery.prepare(1, journal.read(null))!!
        journal.append("cdefgh")
        assertTrue(delivery.acknowledge(1, batch.token))
        assertTrue(journal.read(delivery.cursor).gap)
    }
}
