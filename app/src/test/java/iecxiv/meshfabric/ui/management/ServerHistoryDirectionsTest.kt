package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ServerHistoryDirectionsTest {
    @Test fun incomingAndOutgoingStaySeparateInMiB() {
        val values=serverHistory("""{"events":[{"time":1700000000000,"traffic":{"AgentCtrlIn":[1048576,2097152],"AgentCtrlOut":[4194304],"httpIn":1048576,"httpOut":2097152,"relayIn":[0,1048576,2097152,0,0,3145728],"relayOut":[0,4194304,5242880,0,0,6291456]}}]}""").single().values
        mapOf("agent.in" to 3.0,"agent.out" to 4.0,"http.in" to 1.0,"http.out" to 2.0,"terminal.in" to 1.0,"terminal.out" to 4.0,"desktop.in" to 2.0,"desktop.out" to 5.0,"files.in" to 3.0,"files.out" to 6.0).forEach { (key,expected)->
            assertEquals(expected,values.getValue("traffic.$key"),0.000001)
        }
        assertEquals(7.0,values.getValue("traffic.agent"),0.000001)
    }
    @Test fun missingDirectionDoesNotBecomeZero() {
        val values=serverHistory("""{"events":[{"time":1700000000000,"traffic":{"AgentCtrlIn":0,"httpOut":-1,"relayIn":[0,null,0]}}]}""").single().values
        assertEquals(0.0,values.getValue("traffic.agent.in"),0.0)
        assertEquals(0.0,values.getValue("traffic.desktop.in"),0.0)
        assertFalse(values.containsKey("traffic.agent.out"))
        assertFalse(values.containsKey("traffic.terminal.in"))
        assertFalse(values.containsKey("traffic.http.out"))
    }
    @Test fun periodsAcceptOnlySupportedValuesAndDefaultToThreeHours() {
        serverHistoryPeriods.forEach { assertEquals(it,normalizedHistoryHours(it)) }
        listOf(-1,0,1,6,8760).forEach { assertEquals(3,normalizedHistoryHours(it)) }
    }
}
