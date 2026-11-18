package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralActionsPolicyTest {
    private val node="node//a"
    private val meta=GeneralMeta("Equipo",emptyList(),262216L,"Linux","mesh//a")
    private val device=MeshNode(id=node,name="Equipo",meshid=meta.mesh,conn=1)
    private val group=MeshGroup(id=meta.mesh,name="Grupo",mtype=2)
    private val inventory=listOf(group to listOf(device))
    private fun draft(action:GeneralDeviceAction)=GeneralDeviceActionDraft(action,meta.mesh,meta.os)
    @Test fun wakePacketTargetsExactlyOneDevice() {val p=generalDeviceActionPacket(node,"r",draft(GeneralDeviceAction.WAKE),meta,inventory);assertEquals("wakedevices",p.get("action").asString);assertEquals(1,p.getAsJsonArray("nodeids").size());assertEquals(node,p.getAsJsonArray("nodeids")[0].asString);assertFalse(p.has("actiontype"))}
    @Test fun powerTypesAreExplicit() {for((action,type) in listOf(GeneralDeviceAction.RESTART to 3,GeneralDeviceAction.SHUTDOWN to 2,GeneralDeviceAction.SLEEP to 4)) assertEquals(type,generalDeviceActionPacket(node,"r",draft(action),meta,inventory).get("actiontype").asInt)}
    @Test fun wakeDoesNotRequireOnlineAgent() {assertTrue(generalDeviceActionAvailable(node,meta,listOf(group to listOf(device.copy(conn=0))),GeneralDeviceAction.WAKE))}
    @Test fun powerRequiresOnlineAgentNotAmtConnectivity() {for(conn in listOf(0,2)) assertFalse(generalDeviceActionAvailable(node,meta,listOf(group to listOf(device.copy(conn=conn))),GeneralDeviceAction.RESTART))}
    @Test fun managementCannotReplacePowerRights() {for(rights in listOf(4L,8L,262144L)) assertFalse(generalDeviceActionAvailable(node,meta.copy(rights=rights),inventory,GeneralDeviceAction.SHUTDOWN))}
    @Test fun wakeRequiresSpecificRight() {assertFalse(generalDeviceActionAvailable(node,meta.copy(rights=4L),inventory,GeneralDeviceAction.WAKE))}
    @Test fun duplicatesAreRejected() {assertFalse(generalDeviceActionAvailable(node,meta,listOf(group to listOf(device,device)),GeneralDeviceAction.RESTART))}
    @Test fun movedDeviceAndNonAgentGroupAreRejected() {assertFalse(generalDeviceActionAvailable(node,meta,listOf(group to listOf(device.copy(meshid="mesh//b"))),GeneralDeviceAction.RESTART));assertFalse(generalDeviceActionAvailable(node,meta,listOf(group.copy(mtype=3) to listOf(device)),GeneralDeviceAction.WAKE))}
    @Test fun changedReviewBlocksSend() {val d=draft(GeneralDeviceAction.RESTART);assertTrue(runCatching {generalDeviceActionPacket(node,"r",d.copy(os="Windows"),meta,inventory)}.isFailure);assertTrue(runCatching {generalDeviceActionPacket(node,"r",d.copy(mesh="mesh//b"),meta,inventory)}.isFailure)}
    @Test fun acknowledgementRequiresCorrelation() {val p=JsonObject().apply {addProperty("action","poweraction");addProperty("responseid","r");addProperty("result","ok")};assertEquals("accepted",generalDeviceActionAck(p,node,"r",GeneralDeviceAction.RESTART));assertNull(generalDeviceActionAck(p,node,"other",GeneralDeviceAction.RESTART))}
    @Test fun contradictoryTargetCannotBeAccepted() {val p=JsonObject().apply {addProperty("action","poweraction");addProperty("responseid","r");addProperty("nodeid","node//b");addProperty("result","ok")};assertNull(generalDeviceActionAck(p,node,"r",GeneralDeviceAction.RESTART))}
    @Test fun missingReplyCannotImplySuccess() {assertNull(generalDeviceActionAck(JsonObject(),node,"r",GeneralDeviceAction.WAKE))}
}
