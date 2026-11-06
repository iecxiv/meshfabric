package iecxiv.meshfabric.ui.management

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ServerGeneralPresentationTest {
    @Test fun errorLogIsUnwrappedAndLineBreaksPreserved() {
        val raw=Gson().toJson(mapOf("action" to "servererrors","data" to "Error\r\n at server.js:10"))
        assertEquals("Error\n at server.js:10",serverReadableResult(raw,"servererrors").text)
    }
    @Test fun configStringIsDecodedAndIndented() {
        val raw=Gson().toJson(mapOf("action" to "serverconfig","data" to "{\"settings\":{\"port\":443}}"))
        val result=serverReadableResult(raw,"serverconfig")
        assertFalse(result.invalidJson)
        assertEquals("{\n  \"settings\": {\n    \"port\": 443\n  }\n}",result.text)
    }
    @Test fun missingPayloadDoesNotMeanEmptyErrorLog() {
        assertTrue(serverReadableResult("{\"error\":\"Denied\"}","servererrors").missing)
        assertEquals("",serverReadableResult("{\"data\":\"\"}","servererrors").text)
        assertTrue(serverReadableResult("{\"data\":\"invalid json\"}","serverconfig").invalidJson)
    }
    @Test fun memoryUsesTotalMinusFreeAndPreservesZeroCounters() {
        val stats=serverOverview("""{"totalmem":1000,"freemem":250,"availablemem":400,"values":{"ServerState":{"AgentSessions":0}}}""")!!
        assertEquals(0.75,stats.memoryFraction!!,0.000001)
        assertEquals(400.0,stats.available!!,0.0)
        assertEquals(0.0,stats.counts.getValue("AgentSessions"),0.0)
        assertFalse(stats.counts.containsKey("ConnectedUsers"))
    }
    @Test fun invalidOrMissingMemoryIsNotInventedAsZeroUsage() {
        assertNull(serverOverview("""{"totalmem":100,"freemem":200}""")!!.memoryFraction)
        assertNull(serverOverview("""{"totalmem":100}""")!!.memoryFraction)
        assertNull(serverOverview("invalid"))
    }
    @Test fun loadRingHasAnExplicitAutoScaleNotCpuPercentage() {
        val stats=serverOverview("""{"cpuavg":[2.4,1.5,0.5]}""")!!
        assertEquals(3.0,stats.loadScale,0.0)
        assertEquals(0.8,stats.loadFraction!!,0.000001)
        assertNull(serverOverview("{}")!!.loadFraction)
    }
}
