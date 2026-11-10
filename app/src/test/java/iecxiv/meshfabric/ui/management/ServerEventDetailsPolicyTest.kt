package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class ServerEventDetailsPolicyTest {
    private fun decode(limit:Int?):ServerEventsSnapshot {
        val gate=ServerEventsReadGate();val scope=ServerEventsScope("server","actor",1,"rights")
        val attempt=gate.begin(scope,"test",ServerEventsQuery(limit=limit))
        gate.receive(attempt,scope,JsonParser.parseString("{\"action\":\"events\",\"tag\":\"test\",\"events\":[{\"msg\":\""+"x".repeat(5000)+"\"}]}").asJsonObject)
        return gate.state
    }
    @Test fun unlimitedMessageHasNoFixedDescriptionLength() {
        val state=decode(null);assertEquals(ServerEventsPhase.READY,state.phase);assertEquals("x".repeat(5000),state.records.single().message)
    }
    @Test fun finiteQueryKeepsItsExistingMessageProtection() {
        val state=decode(60);assertEquals(ServerEventsPhase.INVALID,state.phase);assertEquals(ServerEventsIssueKind.FIELD_LENGTH,state.issue?.kind)
    }
    @Test fun detailsUseOnlyExplicitSafeFields() {
        val details=serverEventDetails(JsonParser.parseString("{\"msgid\":107,\"protocol\":2,\"password\":\"secret\",\"cookie\":\"cookie-secret\",\"token\":\"token-secret\",\"auth\":\"auth-secret\"}").asJsonObject)
        assertEquals(mapOf("msgid" to "107","protocol" to "2"),details);assertFalse(details.toString().contains("secret"))
    }
    @Test fun nestedUnknownPayloadIsNotExposed() {
        assertTrue(serverEventDetails(JsonParser.parseString("{\"ip\":{\"secret\":\"hidden\"},\"unknown\":\"hidden\"}").asJsonObject).isEmpty())
    }
}
