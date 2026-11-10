package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ServerEventsQueryTest {
    private val scope=ServerEventsScope("server","actor",1,"rights")
    private fun packet(count:Int,tag:String="test")=JsonParser.parseString("{\"action\":\"events\",\"tag\":\""+tag+"\",\"events\":["+List(count) {"{\"msg\":123}"}.joinToString(",")+"]}").asJsonObject
    private fun decode(query:ServerEventsQuery,count:Int):ServerEventsSnapshot {
        val gate=ServerEventsReadGate();val attempt=gate.begin(scope,"test",query)
        assertTrue(gate.receive(attempt,scope,packet(count)));return gate.state
    }
    @Test fun visibleChoicesMatchRequestedCounts() {
        assertEquals(listOf(60,120,250,500,1000,0),serverEventLimitChoices)
        for(count in serverEventLimitChoices.filter {it>0}) assertEquals(count,serverEventsRequest("test",ServerEventsQuery(limit=count)).get("limit").asInt)
    }
    @Test fun everyTypeUsesOnlyItsProtocolFilter() {
        for(type in ServerEventType.entries) {
            val p=serverEventsRequest("test",ServerEventsQuery(type,120))
            if(type==ServerEventType.ALL) assertFalse(p.has("filter")) else assertEquals(type.wire,p.get("filter").asString)
            assertEquals("test",p.get("tag").asString)
        }
    }
    @Test fun unlimitedOmitsLimitButKeepsFilter() {
        val p=serverEventsRequest("test",ServerEventsQuery(ServerEventType.RELAY,null))
        assertFalse(p.has("limit"));assertEquals("relaylog",p.get("filter").asString)
    }
    @Test fun selectedFiniteBoundRejectsAnOversizedBatch() {
        val state=decode(ServerEventsQuery(limit=60),61)
        assertEquals(ServerEventsPhase.INVALID,state.phase);assertTrue(state.records.isEmpty())
    }
    @Test fun largerFiniteSelectionAcceptsMoreThanLegacyTwoHundred() {
        val state=decode(ServerEventsQuery(limit=1000),500)
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals(500,state.records.size)
    }
    @Test fun unlimitedAcceptsMoreThanOneThousandWithoutTruncation() {
        val state=decode(ServerEventsQuery(limit=null),1001)
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals(1001,state.records.size)
    }
    @Test fun unlimitedHasNoTenThousandRecordCeiling() {
        val state=decode(ServerEventsQuery(limit=null),10001)
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals(10001,state.records.size)
    }
    @Test fun changedSelectionRejectsPreviousAttemptAndTag() {
        val gate=ServerEventsReadGate();val first=gate.begin(scope,"old",ServerEventsQuery(limit=60))
        val next=gate.begin(scope,"new",ServerEventsQuery(ServerEventType.MANUAL,1000))
        assertFalse(gate.receive(first,scope,packet(1,"old")))
        assertFalse(gate.receive(next,scope,packet(1,"old")))
        assertTrue(gate.receive(next,scope,packet(1,"new")));assertEquals(ServerEventsPhase.READY,gate.state.phase)
    }
}
