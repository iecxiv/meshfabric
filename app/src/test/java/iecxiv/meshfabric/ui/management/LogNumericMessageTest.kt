package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class LogNumericMessageTest {
    private val scope=ServerEventsScope("server","actor",1,"rights")
    private fun packet(rows:String,tag:String="test")=JsonParser.parseString("{\"action\":\"events\",\"tag\":\""+tag+"\",\"events\":"+rows+"}").asJsonObject
    private fun decode(rows:String):ServerEventsSnapshot {
        val gate=ServerEventsReadGate();val attempt=gate.begin(scope,"test")
        assertTrue(gate.receive(attempt,scope,packet(rows)));return gate.state
    }
    @Test fun mixedTextAndNumericMessagesKeepEveryRecord() {
        val state=decode("[{\"msg\":123},{\"msg\":\"text message\"}]")
        assertEquals(ServerEventsPhase.READY,state.phase)
        assertEquals(listOf("123","text message"),state.records.map {it.message})
        assertNull(state.issue)
    }
    @Test fun largeIntegerKeepsExactDigits() {
        val number="900719925474099312345678901234567890"
        val state=decode("[{\"msg\":"+number+"}]")
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals(number,state.records.single().message)
    }
    @Test fun scientificNotationKeepsLiteralRepresentation() {
        val state=decode("[{\"msg\":1.25e+40}]")
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals("1.25e+40",state.records.single().message)
    }
    @Test fun nullAndMissingMessageRemainSupported() {
        val state=decode("[{\"msg\":null},{}]")
        assertEquals(ServerEventsPhase.READY,state.phase);assertEquals(listOf("",""),state.records.map {it.message})
    }
    @Test fun booleanArrayAndObjectMessagesStillRejectTheWholeBatch() {
        for(value in listOf("true","[]","{}")) {
            val state=decode("[{\"msg\":123},{\"msg\":"+value+"}]")
            assertEquals(ServerEventsPhase.INVALID,state.phase);assertTrue(state.records.isEmpty())
            assertEquals(ServerEventsIssueKind.FIELD_TYPE,state.issue?.kind);assertEquals(ServerEventsField.MSG,state.issue?.field)
        }
    }
    @Test fun numericValuesInOtherTextFieldsAreStillRejected() {
        for(key in listOf("action","username","nodeid","nodename")) {
            val state=decode("[{\"msg\":123,\""+key+"\":456}]")
            assertEquals(ServerEventsPhase.INVALID,state.phase);assertTrue(state.records.isEmpty())
            assertEquals(ServerEventsIssueKind.FIELD_TYPE,state.issue?.kind)
        }
    }
    @Test fun numericMessageRetainsTheTextLengthBound() {
        val state=decode("[{\"msg\":"+"1".repeat(4097)+"}]")
        assertEquals(ServerEventsPhase.INVALID,state.phase);assertTrue(state.records.isEmpty())
        assertEquals(ServerEventsIssueKind.FIELD_LENGTH,state.issue?.kind)
    }
    @Test fun mismatchedTagCannotAcceptNumericHistory() {
        val gate=ServerEventsReadGate();val attempt=gate.begin(scope,"test")
        assertFalse(gate.receive(attempt,scope,packet("[{\"msg\":123}]","other")))
        assertEquals(ServerEventsPhase.LOADING,gate.state.phase);assertTrue(gate.state.records.isEmpty())
    }
}
