package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralMessagePolicyTest {
    private val node="node//a"
    private val meta=GeneralMeta("Equipo",emptyList(),16392,"Linux","mesh//a")
    private val inventory=listOf(MeshGroup(id=meta.mesh,name="Grupo",mtype=2) to listOf(MeshNode(id=node,name="Equipo",meshid=meta.mesh,conn=1)))
    private val draft=GeneralMessageDraft("Título","Mensaje + 100%\nRevisión ✓",meta.mesh)
    @Test fun sendsMessageboxToExactlyOneDevice() {
        val p=generalMessagePacket(node,"request",draft,meta,inventory)
        assertEquals("msg",p.get("action").asString);assertEquals("messagebox",p.get("type").asString)
        assertEquals(node,p.get("nodeid").asString);assertFalse(p.has("nodeids"))
    }
    @Test fun preservesLiteralTextAndUsesBoundedExpiry() {
        val p=generalMessagePacket(node,"request",draft,meta,inventory)
        assertEquals(draft.body,p.get("msg").asString);assertEquals(draft.title,p.get("title").asString)
        assertEquals(120000,p.get("timeout").asInt)
    }
    @Test fun bothSpecificRightsAreRequired() {
        for(rights in listOf(4L,8L,16384L)) assertFalse(generalMessageAvailable(node,meta.copy(rights=rights),inventory))
    }
    @Test fun amtConnectivityIsNotAnAgent() {
        val received=listOf(inventory.single().first to listOf(inventory.single().second.single().copy(conn=2)))
        assertFalse(generalMessageAvailable(node,meta,received))
    }
    @Test fun duplicateDeviceIsBlocked() {assertFalse(generalMessageAvailable(node,meta,listOf(inventory.single().first to inventory.single().second.let {it+it})))}
    @Test fun changedGroupInvalidatesReview() {assertTrue(runCatching {generalMessagePacket(node,"request",draft.copy(mesh="mesh//b"),meta,inventory)}.isFailure)}
    @Test fun emptyOrOversizedFieldsAreRejected() {
        assertFalse(generalMessageTextValid(" ","Texto"));assertFalse(generalMessageTextValid("Título"," "))
        assertFalse(generalMessageTextValid("x".repeat(129),"Texto"));assertFalse(generalMessageTextValid("Título","x".repeat(1025)))
    }
    @Test fun invalidControlCharactersAreRejected() {
        assertFalse(generalMessageTextValid("Título\n","Texto"));assertFalse(generalMessageTextValid("Título","bad\u0000text"))
    }
    @Test fun acknowledgementNeedsExactCorrelation() {
        val p=JsonObject().apply {addProperty("action","msg");addProperty("type","messagebox");addProperty("nodeid",node);addProperty("responseid","request");addProperty("result","ok")}
        assertEquals("accepted",generalMessageAck(p,node,"request"))
        assertNull(generalMessageAck(p,node,"other"));assertNull(generalMessageAck(p,"node//b","request"))
    }
    @Test fun unrelatedReplyIsNotAnAcknowledgement() {
        val p=JsonObject().apply {addProperty("action","msg");addProperty("type","toast");addProperty("responseid","request");addProperty("result","ok")}
        assertNull(generalMessageAck(p,node,"request"))
    }
    @Test fun explicitErrorIsRejectedNotAccepted() {
        val p=JsonObject().apply {addProperty("action","msg");addProperty("responseid","request");addProperty("error","denied")}
        assertEquals("rejected",generalMessageAck(p,node,"request"))
    }
    @Test fun missingAcknowledgementCannotBecomeSuccess() {
        assertNull(generalMessageAck(JsonObject().apply {addProperty("action","msg");addProperty("responseid","request")},node,"request"))
    }
}
