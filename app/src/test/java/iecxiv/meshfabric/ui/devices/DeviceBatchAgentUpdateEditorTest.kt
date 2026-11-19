package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchAgentUpdateEditorTest {
    private val node = MeshNode(id = "node//device", meshid = "mesh//group", conn = 1)
    private val meta = GeneralMeta("Device", emptyList(), 0xffffffffL, "Linux", node.meshid)
    private fun inventory(current: MeshNode = node, type: Int = 2) = listOf(MeshGroup(id = node.meshid, mtype = type) to listOf(current))
    private val draft = DeviceBatchAgentUpdateDraft(node.id, node.meshid, meta.os, "100")

    @Test fun packetTargetsOneExactDeviceAndUsesExistingProtocol() {
        val packet = deviceBatchAgentUpdatePacket(draft, node.id, meta, inventory(), "100")
        assertEquals("updateAgents", packet.get("action").asString)
        assertEquals(listOf(node.id), packet.getAsJsonArray("nodeids").map { it.asString })
        assertFalse(packet.has("cmds"))
    }
    @Test fun requiresFullRightsNotOnlyConsolePermission() {
        assertNull(deviceBatchAgentUpdateDraft(node.id, meta.copy(rights = 16L), inventory(), "100"))
        assertNotNull(deviceBatchAgentUpdateDraft(node.id, meta.copy(rights = -1L), inventory(), "100"))
    }
    @Test fun requiresAnOnlineAgentRatherThanOnlyAmtConnectivity() {
        for (conn in listOf(0, 2, 4)) assertNull(deviceBatchAgentUpdateDraft(node.id, meta, inventory(node.copy(conn = conn)), "100"))
    }
    @Test fun rejectsNonAgentGroupsAndMissingDevices() {
        assertNull(deviceBatchAgentUpdateDraft(node.id, meta, inventory(type = 1), "100"))
        assertNull(deviceBatchAgentUpdateDraft(node.id, meta, emptyList(), "100"))
    }
    @Test fun duplicateDevicesAreAmbiguous() {
        val list = listOf(MeshGroup(id = node.meshid, mtype = 2) to listOf(node, node))
        assertNull(deviceBatchAgentUpdateDraft(node.id, meta, list, "100"))
    }
    @Test fun changedGroupOrOperatingSystemInvalidatesReview() {
        assertTrue(runCatching { deviceBatchAgentUpdatePacket(draft, node.id, meta.copy(mesh = "mesh//other"), inventory(), "100") }.isFailure)
        assertTrue(runCatching { deviceBatchAgentUpdatePacket(draft, node.id, meta.copy(os = "Windows"), inventory(), "100") }.isFailure)
    }
    @Test fun changedObservedVersionInvalidatesReview() {
        assertTrue(runCatching { deviceBatchAgentUpdatePacket(draft, node.id, meta, inventory(), "101") }.isFailure)
    }
    @Test fun unknownVersionCanBeReviewedWithoutInventingOne() {
        assertNull(requireNotNull(deviceBatchAgentUpdateDraft(node.id, meta, inventory(), null)).version)
    }
    @Test fun anotherDeviceCannotReuseTheReview() {
        assertTrue(runCatching { deviceBatchAgentUpdatePacket(draft, "node//other", meta, inventory(), "100") }.isFailure)
    }
    @Test fun malformedAndCrossDomainIdentitiesAreRejected() {
        assertTrue(deviceBatchAgentUpdateIdentity("user//operator", node.id))
        assertFalse(deviceBatchAgentUpdateIdentity("user/other/operator", node.id))
        assertFalse(deviceBatchAgentUpdateIdentity("node//operator", node.id))
        assertFalse(deviceBatchAgentUpdateIdentity("user//operator", "node//"))
        assertFalse(deviceBatchAgentUpdateIdentity("user//operator", "node//bad\n"))
    }
}
