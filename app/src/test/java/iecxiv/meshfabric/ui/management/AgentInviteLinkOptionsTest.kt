package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class AgentInviteLinkOptionsTest {
    @Test fun expirationValues() {assertEquals(listOf(1,8,24,168,720,0),AgentInviteLinkOptions.expirations)}
    @Test fun agentValues() {assertEquals(listOf(0,1,2,4,8,16),AgentInviteLinkOptions.agentKinds)}
    @Test fun modeValues() {assertEquals(listOf(0,2,1),AgentInviteLinkOptions.installationModes)}
    @Test fun assistantClearsMode() {val o=AgentInviteLinkOptions(flags=2).selectAgent(8);assertFalse(o.showsType);assertEquals(0,o.flags)}
    @Test fun androidClearsMode() {val o=AgentInviteLinkOptions(flags=1).selectAgent(16);assertFalse(o.showsType);assertEquals(0,o.flags)}
    @Test fun windowsDoesNotRestoreHiddenMode() {assertEquals(0,AgentInviteLinkOptions(flags=2).selectAgent(8).selectAgent(1).flags)}
    @Test fun packetIsInstallationLinkOnly() {val p=AgentInviteLinkOptions(168,4,1).packet("mesh//group");assertEquals("createInviteLink",p.get("action").asString);assertEquals(168,p.get("expire").asInt);assertEquals(4,p.get("agents").asInt);assertEquals(1,p.get("flags").asInt);assertFalse(p.has("nodeid"));assertFalse(p.has("email"))}
    @Test fun hiddenModeCannotLeak() {for(a in listOf(8,16)) assertEquals(0,AgentInviteLinkOptions(agents=a,flags=2).packet("mesh//group").get("flags").asInt)}
}
