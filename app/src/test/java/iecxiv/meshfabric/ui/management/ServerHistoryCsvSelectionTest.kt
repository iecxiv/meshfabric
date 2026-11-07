package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ServerHistoryCsvSelectionTest {
    @Test fun selectedTrafficDirectionDoesNotExportTheOtherDirectionOrAggregate() {
        val point=HistoryPoint(1000,"server",false,mapOf("traffic.agent.in" to 2.0,"traffic.agent.out" to 8.0,"traffic.agent" to 10.0))
        val csv=serverHistoryCsv(listOf(point),listOf("traffic.agent.out"),3)
        assertTrue(csv.contains("traffic.agent.out_MiB"))
        assertFalse(csv.contains("traffic.agent.in_MiB"))
        assertFalse(csv.contains("traffic.agent_MiB"))
        assertTrue(csv.endsWith("\"8.0\"\r\n"))
    }
    @Test fun exportsOriginalValuesIncludingZeroRatherThanLogarithms() {
        val points=listOf(HistoryPoint(1000,"server",false,mapOf("cpu.0" to 100.0)),HistoryPoint(2000,"server",false,mapOf("cpu.0" to 0.0)))
        val csv=serverHistoryCsv(points,listOf("cpu.0"),8)
        assertTrue(csv.contains("\"100.0\"\r\n"))
        assertTrue(csv.endsWith("\"0.0\"\r\n"))
        assertTrue(csv.contains("\"8\""))
    }
}
