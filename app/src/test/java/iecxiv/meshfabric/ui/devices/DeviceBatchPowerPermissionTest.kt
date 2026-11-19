package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchPowerPermissionTest {
    private val nodeId = "node//one"
    private val meshId = "mesh//one"
    private val powerRights = 262152L
    private val powerActions = listOf(GeneralDeviceAction.RESTART, GeneralDeviceAction.SHUTDOWN, GeneralDeviceAction.SLEEP)
    private val actions = listOf(GeneralDeviceAction.WAKE) + powerActions
    private fun meta(rights: Long = powerRights) = GeneralMeta("Device", emptyList(), rights, "Linux", meshId)
    private fun inventory(conn: Int = 1, type: Int = 2): List<Pair<MeshGroup, List<MeshNode>>> =
        listOf(MeshGroup(id = meshId, name = "Group", mtype = type) to listOf(MeshNode(id = nodeId, meshid = meshId, conn = conn)))
    private fun draft(action: GeneralDeviceAction) = GeneralDeviceActionDraft(action, meshId, "Linux")
    private fun reply(action: String = "poweraction", id: String = "request", node: String = nodeId, result: String = "ok") =
        JsonObject().apply {
            addProperty("action", action)
            addProperty("responseid", id)
            addProperty("nodeid", node)
            addProperty("result", result)
        }

    @Test fun wakeCanTargetAnOfflineDevice() {
        assertTrue(generalDeviceActionAvailable(nodeId, meta(64L), inventory(conn = 0), GeneralDeviceAction.WAKE))
    }

    @Test fun wakeRequiresItsOwnPermission() {
        for (rights in listOf(0L, 8L, powerRights)) {
            assertFalse(generalDeviceActionAvailable(nodeId, meta(rights), inventory(), GeneralDeviceAction.WAKE))
        }
    }

    @Test fun powerActionsAllowAnOnlineAgentWithBothPermissions() {
        for (action in powerActions) {
            assertTrue(generalDeviceActionAvailable(nodeId, meta(), inventory(conn = 1), action))
            assertTrue(generalDeviceActionAvailable(nodeId, meta(), inventory(conn = 3), action))
        }
    }

    @Test fun powerActionsDoNotTreatAnyConnectivityAsAnOnlineAgent() {
        for (action in powerActions) for (conn in listOf(0, 2, 4)) {
            assertFalse(generalDeviceActionAvailable(nodeId, meta(), inventory(conn = conn), action))
        }
    }

    @Test fun powerActionsRequireBothPermissionBits() {
        for (action in powerActions) for (rights in listOf(0L, 8L, 262144L, 64L)) {
            assertFalse(generalDeviceActionAvailable(nodeId, meta(rights), inventory(), action))
        }
    }

    @Test fun nonAgentGroupsAreNotOfferedTheseAgentActions() {
        for (action in actions) for (type in listOf(0, 1, 3, 4)) {
            assertFalse(generalDeviceActionAvailable(nodeId, meta(0xffffffffL), inventory(type = type), action))
        }
    }

    @Test fun missingOrChangedGroupCannotBeUsed() {
        for (action in actions) {
            assertFalse(generalDeviceActionAvailable(nodeId, meta(0xffffffffL), emptyList(), action))
            assertFalse(generalDeviceActionAvailable(nodeId, meta(0xffffffffL).copy(mesh = "mesh//other"), inventory(), action))
        }
    }

    @Test fun duplicateNodeIdentitiesAreRejected() {
        val original = inventory().single()
        val duplicated = listOf(original.first to (original.second + original.second))
        for (action in actions) {
            assertFalse(generalDeviceActionAvailable(nodeId, meta(0xffffffffL), duplicated, action))
            assertFalse(generalDeviceActionAvailable(nodeId, meta(0xffffffffL), inventory() + inventory(), action))
        }
    }

    @Test fun anUnknownNodeIsNotAuthorizedByAnotherNodesMetadata() {
        for (action in actions) {
            assertFalse(generalDeviceActionAvailable("node//other", meta(0xffffffffL), inventory(), action))
        }
    }

    @Test fun powerPacketsKeepTheExactTargetAndRequestIdentifier() {
        for ((action, type) in listOf(GeneralDeviceAction.RESTART to 3, GeneralDeviceAction.SHUTDOWN to 2, GeneralDeviceAction.SLEEP to 4)) {
            val packet = generalDeviceActionPacket(nodeId, "request", draft(action), meta(), inventory())
            assertEquals("poweraction", packet.get("action").asString)
            assertEquals(type, packet.get("actiontype").asInt)
            assertEquals("request", packet.get("responseid").asString)
            assertEquals(1, packet.getAsJsonArray("nodeids").size())
            assertEquals(nodeId, packet.getAsJsonArray("nodeids")[0].asString)
        }
    }

    @Test fun wakePacketDoesNotInventAPowerActionType() {
        val packet = generalDeviceActionPacket(nodeId, "request", draft(GeneralDeviceAction.WAKE), meta(64L), inventory(conn = 0))
        assertEquals("wakedevices", packet.get("action").asString)
        assertFalse(packet.has("actiontype"))
        assertEquals(nodeId, packet.getAsJsonArray("nodeids")[0].asString)
    }

    @Test fun changedBaselinePreventsPacketCreation() {
        val original = draft(GeneralDeviceAction.RESTART)
        assertTrue(runCatching { generalDeviceActionPacket(nodeId, "request", original, meta().copy(mesh = "mesh//other"), inventory()) }.isFailure)
        assertTrue(runCatching { generalDeviceActionPacket(nodeId, "request", original, meta().copy(os = "Windows"), inventory()) }.isFailure)
    }

    @Test fun revokedRightsPreventPacketCreation() {
        for (action in powerActions) {
            assertTrue(runCatching { generalDeviceActionPacket(nodeId, "request", draft(action), meta(0L), inventory()) }.isFailure)
        }
    }

    @Test fun acknowledgementsMustMatchActionAndRequest() {
        assertNull(generalDeviceActionAck(reply(id = "other"), nodeId, "request", GeneralDeviceAction.RESTART))
        assertNull(generalDeviceActionAck(reply(action = "wakedevices"), nodeId, "request", GeneralDeviceAction.RESTART))
        assertNull(generalDeviceActionAck(reply(), nodeId, "", GeneralDeviceAction.RESTART))
    }

    @Test fun acknowledgementsCannotReferToOtherOrMultipleDevices() {
        assertNull(generalDeviceActionAck(reply(node = "node//other"), nodeId, "request", GeneralDeviceAction.RESTART))
        val packet = reply().apply { add("nodeids", JsonArray().apply { add(nodeId); add("node//other") }) }
        assertNull(generalDeviceActionAck(packet, nodeId, "request", GeneralDeviceAction.RESTART))
    }

    @Test fun ambiguousRepliesAreNotTreatedAsAcceptance() {
        for (result in listOf("", "queued", "pending")) {
            assertNull(generalDeviceActionAck(reply(result = result), nodeId, "request", GeneralDeviceAction.RESTART))
        }
    }

    @Test fun acceptanceAndRejectionAreKeptDistinct() {
        assertEquals("accepted", generalDeviceActionAck(reply(), nodeId, "request", GeneralDeviceAction.RESTART))
        assertEquals("rejected", generalDeviceActionAck(reply(result = "denied"), nodeId, "request", GeneralDeviceAction.RESTART))
        assertEquals("rejected", generalDeviceActionAck(reply().apply { addProperty("error", "denied") }, nodeId, "request", GeneralDeviceAction.RESTART))
    }
}
