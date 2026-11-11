package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class AgentInvitationContractTest {
    private val mesh="mesh//group"
    private fun j(s:String):JsonObject=JsonParser.parseString(s).asJsonObject
    private fun parse(a:String,t:String,r:String,p:JsonObject)=AgentInvitationReplies.parse(a,t,r,p)
    private val link=j("""{"action":"createInviteLink","meshid":"mesh//group","responseid":"r","url":"https://example.test/agentinvite?c=secret","expire":24}""")
    private val ok=j("""{"action":"inviteAgent","responseid":"r","result":"ok"}""")
    @Test fun bothOriginsResolveTheSameGroup() {assertEquals(mesh,AgentInvitationContract.installationTarget("user//actor",mesh))}
    @Test fun deviceIdentifierIsNotAGroup() {assertThrows(IllegalArgumentException::class.java) {AgentInvitationContract.installationTarget("user//actor","node//device")}}
    @Test fun missingGroupIsNotGuessed() {assertThrows(IllegalArgumentException::class.java) {AgentInvitationContract.installationTarget("user//actor","")}}
    @Test fun crossDomainInvitationIsRejected() {assertThrows(IllegalArgumentException::class.java) {AgentInvitationContract.installationTarget("user/other/actor",mesh)}}
    @Test fun agentGroupTypeIsRequired() {assertFalse(AgentInvitationContract.groupAllowed(-1,-1,1,false,true))}
    @Test fun accountRestrictionBlocksEnrollment() {assertFalse(AgentInvitationContract.groupAllowed(4096,4,2,false,false))}
    @Test fun lanOnlySeparatesInstallFromInvitation() {assertTrue(AgentInvitationContract.groupAllowed(0,4,2,true,false));assertFalse(AgentInvitationContract.groupAllowed(0,4,2,true,true))}
    @Test fun installationInvitationUsesHours() {val p=AgentInvitationContract.groupLink(mesh,24,2,16,"r");assertEquals(24,p.get("expire").asInt);assertEquals(mesh,p.get("meshid").asString);assertFalse(p.has("nodeid"))}
    @Test fun emailHasDistinctPlatformAndModeFields() {val p=AgentInvitationContract.groupEmail(mesh,"guest@example.test","Guest","Welcome",4,2,24,"r");assertEquals("inviteAgent",p.get("action").asString);assertEquals("4",p.get("os").asString);assertEquals("2",p.get("flags").asString)}
    @Test fun downloadPreservesDomainPrefix() {val u=AgentInvitationContract.download("https://example.test/proxy/domain",mesh,"windows",43,2).toHttpUrl();assertEquals("/proxy/domain/meshagents",u.encodedPath);assertEquals("43",u.queryParameter("id"));assertEquals("2",u.queryParameter("installflags"));assertNull(u.queryParameter("auth"))}
    @Test fun assistantDoesNotUseInstallFlags() {val u=AgentInvitationContract.download("https://example.test",mesh,"assistant",10006,4).toHttpUrl();assertEquals("4",u.queryParameter("ac"));assertNull(u.queryParameter("installflags"))}
    @Test fun binaryInstallerUsesArchitectureSelector() {val u=AgentInvitationContract.download("https://example.test",mesh,"binary",29,1).toHttpUrl();assertEquals("29",u.queryParameter("meshinstall"));assertEquals("group",u.queryParameter("id"))}
    @Test fun mobilePayloadIncludesCertificateAndGroup() {assertEquals("mc://agent.example/domain,"+"a".repeat(64)+",group",AgentInvitationContract.mobileConfiguration("mc://agent.example/domain","a".repeat(64),mesh))}
    @Test fun sharingIsNotAnInstallationFailureAlias() {val p=j("""{"action":"deviceShares","responseid":"r","result":"Access denied"}""");assertFalse(AgentInvitationContract.isCorrelatedFailure("createInviteLink","r",p));assertFalse(AgentInvitationContract.isCorrelatedFailure("createDeviceShareLink","r",p))}
}
