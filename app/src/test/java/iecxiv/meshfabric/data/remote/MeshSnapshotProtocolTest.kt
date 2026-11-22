package iecxiv.meshfabric.data.remote
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
class MeshSnapshotProtocolTest {
    private val gson=Gson()
    private fun decode(text:String)=CompleteMeshSnapshot.decode(gson.fromJson(text,JsonElement::class.java),gson)
    @Test fun requestUsesTagInsteadOfResponseId() {
        val requests=MeshSnapshotRequests();val packet=requests.request()
        assertEquals("meshes",packet.get("action").asString);assertTrue(packet.has("tag"));assertFalse(packet.has("responseid"))
        assertEquals(MeshSnapshotRequests.Mode.COMPLETE,requests.mode(packet))
    }
    @Test fun missingAndForeignTagsRemainMergeOnly() {
        val requests=MeshSnapshotRequests()
        assertEquals(MeshSnapshotRequests.Mode.LEGACY,requests.mode(JsonObject()))
        assertEquals(MeshSnapshotRequests.Mode.LEGACY,requests.mode(requests.request().apply {addProperty("tag","external")}))
    }
    @Test fun errorsScopesAndWrongActionsAreIgnored() {
        val requests=MeshSnapshotRequests();val packet=requests.request()
        for(key in listOf("error","meshid","meshname","partial","more","skip","limit"))
            assertEquals(MeshSnapshotRequests.Mode.IGNORE,requests.mode(packet.deepCopy().apply {addProperty(key,"x")}))
        assertEquals(MeshSnapshotRequests.Mode.IGNORE,requests.mode(packet.deepCopy().apply {addProperty("action","nodes")}))
        assertEquals(MeshSnapshotRequests.Mode.IGNORE,requests.mode(packet.deepCopy().apply {addProperty("result","denied")}))
    }
    @Test fun staleAndPreviousConnectionRepliesAreIgnored() {
        val requests=MeshSnapshotRequests();val old=requests.request();val newer=requests.request();requests.accept(newer)
        assertEquals(MeshSnapshotRequests.Mode.IGNORE,requests.mode(old));requests.reset()
        assertEquals(MeshSnapshotRequests.Mode.IGNORE,requests.mode(newer))
        assertEquals(MeshSnapshotRequests.Mode.COMPLETE,requests.mode(requests.request()))
    }
    @Test fun nodeResponseIdCannotAuthorizeGroupReplacement() {
        val packet=NodeSnapshotRequests().request().apply {addProperty("action","meshes")}
        assertEquals(MeshSnapshotRequests.Mode.LEGACY,MeshSnapshotRequests().mode(packet))
    }
    @Test fun arraysAndMapsPreserveGroupMetadata() {
        assertEquals("G",requireNotNull(decode("""[{"_id":"mesh//g","name":"G"}]""")).values.single().name)
        assertEquals("mesh//g",requireNotNull(decode("""{"mesh//g":{"name":"G"}}""")).values.single().id)
    }
    @Test fun emptySnapshotsAreValidButMissingPayloadIsNot() {
        assertTrue(requireNotNull(decode("[]")).isEmpty());assertTrue(requireNotNull(decode("{}")).isEmpty());assertNull(decode("null"))
    }
    @Test fun malformedRowsRejectTheEntireSnapshot() {
        assertNull(decode("""[{"_id":"mesh//g"},null]"""));assertNull(decode("""[{"name":"Missing ID"}]"""))
    }
    @Test fun duplicateAliasesAreRejected() {assertNull(decode("""[{"_id":"mesh//g"},{"_id":"g"}]"""))}
    @Test fun invalidFieldsAreRejected() {
        assertNull(decode("""[{"_id":"mesh//g","name":null}]"""));assertNull(decode("""[{"_id":"mesh//g","mtype":-1}]"""))
        assertNull(decode("""{"mesh//g":7}"""))
    }
}
