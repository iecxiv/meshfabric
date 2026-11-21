package iecxiv.meshfabric.data.remote
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
class NodeSnapshotControlTest {
    private val gson=Gson()
    private fun ingest(client:MeshCentralClient,text:String) {
        val method=MeshCentralClient::class.java.getDeclaredMethod("handleControlMessage",String::class.java)
        method.isAccessible=true;method.invoke(client,text)
    }
    private fun requests(client:MeshCentralClient):NodeSnapshotRequests {
        val field=MeshCentralClient::class.java.getDeclaredField("nodeSnapshots");field.isAccessible=true
        return field.get(client) as NodeSnapshotRequests
    }
    private fun reply(client:MeshCentralClient,packet:JsonObject,payload:String) {
        packet.add("nodes",gson.fromJson(payload,JsonElement::class.java));ingest(client,packet.toString())
    }
    private fun nodes(client:MeshCentralClient)=client.groupedDevices.value.flatMap {it.second}.associateBy {it.id}
    private fun withClient(body:(MeshCentralClient)->Unit) {
        val client=MeshCentralClient()
        try {
            ingest(client,"""{"action":"meshes","meshes":[{"_id":"mesh//g","name":"Group"}]}""")
            ingest(client,"""{"action":"nodes","nodes":{"mesh//g":[{"_id":"node//a","name":"A","conn":1},{"_id":"node//b","name":"B","conn":1}]}}""")
            body(client)
        } finally {client.disconnect()}
    }
    @Test fun completeSnapshotRemovesObsoleteDevices() = withClient {client ->
        reply(client,requests(client).request(),"""{"mesh//g":[{"_id":"node//b","name":"B","conn":0}]}""")
        assertEquals(setOf("node//b"),nodes(client).keys);assertEquals(0,nodes(client).getValue("node//b").conn)
    }
    @Test fun uncorrelatedFilteredReplyDoesNotRemoveOtherDevices() = withClient {client ->
        ingest(client,"""{"action":"nodes","responseid":"external-filter","nodes":{"mesh//g":[{"_id":"node//b","name":"Updated","conn":0}]}}""")
        assertEquals(setOf("node//a","node//b"),nodes(client).keys)
    }
    @Test fun completeEmptySnapshotClearsDevices() = withClient {client ->
        reply(client,requests(client).request(),"{}");assertTrue(nodes(client).isEmpty())
    }
    @Test fun malformedCompleteSnapshotLeavesAllDevicesUntouched() = withClient {client ->
        reply(client,requests(client).request(),"""[{"_id":"node//b","name":"Changed"},null]""")
        assertEquals(setOf("node//a","node//b"),nodes(client).keys);assertEquals("B",nodes(client).getValue("node//b").name)
    }
    @Test fun olderReplyCannotResurrectRemovedDevices() = withClient {client ->
        val old=requests(client).request();val newer=requests(client).request()
        reply(client,newer,"""{"mesh//g":[{"_id":"node//b","name":"B"}]}""")
        reply(client,old,"""{"mesh//g":[{"_id":"node//a","name":"A"}]}""")
        assertEquals(setOf("node//b"),nodes(client).keys)
    }
    @Test fun previousConnectionReplyIsIgnored() = withClient {client ->
        val old=requests(client).request();requests(client).reset();reply(client,old,"{}")
        assertEquals(setOf("node//a","node//b"),nodes(client).keys)
    }
    @Test fun completeSnapshotUpdatesNameGroupAndConnection() = withClient {client ->
        reply(client,requests(client).request(),"""{"mesh//h":[{"_id":"node//b","name":"Renamed","conn":0}]}""")
        val node=nodes(client).values.single();assertEquals("Renamed",node.name);assertEquals("mesh//h",node.meshid);assertEquals(0,node.conn)
    }
    @Test fun correlatedButScopedOrErrorReplyCannotClearDevices() = withClient {client ->
        reply(client,requests(client).request().apply {addProperty("meshid","mesh//g")},"{}")
        reply(client,requests(client).request().apply {addProperty("result","error")},"{}")
        assertEquals(setOf("node//a","node//b"),nodes(client).keys)
    }
}
