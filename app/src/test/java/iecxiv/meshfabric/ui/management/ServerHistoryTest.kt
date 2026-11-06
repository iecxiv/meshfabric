package iecxiv.meshfabric.ui.management

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ServerHistoryTest {
    @Test fun timestampsAcceptSecondsMillisecondsAndIso() {
        fun time(value:String)=historyTime(JsonParser.parseString(value))
        assertEquals(1700000000000L,time("1700000000"))
        assertEquals(1700000000000L,time("1700000000000"))
        assertEquals(1700000000000L,time("\"2023-11-14T22:13:20Z\""))
        assertNull(time("\"invalid\""))
    }
    @Test fun absentCpuStaysAbsentAndMemoryUsesMib() {
        val points=serverHistory("""{"events":[{"time":1700000000,"mem":{"rss":1048576}}]}""")
        assertFalse(points.single().values.containsKey("cpu.0"))
        assertEquals(1.0,points.single().values.getValue("mem.rss"),0.00001)
    }
    @Test fun cpuObjectsAndTrafficProtocolsAreParsed() {
        val p=serverHistory("""{"events":[{"time":1700000000,"cpu":{"0":2.5},"traffic":{"relayIn":{"2":1048576},"relayOut":{"2":2097152}}}]}""").single()
        assertEquals(2.5,p.values.getValue("cpu.0"),0.00001)
        assertEquals(3.0,p.values.getValue("traffic.desktop"),0.00001)
        assertFalse(p.values.containsKey("traffic.files"))
    }
    @Test fun restartsMissingValuesAndPeersBreakLines() {
        fun p(t:Long,first:Boolean=false,value:Double?=1.0,server:String="a")=HistoryPoint(t,server,first,if(value==null) emptyMap() else mapOf("x" to value))
        val points=listOf(p(1),p(2),p(3,first=true),p(4,value=null),p(5),p(6,server="b"))
        assertEquals(listOf(2,1,1,1),historySegments(points,"x",false).map { it.size })
    }
    @Test fun logarithmicModeExcludesZeroWithoutInventingValues() {
        val points=listOf(HistoryPoint(1,"",false,mapOf("x" to 0.0)),HistoryPoint(2,"",false,mapOf("x" to 100.0)))
        val result=historySegments(points,"x",true)
        assertEquals(1,result.single().size)
        assertEquals(2.0,result.single().single().second,0.00001)
    }
}
