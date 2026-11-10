package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class TraceSourcesPolicyTest {
    private fun decode(fields:String)=decodeTraceSources(JsonParser.parseString("{\"action\":\"traceinfo\""+fields+"}").asJsonObject)
    @Test fun explicitNullIsKnownEmptyState() {assertEquals(TraceSourcesRead.Known(emptySet()),decode(",\"traceSources\":null"))}
    @Test fun emptyArrayIsKnownEmptyState() {assertEquals(TraceSourcesRead.Known(emptySet()),decode(",\"traceSources\":[]"))}
    @Test fun stringArrayPreservesKnownAndUnknownNames() {assertEquals(TraceSourcesRead.Known(setOf("agent","future-source")),decode(",\"traceSources\":[\"agent\",\"future-source\",\"agent\"]"))}
    @Test fun absentFieldIsNotEmptyState() {assertEquals(TraceSourcesRead.Missing,decode(""))}
    @Test fun malformedTypesAreRejectedWithoutPartialAcceptance() {
        for(value in listOf("{}","true","123","\"*\"","[\"agent\",123]","[null]","[\"\"]")) assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":"+value))
    }
    @Test fun excessiveCountAndNameLengthAreRejected() {
        assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":["+List(257) {"\"agent\""}.joinToString(",")+"]"))
        assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":[\""+"x".repeat(101)+"\"]"))
    }
    @Test fun errorReplyCannotBeMistakenForNoActiveSources() {
        assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":null,\"error\":\"denied\""))
        assertEquals(TraceSourcesRead.Invalid,decode(",\"traceSources\":null,\"result\":false"))
    }
    @Test fun unrelatedActionsCannotSupplyCurrentSources() {
        assertEquals(TraceSourcesRead.Invalid,decodeTraceSources(JsonParser.parseString("{\"action\":\"userinfo\",\"traceSources\":null}").asJsonObject))
    }
}
