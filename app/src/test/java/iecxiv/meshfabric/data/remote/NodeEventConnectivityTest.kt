package iecxiv.meshfabric.data.remote
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
class NodeEventConnectivityTest {
    private fun packet(text:String)=Gson().fromJson(text,JsonObject::class.java)
    @Test fun unrelatedEventsCannotChangeConnectivity() {
        assertNull(NodeEventConnectivity.connection(packet("""{"action":"agentconsole","nodeid":"node//a","conn":1}""")))
    }
    @Test fun explicitZeroIsNotConvertedToConnected() {
        assertEquals(0,NodeEventConnectivity.connection(packet("""{"action":"nodeconnect","conn":0}""")))
    }
    @Test fun missingConnectionDoesNotInventConnectivity() {
        assertNull(NodeEventConnectivity.connection(packet("""{"action":"nodeconnect","nodeid":"node//a"}""")))
        assertNull(NodeEventConnectivity.connection(packet("""{"action":"changenode","node":{"_id":"node//a","name":"Changed"}}""")))
    }
    @Test fun malformedConnectionDoesNotOverwriteState() {
        listOf("null","true","-1","1.5","2147483648","\"1\"").forEach {
            assertNull(NodeEventConnectivity.connection(packet("""{"action":"nodeconnect","conn":$it}""")))
        }
    }
    @Test fun disconnectWithoutMaskDefaultsToOffline() {
        assertEquals(0,NodeEventConnectivity.connection(packet("""{"action":"nodedisconnect"}""")))
    }
    @Test fun disconnectPreservesExplicitRemainingTransports() {
        assertEquals(2,NodeEventConnectivity.connection(packet("""{"action":"nodedisconnect","conn":2}""")))
    }
    @Test fun nestedNodeConnectionAndIdAreReadSafely() {
        val p=packet("""{"action":"changenode","node":{"_id":"node//a","conn":3}}""")
        assertEquals("node//a",NodeEventConnectivity.nodeId(p)); assertEquals(3,NodeEventConnectivity.connection(p))
    }
    @Test fun explicitOuterMaskTakesPriority() {
        assertEquals(0,NodeEventConnectivity.connection(packet("""{"action":"nodeconnect","conn":0,"node":{"_id":"node//a","conn":1}}""")))
    }
    @Test fun primitiveAndMissingNodeIdsAreHandled() {
        assertEquals("node//a",NodeEventConnectivity.nodeId(packet("""{"node":"node//a"}""")))
        assertNull(NodeEventConnectivity.nodeId(packet("""{"node":7}""")))
        assertNull(NodeEventConnectivity.nodeId(packet("""{"node":{}}""")))
    }
    @Test fun aliasesPreserveDomainAndStripOnlyOnePrefix() {
        assertTrue(NodeEventConnectivity.keys("domain/a").contains("node/domain/a"))
        assertTrue(NodeEventConnectivity.keys("a").contains("node//a"))
        assertTrue(NodeEventConnectivity.keys("node//node/a").contains("node/a"))
        assertFalse(NodeEventConnectivity.keys("node//node/a").contains("a"))
    }
    @Test fun notifyOnlyForOfflineRecoveryOrNewAgentTransport() {
        assertTrue(NodeEventConnectivity.becameReachable(0,1,"nodeconnect"))
        assertTrue(NodeEventConnectivity.becameReachable(2,3,"nodeconnect"))
        assertFalse(NodeEventConnectivity.becameReachable(1,1,"nodeconnect"))
        assertFalse(NodeEventConnectivity.becameReachable(1,3,"changenode"))
        assertFalse(NodeEventConnectivity.becameReachable(1,0,"nodeconnect"))
        assertFalse(NodeEventConnectivity.becameReachable(0,2,"nodedisconnect"))
    }
}
