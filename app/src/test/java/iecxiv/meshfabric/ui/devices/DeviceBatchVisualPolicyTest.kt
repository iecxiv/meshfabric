package iecxiv.meshfabric.ui.devices
import org.junit.Assert.*
import org.junit.Test
class DeviceBatchVisualPolicyTest {
    @Test fun onlyExplicitlyVerifiedDomainsAreGreen() {
        for(domain in listOf("tags","move")) assertEquals(DeviceBatchVisualKind.VERIFIED,deviceBatchVisualKind(domain,"VERIFIED"))
        assertEquals(DeviceBatchVisualKind.VERIFIED,deviceBatchVisualKind("managed","removed"))
    }
    @Test fun verificationCannotLeakAcrossDomains() {
        for(domain in listOf("run","upload","power","agent","notifications","other")) assertNotEquals(DeviceBatchVisualKind.VERIFIED,deviceBatchVisualKind(domain,"VERIFIED"))
    }
    @Test fun acceptedAndQueuedDoNotClaimSuccess() {
        for(phase in listOf("ACCEPTED","QUEUED","LOCAL_QUEUED")) assertEquals(DeviceBatchVisualKind.PROGRESS,deviceBatchVisualKind("agent",phase))
    }
    @Test fun outputAndLimitedFileObservationAreNotGreen() {
        assertEquals(DeviceBatchVisualKind.PROGRESS,deviceBatchVisualKind("run","OUTPUT"))
        assertEquals(DeviceBatchVisualKind.PROGRESS,deviceBatchVisualKind("upload","OBSERVED"))
    }
    @Test fun unconfirmedAndConflictsAreWarnings() {
        for(phase in listOf("UNKNOWN","UNCONFIRMED","CONFLICT")) assertEquals(DeviceBatchVisualKind.WARNING,deviceBatchVisualKind("move",phase))
    }
    @Test fun rejectedAndUnsentAreDifferent() {
        assertEquals(DeviceBatchVisualKind.ERROR,deviceBatchVisualKind("power","REJECTED"))
        for(phase in listOf("SKIPPED","NOT_SENT","UNCHANGED")) assertEquals(DeviceBatchVisualKind.NEUTRAL,deviceBatchVisualKind("tags",phase))
    }
    @Test fun managedErrorPreservesUncertainty() {
        assertEquals(DeviceBatchVisualKind.WARNING,deviceBatchVisualKind("managed","error","unconfirmed"))
        assertEquals(DeviceBatchVisualKind.NEUTRAL,deviceBatchVisualKind("managed","error","connection"))
    }
    @Test fun unknownStatesAreConservative() {
        assertEquals(DeviceBatchVisualKind.WARNING,deviceBatchVisualKind("other","new_state"))
        assertEquals(DeviceBatchVisualKind.PROGRESS,deviceBatchVisualKind("managed","loading"))
    }
}
