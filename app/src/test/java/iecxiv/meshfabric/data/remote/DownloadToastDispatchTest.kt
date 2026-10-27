package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class DownloadToastDispatchTest {
    @Test fun staleQueuedNoticeDoesNotConsumeCurrentGeneration() {
        val gate = DownloadNetworkAttempt()
        val queued = gate.current()
        gate.invalidate()
        assertFalse(gate.takeReconnectNotice(queued))
        assertTrue(gate.takeReconnectNotice(gate.current()))
    }
    @Test fun failedShowCanBeRetried() {
        val gate = DownloadNetworkAttempt()
        val epoch = gate.current()
        assertTrue(gate.takeReconnectNotice(epoch))
        gate.releaseReconnectNotice(epoch)
        assertTrue(gate.takeReconnectNotice(epoch))
    }
    @Test fun oldFailureCannotReleaseNewNotice() {
        val gate = DownloadNetworkAttempt()
        val old = gate.current()
        gate.takeReconnectNotice(old)
        gate.invalidate()
        assertTrue(gate.takeReconnectNotice())
        gate.releaseReconnectNotice(old)
        assertFalse(gate.takeReconnectNotice())
    }
    @Test fun callbackAndRetryStillDeduplicateOnMainDispatch() {
        val gate = DownloadNetworkAttempt()
        gate.invalidate()
        val epoch = gate.current()
        assertTrue(gate.takeReconnectNotice(epoch))
        assertFalse(gate.takeReconnectNotice(epoch))
        gate.invalidate()
        assertTrue(gate.takeReconnectNotice())
    }
}
