package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class DownloadReconnectNoticeTest {
    @Test fun networkCallbackAndCatchShareOneNotice() {
        val attempt = DownloadNetworkAttempt()
        attempt.invalidate()
        assertTrue(attempt.takeReconnectNotice())
        assertFalse(attempt.takeReconnectNotice())
    }
    @Test fun eachNetworkDirectionCanNotify() {
        val attempt = DownloadNetworkAttempt()
        attempt.invalidate()
        assertTrue(attempt.takeReconnectNotice())
        attempt.invalidate()
        assertTrue(attempt.takeReconnectNotice())
        assertFalse(attempt.takeReconnectNotice())
    }
    @Test fun openingAnotherTransportDoesNotDuplicateNotice() {
        val attempt = DownloadNetworkAttempt()
        assertTrue(attempt.takeReconnectNotice())
        val resource = Any()
        attempt.bind(attempt.current(), resource) { }
        attempt.release(attempt.current(), resource)
        assertFalse(attempt.takeReconnectNotice())
    }
}
