package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceAgentConsolePolicyTest {
    private val node="node//a"
    private val meta=GeneralMeta("Equipo",emptyList(),16L,"Linux","mesh//a")
    private val group=MeshGroup(id=meta.mesh,name="Grupo",mtype=2)
    private val device=MeshNode(id=node,name="Equipo",meshid=meta.mesh,conn=1)
    private val inventory=listOf(group to listOf(device))
    private fun output()=JsonObject().apply {addProperty("action","msg");addProperty("type","console");addProperty("nodeid",node);addProperty("value","Resultado")}
    @Test fun specificConsoleRightIsRequired() {assertTrue(agentConsoleAvailable(node,meta,inventory));for(rights in listOf(4L,8L,131072L)) assertFalse(agentConsoleAvailable(node,meta.copy(rights=rights),inventory))}
    @Test fun agentMustBeOnlineNotOnlyAmt() {for(conn in listOf(0,2)) assertFalse(agentConsoleAvailable(node,meta,listOf(group to listOf(device.copy(conn=conn)))))}
    @Test fun duplicateOrMovedDeviceIsBlocked() {assertFalse(agentConsoleAvailable(node,meta,listOf(group to listOf(device,device))));assertFalse(agentConsoleAvailable(node,meta,listOf(group to listOf(device.copy(meshid="mesh//b")))))}
    @Test fun nonAgentGroupIsBlocked() {assertFalse(agentConsoleAvailable(node,meta,listOf(group.copy(mtype=3) to listOf(device))))}
    @Test fun rejectsBlankMultilineAndOversizedCommands() {for(s in listOf(" ","help\ninfo","help\rinfo","bad\u0000text","x".repeat(4097))) assertFalse(agentConsoleCommandValid(s))}
    @Test fun exactCommandIsSentOnlyToSelectedNode() {val p=agentConsolePacket(node,"help",meta,inventory);assertEquals("msg",p.get("action").asString);assertEquals("console",p.get("type").asString);assertEquals(node,p.get("nodeid").asString);assertEquals("help",p.get("value").asString);assertEquals(4,p.entrySet().size)}
    @Test fun unrelatedOutputIsIgnored() {assertNull(agentConsoleOutput(output(),"node//b"));assertNull(agentConsoleOutput(output().apply {addProperty("type","runcommands")},node))}
    @Test fun missingNodeCannotBeBoundToCurrentDevice() {val p=output();p.remove("nodeid");assertNull(agentConsoleOutput(p,node))}
    @Test fun outputIsPlainTextWithNormalizedNewlines() {val p=output().apply {addProperty("value","<script>literal</script>\r\nTexto")};assertEquals("<script>literal</script>\nTexto",agentConsoleOutput(p,node))}
    @Test fun hugeOutputIsRejected() {assertTrue(runCatching {agentConsoleOutput(output().apply {addProperty("value","x".repeat(131073))},node)}.isFailure)}
    @Test fun bufferIsBoundedAndKeepsNewestOutput() {val lines=agentConsoleBounded((1..1100).map {AgentConsoleLine("output","$it:"+"x".repeat(1000))});assertTrue(lines.size<=1000);assertTrue(lines.sumOf {it.text.length}<=256000);assertTrue(lines.last().text.startsWith("1100:"))}
    @Test fun emptyPacketCannotImplyOutputOrCompletion() {assertNull(agentConsoleOutput(JsonObject(),node))}
}
