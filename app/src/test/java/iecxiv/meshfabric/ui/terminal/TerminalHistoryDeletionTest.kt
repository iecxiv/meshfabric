package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalHistoryDeletionTest {
    @Test fun deletesOnlyRequestedArchivedIds() {
        assertEquals(setOf("old"), terminalHistoryDeletionIds(setOf("old"), setOf("old", "other"), "current"))
    }
    @Test fun neverDeletesCurrentSessionEvenIfListedAsArchived() {
        assertEquals(setOf("old"), terminalHistoryDeletionIds(setOf("old", "current"), setOf("old", "current"), "current"))
    }
    @Test fun ignoresUnknownIds() {
        assertTrue(terminalHistoryDeletionIds(setOf("missing"), setOf("old"), "current").isEmpty())
    }
    @Test fun confirmationSnapshotCannotDeleteLaterArrivals() {
        val reviewed = setOf("old")
        assertEquals(reviewed, terminalHistoryDeletionIds(reviewed, setOf("old", "newlyArchived"), "current"))
    }
    @Test fun supportsSeveralHistoriesAndEmptySelection() {
        assertEquals(setOf("a", "b"), terminalHistoryDeletionIds(setOf("a", "b"), setOf("a", "b"), "current"))
        assertTrue(terminalHistoryDeletionIds(emptySet(), setOf("a"), "current").isEmpty())
    }
}
