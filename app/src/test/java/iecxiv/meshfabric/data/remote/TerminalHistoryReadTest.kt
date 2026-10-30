package iecxiv.meshfabric.data.remote
import org.junit.Assert.*
import org.junit.Test
class TerminalHistoryReadTest {
    @Test fun explicitHistoryReadAdvancesWithoutChangingStrictStartup() {
        val journal = TerminalOutputJournal(4)
        journal.append("abcdef")
        assertTrue(journal.read(null).gap)
        val first = journal.readHistory(null, 2)
        assertFalse(first.gap)
        assertEquals("cd", first.text)
        val second = journal.readHistory(TerminalOutputCursor(first.epoch, first.end), 2)
        assertEquals("ef", second.text)
        assertTrue(journal.read(null).gap)
    }
    @Test fun historyReadStillRejectsEvictedAndInvalidatedCursors() {
        val journal = TerminalOutputJournal(4)
        journal.append("abcdef")
        assertTrue(journal.readHistory(TerminalOutputCursor(0, 0)).gap)
        val first = journal.readHistory(null)
        journal.clear()
        journal.append("new")
        assertTrue(journal.readHistory(TerminalOutputCursor(first.epoch, first.end)).gap)
        assertEquals("new", journal.readHistory(null).text)
    }
}
