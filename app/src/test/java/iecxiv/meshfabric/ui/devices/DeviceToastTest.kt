package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceToastTest {
    private val node = "node//one"
    private val mesh = "mesh//one"
    private val meta = GeneralMeta("Device", emptyList(), 16392L, "Windows", mesh)
    private val draft = GeneralMessageDraft("Title", "Body", mesh)
    private fun inventory(conn: Int = 1) = listOf(MeshGroup(id = mesh, mtype = 2) to listOf(MeshNode(id = node, meshid = mesh, conn = conn)))
    private fun ack(result: String = "ok") = JsonObject().apply { addProperty("action", "toast"); addProperty("responseid", "request"); addProperty("result", result) }

    @Test fun acceptsUnicodeAndMultilineMessages() {
        assertTrue(generalToastTextValid("Aviso á", "Primera línea\nSegunda línea"))
    }
    @Test fun titleLengthIsBounded() {
        assertTrue(generalToastTextValid("a".repeat(160), "body"))
        assertFalse(generalToastTextValid("a".repeat(161), "body"))
    }
    @Test fun bodyLengthIsBounded() {
        assertTrue(generalToastTextValid("title", "a".repeat(512)))
        assertFalse(generalToastTextValid("title", "a".repeat(513)))
    }
    @Test fun rejectsBlankAndUnsafeText() {
        assertFalse(generalToastTextValid("", "body"))
        assertFalse(generalToastTextValid("title", " "))
        assertFalse(generalToastTextValid("title\nline", "body"))
        assertFalse(generalToastTextValid("title", "a\u0000b"))
    }
    @Test fun notificationPacketUsesToastAndExactlyOneDevice() {
        val packet = generalToastPacket(node, "request", draft, meta, inventory())
        assertEquals(setOf("action", "nodeids", "title", "msg", "responseid"), packet.entrySet().map { it.key }.toSet())
        assertEquals("toast", packet.get("action").asString)
        assertEquals(1, packet.getAsJsonArray("nodeids").size())
        assertEquals(node, packet.getAsJsonArray("nodeids")[0].asString)
        assertEquals(draft.title, packet.get("title").asString)
        assertEquals(draft.body, packet.get("msg").asString)
    }
    @Test fun changedGroupPreventsPacketCreation() {
        assertTrue(runCatching { generalToastPacket(node, "request", draft.copy(mesh = "mesh//other"), meta, inventory()) }.isFailure)
    }
    @Test fun offlineAgentPreventsPacketCreation() {
        assertTrue(runCatching { generalToastPacket(node, "request", draft, meta, inventory(conn = 0)) }.isFailure)
    }
    @Test fun reusesTheExistingMessagePermissionPolicy() {
        for (rights in listOf(0L, 8L, 16384L)) assertTrue(runCatching { generalToastPacket(node, "request", draft, meta.copy(rights = rights), inventory()) }.isFailure)
    }
    @Test fun acknowledgementsMustMatchTheActionAndRequest() {
        assertNull(generalToastAck(ack().apply { addProperty("action", "msg") }, node, "request"))
        assertNull(generalToastAck(ack(), node, "other"))
    }
    @Test fun acknowledgementsCannotReferToOtherOrMultipleDevices() {
        assertNull(generalToastAck(ack().apply { addProperty("nodeid", "node//other") }, node, "request"))
        assertNull(generalToastAck(ack().apply { add("nodeids", JsonArray().apply { add(node); add("node//other") }) }, node, "request"))
    }
    @Test fun ambiguousRepliesDoNotProveAcceptance() {
        for (result in listOf("", "queued", "pending")) assertNull(generalToastAck(ack(result), node, "request"))
    }
    @Test fun distinguishesAcceptanceAndRejection() {
        assertEquals("accepted", generalToastAck(ack(), node, "request"))
        assertEquals("rejected", generalToastAck(ack("denied"), node, "request"))
        assertEquals("rejected", generalToastAck(ack().apply { addProperty("error", "denied") }, node, "request"))
    }
    @Test fun existingMessageBoxPacketIsNotChanged() {
        val packet = generalMessagePacket(node, "request", draft, meta, inventory())
        assertEquals("msg", packet.get("action").asString)
        assertEquals("messagebox", packet.get("type").asString)
        assertTrue(packet.has("timeout"))
    }
    @Test fun missingAcknowledgementRemainsUnconfirmed() {
        assertEquals(DeviceBatchPowerPhase.UNCONFIRMED, deviceBatchNotificationResult(GeneralMessageState(phase = "sent"), true))
        assertEquals(DeviceBatchPowerPhase.ACCEPTED, deviceBatchNotificationResult(GeneralMessageState(phase = "accepted"), true))
        assertEquals(DeviceBatchPowerPhase.NOT_SENT, deviceBatchNotificationResult(GeneralMessageState(phase = "error", code = "changed"), false))
    }
}
