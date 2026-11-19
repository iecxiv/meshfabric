package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceListBatchPowerTest {
    @Test fun targetsAreDeduplicatedAndBlankIdsAreExcluded() {
        val group = MeshGroup(id = "mesh//one", name = "Group")
        val node = MeshNode(id = "node//one", name = "Device")
        val targets = deviceBatchPowerTargets(listOf(group to node, group to node, group to MeshNode()))
        assertEquals(1, targets.size)
        assertEquals(node.id, targets.single().node.id)
        assertEquals(DeviceBatchPowerPhase.CHECKING, targets.single().phase)
        assertNull(targets.single().draft)
    }
    @Test fun conflictingGroupAssignmentsAreNotChosenArbitrarily() {
        val node = MeshNode(id = "node//one")
        assertTrue(deviceBatchPowerTargets(listOf(MeshGroup(id = "a") to node, MeshGroup(id = "b") to node)).isEmpty())
    }
    @Test fun planDoesNotGrowWhenInputChanges() {
        val input = mutableListOf(MeshGroup() to MeshNode(id = "node//one"))
        val targets = deviceBatchPowerTargets(input)
        input.add(MeshGroup() to MeshNode(id = "node//two"))
        assertEquals(1, targets.size)
    }
    @Test fun acknowledgementIsNotReportedAsExecutionCompleted() {
        assertEquals(DeviceBatchPowerPhase.ACCEPTED, deviceBatchPowerResult(GeneralDeviceActionsState(phase = "accepted"), true))
        assertEquals(DeviceBatchPowerPhase.REJECTED, deviceBatchPowerResult(GeneralDeviceActionsState(phase = "rejected"), true))
    }
    @Test fun unacknowledgedSubmissionRemainsUnconfirmed() {
        for (phase in listOf("sent", "checking", "ready")) {
            assertEquals(DeviceBatchPowerPhase.UNCONFIRMED, deviceBatchPowerResult(GeneralDeviceActionsState(phase = phase), true))
        }
        assertEquals(DeviceBatchPowerPhase.UNCONFIRMED, deviceBatchPowerResult(GeneralDeviceActionsState(phase = "error", code = "unconfirmed"), true))
    }
    @Test fun preSendFailureDoesNotClaimARequestWasSent() {
        assertEquals(DeviceBatchPowerPhase.NOT_SENT, deviceBatchPowerResult(GeneralDeviceActionsState(phase = "error", code = "changed"), true))
        assertEquals(DeviceBatchPowerPhase.NOT_SENT, deviceBatchPowerResult(GeneralDeviceActionsState(phase = "ready"), false))
    }
}
