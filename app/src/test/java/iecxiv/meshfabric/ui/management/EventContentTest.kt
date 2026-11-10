package iecxiv.meshfabric.ui.management
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test
class EventContentTest {
    private fun row(name:String="",mesh:String="",group:String="",id:String="node//device")=ServerEventRecord("runcommands","Running commands","operator",id,name,1L,meshId=mesh,groupName=group)
    private fun inventory()=serverEventInventory(listOf(MeshGroup("mesh//agent","agent") to listOf(MeshNode("node//device","surface","mesh//agent"))))
    @Test fun commandPayloadIsRetainedLiterallyWithoutDescriptionSubstitution() {
        val command="echo ready\n"+"x".repeat(65520)
        val packet=com.google.gson.JsonObject().apply {addProperty("action","events");addProperty("tag","test");add("events",com.google.gson.JsonArray().apply {add(com.google.gson.JsonObject().apply {addProperty("action","runcommands");addProperty("nodeid","node//device");addProperty("nodename","surface");addProperty("meshid","mesh//agent");addProperty("meshname","agent");addProperty("msg","Running commands");addProperty("cmds",command);addProperty("cmdType",2);addProperty("runAsUser",1)})})}
        val gate=ServerEventsReadGate();val scope=ServerEventsScope("server","user",1,"rights");val attempt=gate.begin(scope,"test")
        assertTrue(gate.receive(attempt,scope,packet));assertEquals(ServerEventsPhase.READY,gate.state.phase)
        val event=gate.state.records.single();assertEquals("surface(agent)",event.deviceLabel());assertEquals("mesh//agent",event.meshId);assertEquals(command,event.details["cmds"]);assertEquals("Running commands",event.message);assertEquals("2",event.details["cmdType"]);assertEquals("1",event.details["runAsUser"])
    }
    @Test fun unknownCredentialFieldsAreNotRetained() {
        val details=serverEventDetails(JsonParser.parseString("{\"action\":\"manual\",\"cmds\":\"hidden\",\"password\":\"hidden\",\"token\":\"hidden\",\"node\":{\"auth\":\"hidden\"}}").asJsonObject)
        assertTrue(details.isEmpty())
    }
    @Test fun removalUsesItsHistoricalStructuredNames() {
        val device=serverEventDevice(JsonParser.parseString("{\"action\":\"removenode\",\"nodeid\":\"node//device\",\"msgArgs\":[\"old surface\",\"old agent\"]}").asJsonObject)
        assertEquals("old surface",device.name);assertEquals("old agent",device.group)
    }
    @Test fun nestedNodeIdentityIsExtractedWithoutArbitraryNodePayload() {
        val device=serverEventDevice(JsonParser.parseString("{\"action\":\"changenode\",\"node\":{\"_id\":\"node//device\",\"name\":\"surface\",\"meshid\":\"mesh//agent\",\"password\":\"hidden\"}}").asJsonObject)
        assertEquals(EventDevice("node//device","surface","mesh//agent",""),device)
    }
    @Test fun conflictingNestedDeviceDoesNotOverrideTheRootIdentity() {
        val device=serverEventDevice(JsonParser.parseString("{\"nodeid\":\"node//device\",\"node\":{\"_id\":\"node/other/device\",\"name\":\"wrong\"}}").asJsonObject)
        assertEquals("node//device",device.nodeId);assertEquals("",device.name)
    }
    @Test fun inventoryCompletesBothNamesAndMarksTheirOrigin() {
        val event=row().withInventory(inventory());assertEquals("surface(agent)",event.deviceLabel());assertEquals("mesh//agent",event.meshId);assertTrue(event.inventoryResolved)
    }
    @Test fun historicalNamesWinOverTheCurrentInventory() {
        val event=row("old surface","mesh//old","old agent").withInventory(inventory());assertEquals("old surface(old agent)",event.deviceLabel());assertFalse(event.inventoryResolved)
    }
    @Test fun currentGroupIsNotAssignedToAHistoricalDifferentMesh() {
        val event=row("old surface","mesh//old").withInventory(inventory());assertEquals("",event.groupName);assertEquals("old surface",event.deviceLabel())
    }
    @Test fun domainIdentifiersAreNotCollapsedIntoDefaultDomain() {
        val event=row(id="node/other/device").withInventory(inventory());assertEquals("",event.nodeName);assertEquals("",event.meshId)
    }
    @Test fun missingInventoryDoesNotInventNamesOrTreatIdsAsNames() {
        val event=row().withInventory(ServerEventInventory());assertEquals("",event.deviceLabel());assertFalse(event.inventoryResolved)
    }
}
