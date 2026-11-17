package iecxiv.meshfabric.ui.management

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DeviceEventsScopeTest {
    private val node="node//device-a"
    private val scope=ServerEventsScope("https://server","user//tester",1,"verified")
    private fun response(id:String)=JsonParser.parseString("""{"action":"events","tag":"request","events":[{"action":"changenode","nodeid":"$id","msg":"changed"}]}""").asJsonObject
    private fun read(packet:JsonObject):ServerEventsSnapshot {
        val gate=ServerEventsReadGate()
        val attempt=gate.begin(scope,"request",ServerEventsQuery(nodeId=node))
        assertTrue(gate.receive(attempt,scope,packet))
        return gate.state
    }
    @Test fun requestIncludesDeviceAndKeepsFilterAndLimit() {
        val request=serverEventsRequest("request",ServerEventsQuery(ServerEventType.CHANGE,120,node))
        assertEquals(node,request.get("nodeid").asString)
        assertEquals(120,request.get("limit").asInt)
        assertEquals("changenode",request.get("filter").asString)
    }
    @Test fun generalRegistroRequestStaysUnscoped() {
        assertFalse(serverEventsRequest("request").has("nodeid"))
    }
    @Test fun acceptsOnlyTheRequestedDevice() {
        val result=read(response(node))
        assertEquals(ServerEventsPhase.READY,result.phase)
        assertEquals(node,result.records.single().nodeId)
    }
    @Test fun rejectsForeignDeviceWithoutExposingItsRecords() {
        val result=read(response("node//device-b"))
        assertEquals(ServerEventsPhase.INVALID,result.phase)
        assertTrue(result.records.isEmpty())
        assertEquals("IDENTITY;NODEID;STRING",result.issue?.safeCode())
    }
    @Test fun invalidatedAttemptCannotPopulateAnotherDevice() {
        val gate=ServerEventsReadGate()
        val old=gate.begin(scope,"request",ServerEventsQuery(nodeId=node))
        gate.begin(scope,"next",ServerEventsQuery(nodeId="node//device-b"))
        assertFalse(gate.receive(old,scope,response(node)))
        assertTrue(gate.state.records.isEmpty())
    }
    @Test fun filterAndLimitChangesKeepDeviceScope() {
        val next=ServerEventsQuery(nodeId=node).copy(type=ServerEventType.COMMAND,limit=250)
        assertEquals(node,serverEventsRequest("request",next).get("nodeid").asString)
    }
    @Test fun verifiedEnvelopeAllowsRowsWithoutRepeatedNodeId() {
        val packet=response(node)
        packet.addProperty("nodeid",node)
        packet.getAsJsonArray("events")[0].asJsonObject.remove("nodeid")
        val result=read(packet)
        assertEquals(ServerEventsPhase.READY,result.phase)
        assertEquals(node,result.records.single().nodeId)
    }
    @Test fun missingIdentityWithoutVerifiedEnvelopeIsRejected() {
        val packet=response(node)
        packet.getAsJsonArray("events")[0].asJsonObject.remove("nodeid")
        val result=read(packet)
        assertEquals(ServerEventsPhase.INVALID,result.phase)
        assertTrue(result.records.isEmpty())
        assertEquals("IDENTITY;NODEID;MISSING",result.issue?.safeCode())
    }
    @Test fun wrongEnvelopeIsRejectedEvenWhenRowsMatch() {
        val packet=response(node)
        packet.addProperty("nodeid","node//device-b")
        val result=read(packet)
        assertEquals(ServerEventsPhase.INVALID,result.phase)
        assertTrue(result.records.isEmpty())
    }
    @Test fun matchingEnvelopeDoesNotOverrideForeignRow() {
        val packet=response("node//device-b")
        packet.addProperty("nodeid",node)
        assertEquals(ServerEventsPhase.INVALID,read(packet).phase)
    }
    @Test fun shortRecordIdIsResolvedWithinRequestedScope() {
        val result=read(response("device-a"))
        assertEquals(ServerEventsPhase.READY,result.phase)
        assertEquals(node,result.records.single().nodeId)
    }
    @Test fun sameSuffixInAnotherDomainIsRejected() {
        assertEquals(ServerEventsPhase.INVALID,read(response("node/other/device-a")).phase)
    }
    @Test fun contradictoryNestedIdentityIsRejected() {
        val packet=response(node)
        packet.getAsJsonArray("events")[0].asJsonObject.add("node",JsonObject().apply {addProperty("_id","node//device-b")})
        assertEquals(ServerEventsPhase.INVALID,read(packet).phase)
    }
    @Test fun lateTagIsStillIgnored() {
        val gate=ServerEventsReadGate()
        val attempt=gate.begin(scope,"next",ServerEventsQuery(nodeId=node))
        val packet=response(node).apply {addProperty("nodeid",node)}
        assertFalse(gate.receive(attempt,scope,packet))
        assertEquals(ServerEventsPhase.LOADING,gate.state.phase)
    }
}
