package iecxiv.meshfabric.ui.devices

import org.junit.Assert.*
import org.junit.Test

class DeviceBatchAgentUpdateResultTest {
    @Test fun queuedMeansOnlyLocallyQueued() {
        assertEquals(DeviceBatchAgentUpdatePhase.LOCAL_QUEUED, deviceBatchAgentUpdateResult("queued", "installation_unverified", true))
    }
    @Test fun acceptanceDoesNotBecomeInstallationSuccess() {
        assertEquals(DeviceBatchAgentUpdatePhase.UNCONFIRMED, deviceBatchAgentUpdateResult("accepted", "", true))
        assertEquals(DeviceBatchAgentUpdatePhase.UNCONFIRMED, deviceBatchAgentUpdateResult("verified", "", true))
    }
    @Test fun missingResultAfterSubmissionIsUnknown() {
        assertEquals(DeviceBatchAgentUpdatePhase.UNCONFIRMED, deviceBatchAgentUpdateResult(null, "", true))
    }
    @Test fun failureBeforeAttemptIsNotSent() {
        assertEquals(DeviceBatchAgentUpdatePhase.NOT_SENT, deviceBatchAgentUpdateResult("error", "changed", true))
        assertEquals(DeviceBatchAgentUpdatePhase.NOT_SENT, deviceBatchAgentUpdateResult(null, "", false))
    }
    @Test fun uncertainTransportAndSkippedAreDistinct() {
        assertEquals(DeviceBatchAgentUpdatePhase.UNCONFIRMED, deviceBatchAgentUpdateResult("error", "unconfirmed", false))
        assertEquals(DeviceBatchAgentUpdatePhase.SKIPPED, deviceBatchAgentUpdateResult("skipped", "unavailable", true))
    }
    @Test fun stoppingDoesNotClaimRemoteCancellation() {
        assertEquals(DeviceBatchAgentUpdatePhase.UNCONFIRMED, deviceBatchAgentUpdateStopped(DeviceBatchAgentUpdatePhase.SENDING))
        assertEquals(DeviceBatchAgentUpdatePhase.NOT_SENT, deviceBatchAgentUpdateStopped(DeviceBatchAgentUpdatePhase.READY))
    }
    @Test fun stoppingPreservesRecordedOutcomes() {
        for (phase in listOf(DeviceBatchAgentUpdatePhase.LOCAL_QUEUED, DeviceBatchAgentUpdatePhase.SKIPPED,
            DeviceBatchAgentUpdatePhase.CONFLICT, DeviceBatchAgentUpdatePhase.NOT_SENT, DeviceBatchAgentUpdatePhase.UNCONFIRMED)) {
            assertEquals(phase, deviceBatchAgentUpdateStopped(phase))
        }
    }
}
