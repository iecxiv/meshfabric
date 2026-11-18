package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceAgentConsoleActionsPolicyTest {
    private val node="node//a"
    private val meta=GeneralMeta("Equipo",emptyList(),16L,"Linux","mesh//a")
    private val group=MeshGroup(id=meta.mesh,name="Grupo",mtype=2)
    private val device=MeshNode(id=node,name="Equipo",meshid=meta.mesh,conn=1)
    private val inventory=listOf(group to listOf(device))
    private fun packet(a:AgentConsoleAction,m:GeneralMeta=meta,admin:Int=-1)=agentActionPacket(node,a,m,inventory,admin)
    @Test fun coreActionsTargetOnlyTheSelectedNode() {
        for((a,type) in listOf(AgentConsoleAction.DEFAULT_CORE to "default",AgentConsoleAction.CLEAR_CORE to "clear",AgentConsoleAction.RECOVERY_CORE to "recovery",AgentConsoleAction.TINY_CORE to "tiny")) {
            val p=packet(a);assertEquals("uploadagentcore",p.get("action").asString);assertEquals(type,p.get("type").asString)
            assertEquals(1,p.getAsJsonArray("nodeids").size());assertEquals(node,p.getAsJsonArray("nodeids")[0].asString);assertEquals(3,p.entrySet().size)
        }
    }
    @Test fun softAndHardDisconnectUseDistinctModes() {
        assertEquals(1,packet(AgentConsoleAction.SOFT_DISCONNECT).get("disconnectMode").asInt)
        assertEquals(2,packet(AgentConsoleAction.HARD_DISCONNECT).get("disconnectMode").asInt)
        assertEquals("agentdisconnect",packet(AgentConsoleAction.HARD_DISCONNECT).get("action").asString)
    }
    @Test fun restartUsesTheAgentConsoleCommand() {val p=packet(AgentConsoleAction.RESTART_SERVICE);assertEquals("msg",p.get("action").asString);assertEquals("console",p.get("type").asString);assertEquals("service restart",p.get("value").asString)}
    @Test fun forceUpdateTargetsOneNode() {val p=packet(AgentConsoleAction.FORCE_UPDATE,meta.copy(rights=0xffffffffL));assertEquals("updateAgents",p.get("action").asString);assertEquals(1,p.getAsJsonArray("nodeids").size())}
    @Test fun consoleRightIsAlwaysRequired() {for(a in AgentConsoleAction.values()) assertFalse(agentActionAllowed(a,8L or 131072L,-1))}
    @Test fun disconnectNeedsFullSiteAdministration() {for(a in listOf(AgentConsoleAction.SOFT_DISCONNECT,AgentConsoleAction.HARD_DISCONNECT)) {assertFalse(agentActionAllowed(a,16L,0));assertFalse(agentActionAllowed(a,16L,2));assertTrue(agentActionAllowed(a,16L,-1))}}
    @Test fun forceUpdateNeedsFullDeviceRights() {assertFalse(agentActionAllowed(AgentConsoleAction.FORCE_UPDATE,16L,-1));assertTrue(agentActionAllowed(AgentConsoleAction.FORCE_UPDATE,0xffffffffL,0));assertTrue(agentActionAllowed(AgentConsoleAction.FORCE_UPDATE,-1L,0))}
    @Test fun offlineOrMovedDeviceIsBlocked() {
        assertTrue(runCatching {agentActionPacket(node,AgentConsoleAction.DEFAULT_CORE,meta,listOf(group to listOf(device.copy(conn=0))),-1)}.isFailure)
        assertTrue(runCatching {agentActionPacket(node,AgentConsoleAction.DEFAULT_CORE,meta,listOf(group to listOf(device.copy(meshid="mesh//b"))),-1)}.isFailure)
    }
    @Test fun unauthorizedActionBuildsNoPacket() {assertTrue(runCatching {packet(AgentConsoleAction.HARD_DISCONNECT,admin=0)}.isFailure);assertTrue(runCatching {packet(AgentConsoleAction.FORCE_UPDATE)}.isFailure)}
    @Test fun everyActionHasDistinctWireNameAndText() {
        assertEquals(AgentConsoleAction.values().size,AgentConsoleAction.values().map {it.wire}.toSet().size)
        for(es in listOf(true,false)) {assertEquals(AgentConsoleAction.values().size,AgentConsoleAction.values().map {agentActionLabel(it,es)}.toSet().size);assertTrue(AgentConsoleAction.values().all {agentActionDetail(it,es).isNotBlank()})}
    }
}
