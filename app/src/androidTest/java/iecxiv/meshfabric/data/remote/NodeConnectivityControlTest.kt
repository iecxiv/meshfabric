package iecxiv.meshfabric.data.remote
import org.junit.Assert.*
import org.junit.Test
class NodeConnectivityControlTest {
    private fun ingest(client:MeshCentralClient,text:String) {
        val method=MeshCentralClient::class.java.getDeclaredMethod("handleControlMessage",String::class.java)
        method.isAccessible=true; method.invoke(client,text)
    }
    private fun withClient(conn:Int,body:(MeshCentralClient,MutableList<String>)->Unit) {
        val client=MeshCentralClient(); val notices=mutableListOf<String>()
        try {
            ingest(client,"""{"action":"meshes","meshes":[{"_id":"mesh//g","name":"Group"}]}""")
            ingest(client,"""{"action":"nodes","nodes":{"mesh//g":[{"_id":"node//a","name":"Device","conn":$conn}]}}""")
            client.onNodeConnectedListener={notices.add(it)}
            body(client,notices)
        } finally {client.disconnect()}
    }
    private fun node(client:MeshCentralClient)=client.groupedDevices.value.flatMap {it.second}.single()
    @Test fun unrelatedNodeEventCannotBringOfflineDeviceOnline() = withClient(0) {client,notices ->
        ingest(client,"""{"action":"event","event":{"action":"agentconsole","nodeid":"node//a","conn":1}}""")
        assertEquals(0,node(client).conn); assertTrue(notices.isEmpty())
    }
    @Test fun nestedZeroConnectionRemainsDisconnected() = withClient(1) {client,notices ->
        ingest(client,"""{"action":"event","event":{"action":"nodeconnect","nodeid":"node//a","conn":0}}""")
        assertEquals(0,node(client).conn); assertTrue(notices.isEmpty())
    }
    @Test fun repeatedPrimitiveNodeConnectNotifiesOnlyOnce() = withClient(0) {client,notices ->
        repeat(2) {ingest(client,"""{"action":"nodeconnect","node":"node//a","conn":1}""")}
        assertEquals(1,node(client).conn); assertEquals(listOf("node//a"),notices)
    }
    @Test fun partialChangeDoesNotEraseExistingDeviceState() = withClient(1) {client,notices ->
        ingest(client,"""{"action":"changenode","node":{"_id":"node//a","name":"Changed"}}""")
        assertEquals(1,node(client).conn); assertEquals("Device",node(client).name); assertTrue(notices.isEmpty())
    }
    @Test fun cleanedNodeIdUpdatesCanonicalDevice() = withClient(0) {client,notices ->
        ingest(client,"""{"action":"event","event":{"action":"nodeconnect","nodeid":"a","conn":1}}""")
        assertEquals(1,node(client).conn); assertEquals(listOf("node//a"),notices)
    }
    @Test fun agentRecoveryAndRemainingTransportAreDistinguished() = withClient(2) {client,notices ->
        ingest(client,"""{"action":"nodeconnect","nodeid":"node//a","conn":3}""")
        ingest(client,"""{"action":"event","event":{"action":"nodedisconnect","node":{"_id":"node//a"},"conn":2}}""")
        assertEquals(2,node(client).conn); assertEquals(listOf("node//a"),notices)
    }
}
