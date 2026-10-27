package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class DownloadNetworkAttemptTest {
    @Test fun networkChangeCancelsBoundHttpRequest() {
        val gate = DownloadNetworkAttempt()
        var cancelled = 0
        val epoch = gate.current()
        assertTrue(gate.bind(epoch, Any()) { cancelled++ })
        gate.invalidate()
        assertEquals(1, cancelled)
        assertFalse(gate.isCurrent(epoch))
    }

    @Test fun newNetworkCanBindFreshRequest() {
        val gate = DownloadNetworkAttempt()
        gate.invalidate()
        var cancelled = false
        assertTrue(gate.bind(gate.current(), Any()) { cancelled = true })
        assertFalse(cancelled)
    }

    @Test fun lateOldRequestIsCancelledWithoutReplacingFreshOne() {
        val gate = DownloadNetworkAttempt()
        val old = gate.current()
        gate.invalidate()
        var freshCancelled = 0
        var oldCancelled = 0
        gate.bind(gate.current(), Any()) { freshCancelled++ }
        assertFalse(gate.bind(old, Any()) { oldCancelled++ })
        assertEquals(1, oldCancelled)
        assertEquals(0, freshCancelled)
        gate.invalidate()
        assertEquals(1, freshCancelled)
    }

    @Test fun oldCleanupCannotDetachNewRequest() {
        val gate = DownloadNetworkAttempt()
        val oldEpoch = gate.current()
        val old = Any()
        gate.bind(oldEpoch, old) { }
        gate.invalidate()
        var cancelled = false
        gate.bind(gate.current(), Any()) { cancelled = true }
        gate.release(oldEpoch, old)
        gate.invalidate()
        assertTrue(cancelled)
    }

    @Test fun staleAttemptCannotWriteDownloadedBytes() {
        val gate = DownloadNetworkAttempt()
        val old = gate.current()
        gate.invalidate()
        var written = false
        assertFalse(gate.runIfCurrent(old) { written = true })
        assertFalse(written)
        assertTrue(gate.runIfCurrent(gate.current()) { written = true })
        assertTrue(written)
    }

    @Test fun releasedRequestIsNotCancelledAgain() {
        val gate = DownloadNetworkAttempt()
        val epoch = gate.current()
        val request = Any()
        var cancelled = false
        gate.bind(epoch, request) { cancelled = true }
        gate.release(epoch, request)
        gate.invalidate()
        assertFalse(cancelled)
    }
}
