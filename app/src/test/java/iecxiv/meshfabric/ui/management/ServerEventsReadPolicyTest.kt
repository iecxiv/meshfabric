package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ServerEventsReadPolicyTest {
    private val scope=ServerEventsScope("https://server","user/a/me",1,"rights-version-1")
    private fun packet(body:String)=JsonParser.parseString(body).asJsonObject
    @Test fun requestIsBoundedReadOnlyAndTagged() {
        val p=serverEventsRequest("read-1")
        assertEquals("events",p.get("action").asString)
        assertEquals("read-1",p.get("tag").asString)
        assertEquals(200,p.get("limit").asInt)
        assertEquals(setOf("action","tag","limit"),p.keySet())
    }
    @Test fun emptyHistoryRequiresValidMatchedArray() {
        val g=ServerEventsReadGate();val attempt=g.begin(scope,"read-1")
        assertTrue(g.receive(attempt,scope,packet("""{"action":"events","tag":"read-1","events":[]}""")))
        assertEquals(ServerEventsPhase.READY,g.state.phase);assertTrue(g.state.records.isEmpty())
    }
    @Test fun missingArrayIsInvalidNotEmptyHistory() {
        val g=ServerEventsReadGate();val attempt=g.begin(scope,"read-1")
        g.receive(attempt,scope,packet("""{"action":"events","tag":"read-1"}"""))
        assertEquals(ServerEventsPhase.INVALID,g.state.phase)
    }
    @Test fun staleTagAndUnrelatedActionCannotCompleteRead() {
        val g=ServerEventsReadGate();val attempt=g.begin(scope,"read-1")
        assertFalse(g.receive(attempt,scope,packet("""{"action":"events","tag":"old","events":[]}""")))
        assertFalse(g.receive(attempt,scope,packet("""{"action":"event","tag":"read-1","events":[]}""")))
        assertEquals(ServerEventsPhase.LOADING,g.state.phase)
    }
    @Test fun timeoutRejectsLateReplyAndDoesNotClaimDenial() {
        val g=ServerEventsReadGate();val attempt=g.begin(scope,"read-1");g.timeout(attempt)
        assertFalse(g.receive(attempt,scope,packet("""{"action":"events","tag":"read-1","events":[]}""")))
        assertEquals(ServerEventsPhase.TIMEOUT,g.state.phase)
    }
    @Test fun changedIdentitySessionOrAuthorityClearsAndInvalidates() {
        listOf(scope.copy(server="other"),scope.copy(actor="user/a/other"),scope.copy(session=2),scope.copy(authority="changed")).forEach {other->
            val g=ServerEventsReadGate();val attempt=g.begin(scope,"read-1")
            assertFalse(g.receive(attempt,other,packet("""{"action":"events","tag":"read-1","events":[]}""")))
            assertEquals(ServerEventsPhase.INVALIDATED,g.state.phase);assertTrue(g.state.records.isEmpty())
        }
    }
    @Test fun explicitDenialIsDifferentFromMalformedOrSilentRead() {
        val g=ServerEventsReadGate();val attempt=g.begin(scope,"read-1")
        g.receive(attempt,scope,packet("""{"action":"events","tag":"read-1","result":"Denied"}"""))
        assertEquals(ServerEventsPhase.DENIED,g.state.phase)
    }
    @Test fun oversizedOrMalformedHistoryIsRejectedWithoutTruncation() {
        listOf("[null]","["+List(201) {"{}"}.joinToString(",")+"]").forEach {rows->
            val g=ServerEventsReadGate();val attempt=g.begin(scope,"read-1")
            g.receive(attempt,scope,packet("{\"action\":\"events\",\"tag\":\"read-1\",\"events\":$rows}"))
            assertEquals(ServerEventsPhase.INVALID,g.state.phase)
        }
    }
    @Test fun recordsUseServerTimeAndNeverInventDeviceOrActorNames() {
        val g=ServerEventsReadGate();val attempt=g.begin(scope,"read-1")
        g.receive(attempt,scope,packet("""{"action":"events","tag":"read-1","events":[{"time":"bad","nodeid":"node/a/1"},{"time":"2026-01-01T00:00:00Z","msg":"hello"}]}"""))
        assertEquals(ServerEventsPhase.READY,g.state.phase)
        assertNotNull(g.state.records[0].time);assertNull(g.state.records[1].time)
        assertEquals("",g.state.records[1].nodeName);assertEquals("",g.state.records[1].actor)
        g.invalidate();assertTrue(g.state.records.isEmpty())
    }
}
