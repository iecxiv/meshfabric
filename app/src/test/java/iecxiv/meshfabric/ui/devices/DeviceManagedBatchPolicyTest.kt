package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.*
import org.junit.Assert.*
import org.junit.Test

class DeviceManagedBatchPolicyTest {
    private val node=MeshNode(id="node//one",meshid="mesh//one",conn=1)
    private val meta=GeneralMeta("Device",emptyList(),0xffffffffL,"Linux",node.meshid)
    private fun inventory(conn:Int=1,type:Int=2)=listOf(MeshGroup(id=node.meshid,mtype=type) to listOf(node.copy(conn=conn)))
    private fun draft(action:ManagedBatchAction)=ManagedBatchDraft(action,"user//operator",node.id,meta.mesh,meta.os)
    @Test fun allFourActionsAreExplicitAndTargetExactlyOneId() {
        for(action in ManagedBatchAction.entries) {
            val p=managedBatchPacket(draft(action),"user//operator",node.id,meta,inventory(),"request")
            assertEquals(action.wire,p.get("action").asString)
            assertEquals(listOf(node.id),p.getAsJsonArray("nodeids").map {it.asString})
            assertEquals("request",p.get("responseid").asString)
            assertFalse(p.has("cmds"))
            when(action) {ManagedBatchAction.CLEAR_CORE->assertEquals("clear",p.get("type").asString);ManagedBatchAction.DEFAULT_CORE->assertEquals("default",p.get("type").asString);else->assertFalse(p.has("type"))}
        }
    }
    @Test fun delegatedOrUnknownRightsAreExcludedByThisConservativeIntegration() {
        for(action in ManagedBatchAction.entries) {
            for(rights in listOf(0L,4L,16L,32768L)) assertFalse(managedBatchAvailable(node.id,meta.copy(rights=rights),inventory(),action))
            assertTrue(managedBatchAvailable(node.id,meta.copy(rights=-1L),inventory(),action))
        }
    }
    @Test fun removalDoesNotRequireAnOnlineAgent() {
        assertTrue(managedBatchAvailable(node.id,meta,inventory(0),ManagedBatchAction.REMOVE))
        for(action in ManagedBatchAction.entries.filter {it!=ManagedBatchAction.REMOVE}) for(conn in listOf(0,2,4)) assertFalse(managedBatchAvailable(node.id,meta,inventory(conn),action))
    }
    @Test fun nonAgentGroupsCannotReceiveCoreOrUninstallRequests() {
        for(action in ManagedBatchAction.entries.filter {it!=ManagedBatchAction.REMOVE}) assertFalse(managedBatchAvailable(node.id,meta,inventory(type=1),action))
    }
    @Test fun changedIdentityGroupAndOsCannotReuseReview() {
        val d=draft(ManagedBatchAction.REMOVE)
        assertTrue(runCatching {managedBatchPacket(d,"user//other",node.id,meta,inventory(),"request")}.isFailure)
        assertTrue(runCatching {managedBatchPacket(d,"user/other/operator",node.id,meta,inventory(),"request")}.isFailure)
        assertTrue(runCatching {managedBatchPacket(d,"user//operator",node.id,meta.copy(mesh="mesh//other"),inventory(),"request")}.isFailure)
        assertTrue(runCatching {managedBatchPacket(d,"user//operator",node.id,meta.copy(os="Windows"),inventory(),"request")}.isFailure)
    }
    @Test fun ackMustMatchActionRequestAndNode() {
        val d=draft(ManagedBatchAction.REMOVE)
        val p=JsonParser.parseString("""{"action":"removedevices","responseid":"request","nodeid":"node//one","result":"ok"}""").asJsonObject
        assertEquals("accepted",managedBatchAck(p,d,"request"))
        assertNull(managedBatchAck(p,d,"other"))
        p.addProperty("nodeid","node//other");assertNull(managedBatchAck(p,d,"request"))
    }
    @Test fun removalRequiresBothAcknowledgementAndTheExactRemovalEvent() {
        assertFalse(managedBatchRemovalObserved(true,false));assertFalse(managedBatchRemovalObserved(false,true));assertTrue(managedBatchRemovalObserved(true,true))
        val p=JsonParser.parseString("""{"action":"event","event":{"action":"removenode","nodeid":"node//one"}}""").asJsonObject
        assertTrue(managedBatchRemovalEvent(p,node.id));assertFalse(managedBatchRemovalEvent(p,"node//other"))
        p.getAsJsonObject("event").addProperty("action","changenode");assertFalse(managedBatchRemovalEvent(p,node.id))
    }
    @Test fun duplicateInventoryTargetsAreRejected() {
        assertFalse(managedBatchAvailable(node.id,meta,listOf(MeshGroup(id=node.meshid,mtype=2) to listOf(node,node)),ManagedBatchAction.REMOVE))
    }
}
