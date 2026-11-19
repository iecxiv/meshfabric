package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchMoveEditorTest {
    private val actor = "user//operator"
    private val node = MeshNode(id = "node//device", meshid = "mesh//source")
    private val meta = GeneralMeta("Device", emptyList(), 4L, "Linux", node.meshid)
    private val groups = listOf(DeviceBatchMoveGroup(node.meshid, 2, 5L), DeviceBatchMoveGroup("mesh//target", 2, 5L))
    private val draft = DeviceBatchMoveDraft(actor, node.id, node.meshid, "mesh//target")
    private fun json(value: String): JsonObject = JsonParser.parseString(value).asJsonObject
    private fun reply() = json("""{"action":"changeDeviceMesh","responseid":"request","result":"ok"}""")
    private fun readback() = json("""{"action":"getsysinfo","tag":"read","nodeid":"node//device","rights":4,"node":{"_id":"node//device","meshid":"mesh//target","name":"Device"}}""")

    @Test fun packetUsesExactProtocolAndIds() {
        val packet = deviceBatchMovePacket(draft, node, actor, groups, meta, "request")
        assertEquals("changeDeviceMesh", packet.get("action").asString)
        assertEquals("mesh//target", packet.get("meshid").asString)
        assertEquals("request", packet.get("responseid").asString)
        assertEquals(listOf(node.id), packet.getAsJsonArray("nodeids").map { it.asString })
        assertFalse(packet.has("meshname"))
    }

    @Test fun changedSourceCannotProduceAPacket() {
        assertTrue(runCatching {
            deviceBatchMovePacket(draft, node.copy(meshid = "mesh//other"), actor, groups, meta, "request")
        }.isFailure)
    }

    @Test fun changedActorOrLostPermissionsCannotProduceAPacket() {
        assertTrue(runCatching { deviceBatchMovePacket(draft, node, "user//other", groups, meta, "request") }.isFailure)
        assertTrue(runCatching { deviceBatchMovePacket(draft, node, actor, groups, meta.copy(rights = 0L), "request") }.isFailure)
    }

    @Test fun invalidResponseIdsCannotProducePackets() {
        for (id in listOf("", " ", "a".repeat(129), "bad\n")) {
            assertTrue(runCatching { deviceBatchMovePacket(draft, node, actor, groups, meta, id) }.isFailure)
        }
    }

    @Test fun acceptedReplyIsOnlyAnAcknowledgement() {
        assertEquals(DeviceBatchMoveAck.ACCEPTED, deviceBatchMoveAck(reply(), "request"))
        assertFalse(deviceBatchMoveObserved(reply(), node.id, "read", draft.destinationGroupId))
    }

    @Test fun unrelatedRepliesAreIgnored() {
        assertNull(deviceBatchMoveAck(reply(), "other"))
        assertNull(deviceBatchMoveAck(reply(), ""))
        assertNull(deviceBatchMoveAck(reply().apply { addProperty("action", "changedevicemesh") }, "request"))
    }

    @Test fun explicitFailureIsRejected() {
        assertEquals(DeviceBatchMoveAck.REJECTED, deviceBatchMoveAck(reply().apply { addProperty("result", "denied") }, "request"))
        assertEquals(DeviceBatchMoveAck.REJECTED, deviceBatchMoveAck(reply().apply { addProperty("error", "denied") }, "request"))
    }

    @Test fun missingOrMalformedResultsAreNotAccepted() {
        assertNull(deviceBatchMoveAck(reply().apply { remove("result") }, "request"))
        assertNull(deviceBatchMoveAck(reply().apply { addProperty("result", true) }, "request"))
    }

    @Test fun matchingReadbackVerifiesDestination() {
        assertTrue(deviceBatchMoveObserved(readback(), node.id, "read", draft.destinationGroupId))
    }

    @Test fun staleReadbackOrAnotherNodeCannotVerifyMove() {
        assertFalse(deviceBatchMoveObserved(readback(), node.id, "stale", draft.destinationGroupId))
        assertFalse(deviceBatchMoveObserved(readback(), "node//other", "read", draft.destinationGroupId))
        assertFalse(deviceBatchMoveObserved(readback(), node.id, "", draft.destinationGroupId))
    }

    @Test fun sourceGroupOrUnavailableMetadataCannotVerifyMove() {
        val originalGroup = readback().apply { getAsJsonObject("node").addProperty("meshid", node.meshid) }
        assertFalse(deviceBatchMoveObserved(originalGroup, node.id, "read", draft.destinationGroupId))
        assertFalse(deviceBatchMoveObserved(readback().apply { addProperty("noinfo", true) }, node.id, "read", draft.destinationGroupId))
    }
}
