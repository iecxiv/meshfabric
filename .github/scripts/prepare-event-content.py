FILES={
'app/src/main/java/iecxiv/meshfabric/ui/management/ServerEventIdentity.kt':r"""package iecxiv.meshfabric.ui.management
import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
internal data class EventDevice(val nodeId:String="",val name:String="",val meshId:String="",val group:String="")
private fun JsonObject.label(key:String)=get(key)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString.orEmpty()
private fun eventKey(id:String,prefix:String):String = when {
    id.isBlank()->""
    id.startsWith("$prefix/")->id
    !id.contains('/')->"$prefix//$id"
    else->""
}
internal fun serverEventDevice(item:JsonObject):EventDevice {
    val action=item.label("action")
    val raw=item.label("nodeid")
    val nested=item.get("node")?.takeIf {it.isJsonObject}?.asJsonObject?.takeIf {raw.isBlank() || (eventKey(raw,"node").isNotBlank() && eventKey(it.label("_id"),"node")==eventKey(raw,"node"))}
    val args=item.get("msgArgs")?.takeIf {it.isJsonArray}?.asJsonArray
    fun arg(index:Int)=args?.takeIf {it.size()>index}?.get(index)?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString.orEmpty()
    val name=item.label("nodename").ifBlank {nested?.label("name").orEmpty()}.ifBlank {if(action=="removenode") arg(0) else ""}
    val mesh=item.label("meshid").ifBlank {if(action=="nodemeshchange") item.label("newMeshId") else ""}.ifBlank {nested?.label("meshid").orEmpty()}
    val group=item.label("meshname").ifBlank {if(action in setOf("removenode","nodemeshchange")) arg(1) else ""}
    return EventDevice(raw.ifBlank {nested?.label("_id").orEmpty()},name,mesh,group)
}
internal data class ServerEventInventory(val nodes:Map<String,EventDevice> = emptyMap(),val groups:Map<String,String> = emptyMap())
internal fun serverEventInventory(groups:List<Pair<MeshGroup,List<MeshNode>>>):ServerEventInventory {
    val names=groups.mapNotNull {(group,_)->eventKey(group.id,"mesh").takeIf {it.isNotBlank()}?.let {it to group.name}}.toMap()
    val nodes=groups.flatMap {(group,items)->items.mapNotNull {node->eventKey(node.id,"node").takeIf {it.isNotBlank()}?.let {it to EventDevice(node.id,node.name,node.meshid.ifBlank {group.id})}}}.toMap()
    return ServerEventInventory(nodes,names)
}
internal fun ServerEventRecord.withInventory(inventory:ServerEventInventory):ServerEventRecord {
    val node=inventory.nodes[eventKey(nodeId,"node")]
    val name=nodeName.ifBlank {node?.name.orEmpty()}
    val mesh=meshId.ifBlank {node?.meshId.orEmpty()}
    val group=groupName.ifBlank {inventory.groups[eventKey(mesh,"mesh")].orEmpty()}
    return copy(nodeName=name,meshId=mesh,groupName=group,inventoryResolved=inventoryResolved || name!=nodeName || mesh!=meshId || group!=groupName)
}
internal fun ServerEventRecord.deviceLabel():String=if(nodeName.isBlank()) "" else nodeName+if(groupName.isBlank()) "" else "($groupName)"
""",
'app/src/test/java/iecxiv/meshfabric/ui/management/EventContentTest.kt':r"""package iecxiv.meshfabric.ui.management
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
""",
'app/src/androidTest/java/iecxiv/meshfabric/ui/management/EventContentUiTest.kt':r"""package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
class EventContentUiTest {
    @get:Rule val compose=createComposeRule()
    private fun event()=ServerEventRecord("runcommands","Running commands","operator","node//device","surface",1L,details=mapOf("cmds" to "echo ready\nwhoami","cmdType" to "2","runAsUser" to "1"),meshId="mesh//agent",groupName="agent")
    private fun state(row:ServerEventRecord)=ServerEventsSnapshot(ServerEventsPhase.READY,listOf(row))
    @Test fun cardAndDetailsShareDeviceGroupAndBothIdentifiers() {
        compose.setContent {MaterialTheme {ServerEventsPanel(state(event()),"",{},true,{})}}
        compose.onNodeWithTag("log-event-device-0",useUnmergedTree=true).assertTextEquals("surface(agent)")
        compose.onNodeWithTag("log-event-node-0",useUnmergedTree=true).assertExists();compose.onNodeWithTag("log-event-mesh-0",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("log-event-row-0").performClick()
        compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface(agent)")
        compose.onNodeWithTag("log-event-command").assertTextEquals("echo ready\nwhoami")
        compose.onNodeWithTag("log-event-details-message").assertTextEquals("Running commands")
    }
    @Test fun longCommandIsNotTruncatedInReadOnlyDetails() {
        val command="echo "+"x".repeat(5000)+"\nEND"
        compose.setContent {MaterialTheme {ServerEventsPanel(state(event().copy(details=mapOf("cmds" to command))),"",{},true,{})}}
        compose.onNodeWithTag("log-event-row-0").performClick();compose.onNodeWithTag("log-event-command").assertTextEquals(command)
    }
    @Test fun groupAndCommandAreSearchable() {
        compose.setContent {var query by remember {mutableStateOf("")};MaterialTheme {ServerEventsPanel(state(event()),query,{query=it},true,{})}}
        compose.onNodeWithTag("log-events-search").performTextInput("whoami");compose.onNodeWithTag("log-event-row-0").assertExists()
        compose.onNodeWithTag("log-events-search").performTextClearance();compose.onNodeWithTag("log-events-search").performTextInput("agent");compose.onNodeWithTag("log-event-row-0").assertExists()
    }
    @Test fun inventoryChangeRemovesOpenResolvedDetails() {
        val inventory=mutableStateOf(ServerEventInventory(mapOf("node//device" to EventDevice("node//device","surface","mesh//agent")),mapOf("mesh//agent" to "agent")))
        compose.setContent {MaterialTheme {ServerEventsPanel(state(event().copy(nodeName="",groupName="",meshId="")),"",{},true,{},inventory=inventory.value)}}
        compose.onNodeWithTag("log-event-row-0").performClick();compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface(agent)")
        compose.runOnIdle {inventory.value=ServerEventInventory()};compose.onNodeWithTag("log-event-details").assertDoesNotExist()
    }
}
"""
}
from pathlib import Path
import json,subprocess,xml.etree.ElementTree as ET
changed=set()
def edit(path,old,new):
    p=Path(path);s=p.read_text();assert s.count(old)==1,(str(p),old);p.write_text(s.replace(old,new,1));changed.add(str(p))
root=Path('app/src/main/java/iecxiv/meshfabric/ui/management');policy=root/'ServerEventsReadPolicy.kt';details=root/'ServerEventDetails.kt';screen=root/'LogScreen.kt'
for p,sha in [(policy,'eaa2da318c6d6e437e07556f0c566301fece22b9'),(details,'8b44fee1a1539f5f6e414fb94e278c09c704f14e'),(screen,'43708d82867f78add1096e511af458cb77ba37ed')]:
    assert subprocess.check_output(['git','hash-object',str(p)],text=True).strip()==sha
for path,content in FILES.items():
    p=Path(path);assert not p.exists(),path;p.write_text(content);changed.add(path)
edit(policy,'val details:Map<String,String> = emptyMap())','val details:Map<String,String> = emptyMap(),val meshId:String="",val groupName:String="",val inventoryResolved:Boolean=false)')
edit(policy,'val item=element.asJsonObject','val item=element.asJsonObject\n                val device=serverEventDevice(item)')
edit(policy,'item.eventText("nodeid"),item.eventText("nodename"),eventTime(item),serverEventDetails(item))','item.eventText("nodeid").ifBlank {device.nodeId},item.eventText("nodename").ifBlank {device.name},eventTime(item),serverEventDetails(item),device.meshId,device.group)')
old="""internal fun serverEventDetails(item:JsonObject):Map<String,String> = listOf("msgid","protocol","duration","ip","raddr","bytesin","bytesout").mapNotNull {key->
    item.get(key)?.takeIf {it.isJsonPrimitive}?.asString?.let {key to it}
}.toMap()"""
new="""internal fun serverEventDetails(item:JsonObject):Map<String,String> {
    val action=item.get("action")?.takeIf {it.isJsonPrimitive && it.asJsonPrimitive.isString}?.asString
    val keys=listOf("msgid","protocol","duration","ip","raddr","bytesin","bytesout")+
        (if(action=="runcommands") listOf("cmds","cmdType","runAsUser") else emptyList())+
        (if(action=="nodemeshchange") listOf("oldMeshId","newMeshId") else emptyList())
    return keys.mapNotNull {key->item.get(key)?.takeIf {it.isJsonPrimitive && when(key) {
        "cmds","oldMeshId","newMeshId"->it.asJsonPrimitive.isString
        "cmdType","runAsUser"->it.asJsonPrimitive.isNumber
        else->true
    }}?.asString?.let {key to it}}.toMap()
}"""
edit(details,old,new)
edit(details,'ManagementInfoRow(stringResource(R.string.log_detail_device),row.nodeName.ifBlank {missing})','Text(stringResource(R.string.log_detail_device),style=MaterialTheme.typography.labelMedium)\n            Text(row.deviceLabel().ifBlank {missing},Modifier.testTag("log-event-details-device"),style=MaterialTheme.typography.bodyMedium)\n            if(row.groupName.isNotBlank()) ManagementInfoRow(stringResource(R.string.log_detail_group),row.groupName)\n            if(row.meshId.isNotBlank()) ManagementInfoRow(stringResource(R.string.log_detail_mesh),row.meshId)\n            if(row.inventoryResolved) Text(stringResource(R.string.log_detail_inventory),style=MaterialTheme.typography.bodySmall)')
edit(details,'row.details.forEach {(key,value)->ManagementInfoRow(key,value)}','if(row.action=="runcommands") {\n                Text(stringResource(R.string.log_detail_command),style=MaterialTheme.typography.labelMedium)\n                Text(row.details["cmds"] ?: missing,Modifier.testTag("log-event-command"),style=MaterialTheme.typography.bodyMedium)\n                row.details["cmdType"]?.let {ManagementInfoRow(stringResource(R.string.log_detail_cmd_type),it)}\n                row.details["runAsUser"]?.let {ManagementInfoRow(stringResource(R.string.log_detail_run_as),it)}\n            }\n            row.details.filterKeys {it !in setOf("cmds","cmdType","runAsUser")}.forEach {(key,value)->ManagementInfoRow(key,value)}')
edit(screen,'    val connected by source.connectionState.collectAsState()','    val connected by source.connectionState.collectAsState()\n    val groups by source.groupedDevices.collectAsState()\n    val inventory=remember(groups,connected,actor?.id,source.serverUrl,source.authCookie) {if(connected && !actor?.id.isNullOrBlank() && !source.isUserLogout) serverEventInventory(groups) else ServerEventInventory()}')
edit(screen,'selectedLimit=it.limit ?: 0})','selectedLimit=it.limit ?: 0},inventory=inventory)')
edit(screen,'onSelection:((ServerEventsQuery)->Unit)?=null) {','onSelection:((ServerEventsQuery)->Unit)?=null,inventory:ServerEventInventory=ServerEventInventory()) {')
edit(screen,'remember(state.records,state.phase,selection)','remember(state.records,state.phase,selection,inventory)')
edit(screen,'state.records.filter {row->listOf(row.message,row.actor,row.nodeName,row.action)','state.records.map {it.withInventory(inventory)}.filter {row->listOf(row.message,row.actor,row.nodeName,row.action,row.groupName,row.nodeId,row.meshId,row.details["cmds"].orEmpty())')
edit(screen,'if(row.nodeName.isNotBlank()) Text(row.nodeName,style=MaterialTheme.typography.labelMedium)','if(row.deviceLabel().isNotBlank()) Text(row.deviceLabel(),Modifier.testTag("log-event-device-$index"),style=MaterialTheme.typography.labelMedium)\n                    if(row.nodeId.isNotBlank()) Text(stringResource(R.string.log_detail_node)+": "+row.nodeId,Modifier.testTag("log-event-node-$index"),style=MaterialTheme.typography.labelSmall)\n                    if(row.meshId.isNotBlank()) Text(stringResource(R.string.log_detail_mesh)+": "+row.meshId,Modifier.testTag("log-event-mesh-$index"),style=MaterialTheme.typography.labelSmall)\n                    if(row.inventoryResolved) Text(stringResource(R.string.log_detail_inventory),style=MaterialTheme.typography.bodySmall)')
labels={
'log_detail_mesh':('Mesh ID','Mesh ID'),
'log_detail_group':('Device group','Grupo de dispositivos'),
'log_detail_command':('Command sent','Comando enviado'),
'log_detail_cmd_type':('Command type (server value)','Tipo de comando (valor del servidor)'),
'log_detail_run_as':('Run-as mode (server value)','Modo de ejecución (valor del servidor)'),
'log_detail_inventory':('Some device or group data comes from the current connection inventory and may differ from the event time.','Parte de los datos de dispositivo o grupo procede del inventario de la conexión actual y puede diferir del momento del evento.')}
for directory,index in [('values',0),('values-en',0),('values-es',1)]:
    p=Path('app/src/main/res')/directory/'log.xml';tree=ET.parse(p);r=tree.getroot()
    for key,pair in labels.items():
        assert r.find("string[@name='"+key+"']") is None;ET.SubElement(r,'string',{'name':key}).text=pair[index]
    ET.indent(tree,space='    ');tree.write(p,encoding='utf-8',xml_declaration=True);p.write_text(p.read_text()+'\n');changed.add(str(p))
p=Path('docs/log.md');text=p.read_text();old='Only explicit additional scalar fields msgid/protocol/duration/ip/raddr/bytesin/bytesout are retained, not arbitrary raw payload or credential fields.'
assert text.count(old)==1
text=text.replace(old,'Explicit scalar detail fields are retained; runcommands additionally keeps literal cmds/cmdType/runAsUser, and nodemeshchange keeps oldMeshId/newMeshId. Arbitrary raw payload, cookies, tokens and password fields are not retained.')
text+='\nEvent content review against meshcentral@1.3.1 meshuser.js: runcommands has generic msg plus cmds/cmdType/runAsUser; manual uses msg; changenode/nodemeshchange may expose a nested node; removenode carries historical device/group names in msgArgs[0:2]. Only those specific structured name arguments are interpreted, not arbitrary message templates. No unverified per-action payload is invented. Relay generic scalar details remain supported; this is not a complete audit of all server producers.\nCards and read-only dialogs share device(group), with available node and mesh IDs separately. Event names win. Missing names are filled from the same active client inventory (not the device-list search result); fallback is labelled as connection inventory, not historical evidence. Mesh-specific group lookup prevents current group names from being attached to a different historical mesh ID. Domains are preserved in ID matching. Inventory changes retire open details; account/server scope remains keyed. Command contents remain selectable in details and searchable locally, never sent for execution or added to diagnostics. Commands can contain sensitive user data; there is no automatic export.\nReference: https://unpkg.com/meshcentral@1.3.1/meshuser.js . Live-server and physical-device checks remain pending.\n'
p.write_text(text);changed.add(str(p))
assert len(changed)==10,changed
subprocess.run(['git','add','--',*sorted(changed)],check=True)
assert set(subprocess.check_output(['git','diff','--cached','--name-only'],text=True).splitlines())==changed
Path('validation/event-content-expected.json').write_text(json.dumps(sorted(changed),indent=2)+'\n')
