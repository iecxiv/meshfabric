package iecxiv.meshfabric.ui.management

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DeviceEventsScopeTest {
    private val node="node//device-a"
    private val scope=ServerEventsScope("https://server","user//tester",1,"verified")
    private fun response(id:String)=JsonParser.parseString("""{"action":"events","tag":"request","events":[{"action":"changenode","nodeid":"$id","msg":"changed"}]}""").asJsonObject
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
        val gate=ServerEventsReadGate()
        val attempt=gate.begin(scope,"request",ServerEventsQuery(nodeId=node))
        assertTrue(gate.receive(attempt,scope,response(node)))
        assertEquals(ServerEventsPhase.READY,gate.state.phase)
        assertEquals(node,gate.state.records.single().nodeId)
    }
    @Test fun rejectsForeignDeviceWithoutExposingItsRecords() {
        val gate=ServerEventsReadGate()
        val attempt=gate.begin(scope,"request",ServerEventsQuery(nodeId=node))
        assertTrue(gate.receive(attempt,scope,response("node//device-b")))
        assertEquals(ServerEventsPhase.INVALID,gate.state.phase)
        assertTrue(gate.state.records.isEmpty())
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
}
