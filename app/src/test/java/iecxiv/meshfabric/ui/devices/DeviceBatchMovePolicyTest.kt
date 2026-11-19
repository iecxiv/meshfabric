package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchMovePolicyTest {
    private val actor = "user//operator"
    private val node = MeshNode(id = "node//device", meshid = "mesh//source")
    private val meta = GeneralMeta("Device", emptyList(), 4L, "Linux", node.meshid)
    private val groups = listOf(
        DeviceBatchMoveGroup(node.meshid, 2, 5L),
        DeviceBatchMoveGroup("mesh//target", 2, 5L)
    )
    private fun draft(
        current: MeshNode = node,
        destination: String = "mesh//target",
        user: String = actor,
        available: List<DeviceBatchMoveGroup> = groups,
        information: GeneralMeta = meta
    ) = deviceBatchMoveDraft(current, destination, user, available, information)

    @Test fun createsAnExplicitReviewedTransfer() {
        assertEquals(DeviceBatchMoveDraft(actor, node.id, node.meshid, "mesh//target"), draft())
    }

    @Test fun offlineDevicesAreNotExcludedByConnectionState() {
        assertNotNull(draft(current = node.copy(conn = 0)))
    }

    @Test fun cannotInferPermissionFromGroupVisibility() {
        for (index in groups.indices) {
            assertNull(draft(available = groups.mapIndexed { i, group -> if (i == index) group.copy(rights = null) else group }))
        }
    }

    @Test fun requiresBothGroupPermissionBits() {
        for (rights in listOf(0L, 1L, 4L)) {
            for (index in groups.indices) {
                assertNull(draft(available = groups.mapIndexed { i, group -> if (i == index) group.copy(rights = rights) else group }))
            }
        }
    }

    @Test fun acceptsSignedAndUnsignedAdministratorMasks() {
        assertTrue(deviceBatchMoveGroupAllowed(-1L))
        assertTrue(deviceBatchMoveGroupAllowed(0xffffffffL))
    }

    @Test fun rejectsOutOfRangeRights() {
        assertFalse(deviceBatchMoveGroupAllowed(-2L))
        assertFalse(deviceBatchMoveGroupAllowed(0x100000005L))
        assertNull(draft(information = meta.copy(rights = 0x100000004L)))
    }

    @Test fun requiresNodeManagementPermission() {
        assertNull(draft(information = meta.copy(rights = 0L)))
    }

    @Test fun rejectsSameGroupAndMissingGroups() {
        assertNull(draft(destination = node.meshid))
        assertNull(draft(available = groups.take(1)))
        assertNull(draft(available = groups.drop(1)))
    }

    @Test fun rejectsDuplicateSourceOrDestinationIds() {
        groups.forEach { group -> assertNull(draft(available = groups + group)) }
    }

    @Test fun limitsInitialScopeToAgentGroups() {
        for (type in listOf(0, 1, 3, 4)) {
            for (index in groups.indices) {
                assertNull(draft(available = groups.mapIndexed { i, group -> if (i == index) group.copy(mtype = type) else group }))
            }
        }
    }

    @Test fun rejectsMalformedIdentifiers() {
        for (id in listOf("", "target", "mesh//", "mesh//target/extra", "mesh//bad\n")) {
            assertNull(draft(destination = id))
        }
        assertNull(draft(user = "node//operator"))
        assertNull(draft(current = node.copy(id = "mesh//device")))
        assertNull(draft(current = node.copy(meshid = "node//source")))
    }

    @Test fun rejectsCrossDomainTransfersAndIdentities() {
        assertNull(draft(user = "user/other/operator"))
        assertNull(draft(current = node.copy(id = "node/other/device")))
        assertNull(draft(destination = "mesh/other/target"))
    }

    @Test fun rejectsMetadataFromAnotherGroup() {
        assertNull(draft(information = meta.copy(mesh = "mesh//other")))
    }

    @Test fun unchangedReviewedTransferCanBeRevalidated() {
        assertTrue(deviceBatchMoveStillMatches(requireNotNull(draft()), node, actor, groups, meta))
    }

    @Test fun groupOrDeviceChangesInvalidateReview() {
        val reviewed = requireNotNull(draft())
        assertFalse(deviceBatchMoveStillMatches(reviewed, node.copy(id = "node//different"), actor, groups, meta))
        val moved = node.copy(meshid = "mesh//target")
        assertFalse(deviceBatchMoveStillMatches(reviewed, moved, actor, groups, meta.copy(mesh = moved.meshid)))
    }

    @Test fun actorOrPermissionChangesInvalidateReview() {
        val reviewed = requireNotNull(draft())
        assertFalse(deviceBatchMoveStillMatches(reviewed, node, "user//different", groups, meta))
        assertFalse(deviceBatchMoveStillMatches(reviewed, node, actor, groups.map { it.copy(rights = null) }, meta))
        assertFalse(deviceBatchMoveStillMatches(reviewed, node, actor, groups, meta.copy(rights = 0L)))
    }
}
