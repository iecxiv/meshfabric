package iecxiv.meshfabric.data.remote

import iecxiv.meshfabric.data.model.SessionStatus
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class FileSessionPolicyTest {
    @Test fun retainedSessionKeepsHighlightDuringReconnect() {
        assertEquals(SessionStatus.CONNECTED, FileSessionPolicy.status(true, false))
    }
    @Test fun unopenedOrExplicitlyClosedSessionIsNeutral() {
        assertEquals(SessionStatus.NONE, FileSessionPolicy.status(false, false))
        assertEquals(SessionStatus.NONE, FileSessionPolicy.status(false, true))
    }
    @Test fun recoverableSessionCountsAsActiveInKeepAlive() {
        assertTrue(FileSessionPolicy.active(true, false))
        assertFalse(FileSessionPolicy.disconnected(true, false))
    }
    @Test fun onlyConfirmedIrrecoverableSessionCountsAsDisconnected() {
        assertEquals(SessionStatus.DISCONNECTED_WITH_BUFFER, FileSessionPolicy.status(true, true))
        assertTrue(FileSessionPolicy.disconnected(true, true))
        assertFalse(FileSessionPolicy.active(true, true))
    }
    @Test fun closedSessionDoesNotKeepEitherNotificationFlag() {
        assertFalse(FileSessionPolicy.active(false, false))
        assertFalse(FileSessionPolicy.disconnected(false, true))
    }
    @Test fun invalidRequestConfigurationIsExplicitFatalCondition() {
        assertTrue(FileSessionPolicy.configurationFailure(IllegalArgumentException("invalid URL")))
    }
    @Test fun networkLossAndTimeoutAreNotIrrecoverable() {
        assertFalse(FileSessionPolicy.configurationFailure(IOException("network lost")))
        assertFalse(FileSessionPolicy.configurationFailure(SocketTimeoutException("timeout")))
    }
    @Test fun tlsAndDirectoryFailuresAreNotPromotedToFatalConfiguration() {
        assertFalse(FileSessionPolicy.configurationFailure(SSLHandshakeException("handshake failed")))
        assertFalse(FileSessionPolicy.configurationFailure(SecurityException("directory access denied")))
    }
}
