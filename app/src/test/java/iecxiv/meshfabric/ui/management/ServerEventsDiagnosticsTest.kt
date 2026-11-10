package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ServerEventsDiagnosticsTest {
    private fun receive(payload:String):ServerEventsSnapshot {
        val gate=ServerEventsReadGate();val scope=ServerEventsScope("server","actor",1,"rights")
        val attempt=gate.begin(scope,"test")
        assertTrue(gate.receive(attempt,scope,JsonParser.parseString(payload).asJsonObject))
        return gate.state
    }
    @Test fun incompatibleFieldReportsOnlySafeMetadata() {
        val result=receive("{\"action\":\"events\",\"tag\":\"test\",\"events\":[{\"msg\":{\"secret\":\"do-not-log\"}}]}")
        assertEquals(ServerEventsPhase.INVALID,result.phase)
        assertEquals(ServerEventsIssueKind.FIELD_TYPE,result.issue?.kind)
        assertEquals(ServerEventsField.MSG,result.issue?.field)
        assertEquals(ServerEventsValueType.OBJECT,result.issue?.type)
        assertFalse(result.issue!!.safeCode().contains("do-not-log"))
        assertTrue(result.records.isEmpty())
    }
    @Test fun fieldLengthDoesNotTruncateOrAcceptPartialHistory() {
        val text="x".repeat(4097)
        val result=receive("{\"action\":\"events\",\"tag\":\"test\",\"events\":[{\"msg\":\"ok\"},{\"msg\":\""+text+"\"}]}")
        assertEquals(ServerEventsIssueKind.FIELD_LENGTH,result.issue?.kind)
        assertEquals(4097,result.issue?.length)
        assertTrue(result.records.isEmpty())
    }
    @Test fun oversizedHistoryRetainsTheTwoHundredRecordLimit() {
        val rows=List(201) {"{}"}.joinToString(",")
        val result=receive("{\"action\":\"events\",\"tag\":\"test\",\"events\":["+rows+"]}")
        assertEquals(ServerEventsPhase.INVALID,result.phase)
        assertEquals(ServerEventsIssueKind.HISTORY_SHAPE,result.issue?.kind)
        assertEquals(201,result.issue?.length)
    }
}
