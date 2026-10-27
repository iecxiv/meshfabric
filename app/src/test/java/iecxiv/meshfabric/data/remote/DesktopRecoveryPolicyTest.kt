package iecxiv.meshfabric.data.remote

import iecxiv.meshfabric.data.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DesktopRecoveryPolicyTest {
    @Test fun retainedDesktopStaysHighlighted() {
        assertEquals(SessionStatus.CONNECTED, DesktopRecoveryPolicy.status(true))
    }
    @Test fun explicitCloseRemovesHighlight() {
        assertEquals(SessionStatus.NONE, DesktopRecoveryPolicy.status(false))
    }
    @Test fun retainedDesktopNeverBecomesLostBuffer() {
        assertNotEquals(SessionStatus.DISCONNECTED_WITH_BUFFER, DesktopRecoveryPolicy.status(true))
    }
    @Test fun retryDelayIsBounded() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L),
            (0..6).map { DesktopRecoveryPolicy.retryDelay(it) })
        assertEquals(30000L, DesktopRecoveryPolicy.retryDelay(Int.MAX_VALUE))
    }
}
