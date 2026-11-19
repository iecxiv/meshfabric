package iecxiv.meshfabric.ui.devices

import org.junit.Assert.*
import org.junit.Test

class DeviceBatchMoveResultTest {
    @Test fun onlyVerifiedReadbackIsDisplayedAsVerified() {
        assertEquals(DeviceBatchMovePhase.VERIFIED, deviceBatchMoveResult("verified", "", true))
        assertEquals(DeviceBatchMovePhase.UNCONFIRMED, deviceBatchMoveResult("accepted", "", true))
        assertEquals(DeviceBatchMovePhase.UNCONFIRMED, deviceBatchMoveResult("moving", "", true))
    }

    @Test fun missingTerminalResultAfterSubmissionIsUnknown() {
        assertEquals(DeviceBatchMovePhase.UNCONFIRMED, deviceBatchMoveResult(null, "", true))
    }

    @Test fun failureBeforeAnyMoveAttemptIsNotSent() {
        assertEquals(DeviceBatchMovePhase.NOT_SENT, deviceBatchMoveResult("error", "changed", true))
        assertEquals(DeviceBatchMovePhase.NOT_SENT, deviceBatchMoveResult(null, "", false))
    }

    @Test fun uncertainTransportDoesNotBecomeNotSent() {
        assertEquals(DeviceBatchMovePhase.UNCONFIRMED, deviceBatchMoveResult("error", "unconfirmed", false))
    }

    @Test fun skippedAndRejectedResultsStayDistinct() {
        assertEquals(DeviceBatchMovePhase.SKIPPED, deviceBatchMoveResult("skipped", "unavailable", true))
        assertEquals(DeviceBatchMovePhase.REJECTED, deviceBatchMoveResult("rejected", "denied", true))
    }

    @Test fun stoppingPendingRowsDoesNotClaimRemoteCancellation() {
        assertEquals(DeviceBatchMovePhase.UNCONFIRMED, deviceBatchMoveStopped(DeviceBatchMovePhase.MOVING))
        assertEquals(DeviceBatchMovePhase.NOT_SENT, deviceBatchMoveStopped(DeviceBatchMovePhase.READY))
    }

    @Test fun stoppingPreservesAlreadyRecordedResults() {
        for (phase in listOf(DeviceBatchMovePhase.VERIFIED, DeviceBatchMovePhase.SKIPPED, DeviceBatchMovePhase.REJECTED,
            DeviceBatchMovePhase.CONFLICT, DeviceBatchMovePhase.NOT_SENT, DeviceBatchMovePhase.UNCONFIRMED)) {
            assertEquals(phase, deviceBatchMoveStopped(phase))
        }
    }
}
