package iecxiv.meshfabric.data.remote
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
class MeshSnapshotControlTest {
    private val gson=Gson()
    private fun ingest(client:MeshCentralClient,text:String) {
        val method=MeshCentralClient::class.java.getDeclaredMethod("handleControlMessage",String::class.java)
        method.isAccessible=true;method.invoke(client,text)
    }
    private fun field(client:MeshCentralClient,name:String):Any {
        val field=MeshCentralClient::class.java.getDeclaredField(name);field.isAccessible=true;return requireNotNull(field.get(client))
    }
    private fun requests(client:MeshCentralClient)=field(client,"meshSnapshots") as MeshSnapshotRequests
    private fun groups(client:MeshCentralClient)=(field(client,"meshesMap") as Map<*,*>).keys.toSet()
    private fun nodes(client:MeshCentralClient)=client.groupedDevices.value.flatMap {it.second}.associateBy {it.id}
    private fun name(client:MeshCentralClient,id:String)=client.groupedDevices.value.first {it.first.id==id}.first.name
    private fun reply(client:MeshCentralClient,packet:JsonObject,payload:String) {
        packet.add("meshes",gson.fromJson(payload,JsonElement::class.java));ingest(client,packet.toString())
    }
    private fun withClient(body:(MeshCentralClient)->Unit) {
        val client=MeshCentralClient()
        try {
            ingest(client,"""{"action":"meshes","meshes":[{"_id":"mesh//g","name":"G"},{"_id":"mesh//h","name":"H"}]}""")
            ingest(client,"""{"action":"nodes","nodes":{"mesh//g":[{"_id":"node//a","name":"A","conn":1}],"mesh//h":[{"_id":"node//b","name":"B","conn":0}]}}""")
            body(client)
        } finally {client.disconnect()}
    }
    @Test fun completeSnapshotDropsGroupsButNotDevices() = withClient {client ->
        val original=nodes(client);reply(client,requests(client).request(),"""[{"_id":"mesh//g","name":"Renamed"}]""")
        assertEquals(setOf("mesh//g"),groups(client));assertEquals(original,nodes(client));assertEquals("Renamed",name(client,"mesh//g"))
    }
    @Test fun partialReplyPreservesOtherGroups() = withClient {client ->
        ingest(client,"""{"action":"meshes","meshes":[{"_id":"mesh//g","name":"Renamed"}]}""")
        assertEquals(setOf("mesh//g","mesh//h"),groups(client));assertEquals("Renamed",name(client,"mesh//g"))
    }
    @Test fun completeEmptyGroupsDoNotDeleteDevices() = withClient {client ->
        val original=nodes(client);reply(client,requests(client).request(),"[]")
        assertTrue(groups(client).isEmpty());assertEquals(original,nodes(client))
    }
    @Test fun malformedGroupSnapshotLeavesAllGroupsUntouched() = withClient {client ->
        reply(client,requests(client).request(),"""[{"_id":"mesh//g","name":"Changed"},null]""")
        assertEquals(setOf("mesh//g","mesh//h"),groups(client));assertEquals("G",name(client,"mesh//g"))
    }
    @Test fun olderReplyCannotResurrectRemovedGroups() = withClient {client ->
        val old=requests(client).request();val newer=requests(client).request()
        reply(client,newer,"""[{"_id":"mesh//g","name":"G"}]""");reply(client,old,"""[{"_id":"mesh//h","name":"H"}]""")
        assertEquals(setOf("mesh//g"),groups(client))
    }
    @Test fun previousConnectionReplyIsIgnored() = withClient {client ->
        val old=requests(client).request();requests(client).reset();reply(client,old,"[]")
        assertEquals(setOf("mesh//g","mesh//h"),groups(client))
    }
    @Test fun errorAndScopedRepliesCannotClearGroups() = withClient {client ->
        for(key in listOf("error","meshid","partial")) reply(client,requests(client).request().apply {addProperty(key,"x")},"[]")
        assertEquals(setOf("mesh//g","mesh//h"),groups(client))
    }
    @Test fun nodesArrivingBeforeGroupsRemainVisible() = withClient {client ->
        val packet=(field(client,"nodeSnapshots") as NodeSnapshotRequests).request()
        packet.add("nodes",gson.fromJson("""{"mesh//j":[{"_id":"node//a","name":"A","conn":1}]}""",JsonElement::class.java))
        ingest(client,packet.toString());assertEquals(setOf("node//a"),nodes(client).keys)
        reply(client,requests(client).request(),"""[{"_id":"mesh//j","name":"J"}]""")
        assertEquals(setOf("node//a"),nodes(client).keys);assertEquals("J",name(client,"mesh//j"))
    }
    @Test fun meshEventsDoNotOverwriteCacheBeforeCompleteReplies() = withClient {client ->
        for(action in listOf("meshchange","createmesh","deletemesh")) {
            ingest(client,"""{"action":"event","event":{"action":"$action","meshid":"mesh//g"}}""")
            ingest(client,"""{"action":"$action","meshid":"mesh//g"}""")
        }
        assertEquals(setOf("mesh//g","mesh//h"),groups(client));assertEquals("G",name(client,"mesh//g"))
    }
}
