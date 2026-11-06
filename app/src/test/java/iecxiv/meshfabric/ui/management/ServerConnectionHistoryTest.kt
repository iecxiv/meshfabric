package iecxiv.meshfabric.ui.management

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ServerConnectionHistoryTest {
    @Test fun readsAllSixOfficialConnectionCounters() {
        val points=serverHistory("""{"events":[{"time":1700000000000,"conn":{"ca":12,"cu":2,"us":3,"rs":4,"am":5,"amc":6}}]}""")
        assertEquals(mapOf("conn.ca" to 12.0,"conn.cu" to 2.0,"conn.us" to 3.0,"conn.rs" to 4.0,"conn.am" to 5.0,"conn.amc" to 6.0),points.single().values)
    }
    @Test fun absentCountersAreNotInventedAsZero() {
        assertTrue(connectionHistoryValues(null).isEmpty())
        val values=connectionHistoryValues(JsonParser.parseString("""{"ca":0,"cu":null}"""))
        assertEquals(mapOf("conn.ca" to 0.0),values)
        assertFalse(values.containsKey("conn.amc"))
    }
    @Test fun malformedNegativeAndFractionalCountersAreRejected() {
        val values=connectionHistoryValues(JsonParser.parseString("""{"ca":-1,"cu":"2","us":1.5,"rs":true,"am":{},"amc":2}"""))
        assertEquals(mapOf("conn.amc" to 2.0),values)
    }
    @Test fun gapsRestartsAndZeroOnLogScaleBreakConnectionLines() {
        val points=listOf(
            HistoryPoint(1,"a",false,mapOf("conn.ca" to 2.0)),
            HistoryPoint(2,"a",false,emptyMap()),
            HistoryPoint(3,"a",false,mapOf("conn.ca" to 0.0)),
            HistoryPoint(4,"a",true,mapOf("conn.ca" to 4.0)),
            HistoryPoint(5,"b",false,mapOf("conn.ca" to 8.0)))
        assertEquals(listOf(1,1,1,1),historySegments(points,"conn.ca",false).map { it.size })
        assertEquals(listOf(1,1,1),historySegments(points,"conn.ca",true).map { it.size })
    }
}
