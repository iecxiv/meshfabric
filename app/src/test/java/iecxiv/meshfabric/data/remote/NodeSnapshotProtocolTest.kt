package iecxiv.meshfabric.data.remote
import com.google.gson.Gson
import com.google.gson.JsonElement
import org.junit.Assert.*
import org.junit.Test
class NodeSnapshotProtocolTest {
    private val gson=Gson()
    private fun decode(text:String)=CompleteNodeSnapshot.decode(gson.fromJson(text,JsonElement::class.java),gson)
    @Test fun onlyIssuedRequestsCanAuthorizeReplacement() {
        val requests=NodeSnapshotRequests();val packet=requests.request()
        assertEquals(NodeSnapshotRequests.Mode.COMPLETE,requests.mode(packet))
        val foreign=packet.deepCopy().apply {addProperty("responseid","external")}
        assertEquals(NodeSnapshotRequests.Mode.LEGACY,requests.mode(foreign))
    }
    @Test fun uncorrelatedResponsesRemainNonDestructive() {
        assertEquals(NodeSnapshotRequests.Mode.LEGACY,NodeSnapshotRequests().mode(com.google.gson.JsonObject()))
    }
    @Test fun scopedAndErrorResponsesCannotAuthorizeReplacement() {
        val requests=NodeSnapshotRequests();val packet=requests.request()
        for(key in listOf("meshid","meshname","nodeid","partial","more","skip","limit")) {
            assertEquals(NodeSnapshotRequests.Mode.IGNORE,requests.mode(packet.deepCopy().apply {addProperty(key,"x")}))
        }
        assertEquals(NodeSnapshotRequests.Mode.IGNORE,requests.mode(packet.deepCopy().apply {addProperty("result","error")}))
    }
    @Test fun acceptedNewerReplyInvalidatesOlderReplies() {
        val requests=NodeSnapshotRequests();val first=requests.request();val second=requests.request()
        requests.accept(second)
        assertEquals(NodeSnapshotRequests.Mode.IGNORE,requests.mode(first))
        assertEquals(NodeSnapshotRequests.Mode.IGNORE,requests.mode(second))
    }
    @Test fun resetInvalidatesPreviousConnectionRequests() {
        val requests=NodeSnapshotRequests();val old=requests.request();requests.reset()
        assertEquals(NodeSnapshotRequests.Mode.IGNORE,requests.mode(old))
        assertEquals(NodeSnapshotRequests.Mode.COMPLETE,requests.mode(requests.request()))
    }
    @Test fun emptyObjectsAndArraysAreValidCompleteSnapshots() {
        assertTrue(requireNotNull(decode("{}")).isEmpty());assertTrue(requireNotNull(decode("[]")).isEmpty())
        assertNull(decode("null"));assertNull(decode("1"))
    }
    @Test fun groupedSnapshotsInheritMeshId() {
        val node=requireNotNull(decode("""{"mesh//g":[{"_id":"node//a","name":"A"}]}""")).values.single()
        assertEquals("node//a",node.id);assertEquals("mesh//g",node.meshid)
    }
    @Test fun flatMapsInheritNodeId() {
        assertEquals("node//a",requireNotNull(decode("""{"node//a":{"name":"A"}}""")).values.single().id)
    }
    @Test fun arraysRetainExplicitOfflineMask() {
        assertEquals(0,requireNotNull(decode("""[{"_id":"node//a","conn":0}]""")).values.single().conn)
    }
    @Test fun malformedRowsRejectTheWholeSnapshot() {
        assertNull(decode("""[{"_id":"node//a"},7]"""))
        assertNull(decode("""[{"name":"Missing ID"}]"""))
        assertNull(decode("""{"mesh//g":[null]}"""))
    }
    @Test fun duplicateNodeAliasesAreRejected() {
        assertNull(decode("""[{"_id":"node//a"},{"_id":"a"}]"""))
    }
    @Test fun invalidCriticalFieldsCannotAuthorizeDeletion() {
        assertNull(decode("""[{"_id":"node//a","conn":-1}]"""))
        assertNull(decode("""[{"_id":"node//a","name":null}]"""))
        assertNull(decode("""[{"_id":8}]"""))
    }

    @Test fun errorFieldAlwaysRejectsInventoryReplies() {
        val requests=NodeSnapshotRequests()
        val packets=listOf(requests.request(),com.google.gson.JsonObject().apply {addProperty("action","nodes")},com.google.gson.JsonObject().apply {addProperty("action","nodes");addProperty("responseid","external")})
        for(value in listOf("\"denied\"","null","false","0","{}","[]")) for(packet in packets) {
            packet.add("error",gson.fromJson(value,JsonElement::class.java))
            assertEquals(NodeSnapshotRequests.Mode.IGNORE,requests.mode(packet))
        }
    }
    @Test fun errorFieldOverridesSuccessfulResult() {
        val requests=NodeSnapshotRequests();val packet=requests.request().apply {
            addProperty("result","OK");addProperty("error","denied")
        }
        assertEquals(NodeSnapshotRequests.Mode.IGNORE,requests.mode(packet))
    }
    @Test fun errorRepliesDoNotAdvanceAcceptedSequence() {
        val requests=NodeSnapshotRequests();val earlier=requests.request()
        val failed=requests.request().apply {addProperty("error","denied")}
        requests.accept(failed)
        assertEquals(NodeSnapshotRequests.Mode.COMPLETE,requests.mode(earlier))
    }
}
