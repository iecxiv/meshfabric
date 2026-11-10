package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl

class AgentInvitationContractTest {
    private val mesh="mesh//abc@${'$'}def"
    private val node="node//device"
    private val rights=524296L
    @Test fun groupAndDeviceUseDifferentUnits() {
        assertEquals(24,AgentInvitationContract.groupLink(mesh,24,0,0,"g").get("expire").asInt)
        assertEquals(60,AgentInvitationContract.temporaryShare(node,"Guest",60,2,rights,null,true,false,"d").get("expire").asInt)
    }
    @Test fun temporaryAccessCannotBeUnlimited() {assertThrows(IllegalArgumentException::class.java) {AgentInvitationContract.temporaryShare(node,"Guest",0,2,rights,null,true,false,"d")}}
    @Test fun serverDurationLimitIsNotSilentlyChanged() {assertThrows(IllegalArgumentException::class.java) {AgentInvitationContract.temporaryShare(node,"Guest",61,2,rights,60,true,false,"d")}}
    @Test fun permissionsRespectRestrictionsAndFullMask() {
        assertFalse(AgentInvitationContract.sharingAllowed(8,2))
        assertFalse(AgentInvitationContract.sharingAllowed(rights or 4096,2))
        assertFalse(AgentInvitationContract.sharingAllowed(rights or 512,1))
        assertFalse(AgentInvitationContract.sharingAllowed(rights or 1024,4))
        assertFalse(AgentInvitationContract.sharingAllowed(rights or 65536,2))
        assertTrue(AgentInvitationContract.sharingAllowed(-1,7))
        assertFalse(AgentInvitationContract.sharingAllowed(-1,2,false))
    }
    @Test fun fileSharingUsesBitFourNotTunnelProtocolFive() {assertEquals(4,AgentInvitationContract.temporaryShare(node,"Guest",5,4,rights,null,true,false,"d").get("p").asInt)}
    @Test fun restrictedDesktopForcesViewOnly() {assertTrue(AgentInvitationContract.temporaryShare(node,"Guest",5,2,rights or 256,null,true,false,"d").get("viewOnly").asBoolean)}
    @Test fun groupPolicySeparatesLanInvitesAndAgentTypes() {
        assertTrue(AgentInvitationContract.groupAllowed(0,4,2,true,false))
        assertFalse(AgentInvitationContract.groupAllowed(0,4,2,true,true))
        assertFalse(AgentInvitationContract.groupAllowed(4096,4,2,false,false))
        assertFalse(AgentInvitationContract.groupAllowed(-1,-1,1,false,false))
    }
    @Test fun aliasFailureNeedsExactRequestIdentity() {
        val p=JsonObject().apply {addProperty("action","deviceShares");addProperty("responseid","r");addProperty("result","Access denied")}
        assertTrue(AgentInvitationContract.isCorrelatedFailure("createDeviceShareLink","r",p))
        assertFalse(AgentInvitationContract.isCorrelatedFailure("createDeviceShareLink","old",p))
        assertFalse(AgentInvitationContract.isCorrelatedFailure("createInviteLink","r",p))
    }
    @Test fun downloadsPreserveDomainPathAndParameterValues() {
        val url=AgentInvitationContract.download("https://example.test/proxy/domain",mesh,"windows",43,2).toHttpUrl()
        assertEquals("/proxy/domain/meshagents",url.encodedPath)
        assertEquals("abc@${'$'}def",url.queryParameter("meshid"))
        assertEquals("43",url.queryParameter("id"))
        assertEquals("2",url.queryParameter("installflags"))
        assertNull(url.queryParameter("auth"))
    }
    @Test fun assistantAndBinaryHaveSeparateSelectors() {
        val a=AgentInvitationContract.download("https://example.test",mesh,"assistant",10006,4).toHttpUrl()
        assertEquals("4",a.queryParameter("ac"));assertNull(a.queryParameter("installflags"))
        val b=AgentInvitationContract.download("https://example.test",mesh,"binary",29,1).toHttpUrl()
        assertEquals("29",b.queryParameter("meshinstall"));assertEquals("abc@${'$'}def",b.queryParameter("id"))
    }
    @Test fun mobilePayloadHasServerCertificateAndGroup() {assertEquals("mc://agent.example/domain,"+"a".repeat(64)+",abc@${'$'}def",AgentInvitationContract.mobileConfiguration("mc://agent.example/domain","a".repeat(64),mesh))}
    @Test fun rejectsInjectedIdentifiers() {assertThrows(IllegalArgumentException::class.java) {AgentInvitationContract.groupLink("mesh//a&auth=secret",24,0,0,"r")}}
    @Test fun emailPacketIsDistinctFromLinkPacket() {
        val p=AgentInvitationContract.groupEmail(mesh,"guest@example.test","Guest","Welcome",4,2,24,"r")
        assertEquals("inviteAgent",p.get("action").asString);assertEquals("4",p.get("os").asString);assertEquals("2",p.get("flags").asString)
    }
    @Test fun revokeTargetsExactPublicIdentifier() {val p=AgentInvitationContract.revoke(node,"public-id","r");assertEquals(node,p.get("nodeid").asString);assertEquals("public-id",p.get("publicid").asString)}
}
