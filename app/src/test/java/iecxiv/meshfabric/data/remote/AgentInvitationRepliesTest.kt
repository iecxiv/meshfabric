package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class AgentInvitationRepliesTest {
    private fun j(text:String):JsonObject=JsonParser.parseString(text).asJsonObject
    private fun parse(action:String,target:String,id:String,reply:JsonObject)=AgentInvitationReplies.parse(action,target,id,reply)
    private val mesh="mesh//abc"
    private val node="node//n"
    private val link=j("""{"action":"createInviteLink","meshid":"mesh//abc","url":"https://example.test/proxy/agentinvite?c=secret","expire":24,"cookie":"secret","responseid":"r"}""")
    private val ok=j("""{"action":"inviteAgent","responseid":"r","result":"ok"}""")
    private val share=j("""{"action":"createDeviceShareLink","nodeid":"node//n","guestname":"Guest","p":2,"expire":1760000000000,"consent":65,"viewOnly":false,"responseid":"r","result":"OK","url":"https://example.test/sharing?c=secret","publicid":"public-id"}""")
    private fun linkWith(url:String):JsonObject=link.deepCopy().apply { addProperty("url",url) }
    @Test fun groupLinkNeedsExactGroupAndCorrelation() {
        assertEquals(AgentInvitationOutcome.GroupLink("https://example.test/proxy/agentinvite?c=secret",24),parse("createInviteLink",mesh,"r",link))
        assertEquals(AgentInvitationOutcome.Malformed,parse("createInviteLink","mesh//other","r",link))
    }
    @Test fun emailOkOnlyMeansServerAcceptedRequest() { assertEquals(AgentInvitationOutcome.EmailAccepted,parse("inviteAgent",mesh,"r",ok)) }
    @Test fun shareCreatedKeepsPublicIdentifierAndHttpsLink() {
        assertEquals(AgentInvitationOutcome.ShareCreated("public-id","https://example.test/sharing?c=secret"),parse("createDeviceShareLink",node,"r",share))
        assertEquals(AgentInvitationOutcome.Malformed,parse("createDeviceShareLink","node//other","r",share))
    }
    @Test fun deniedAliasIsRejectedOnlyForShareOperations() {
        val denied=j("""{"action":"deviceShares","responseid":"r","result":"Access denied"}""")
        val rejected=AgentInvitationOutcome.Rejected("Access denied")
        assertEquals(rejected,parse("createDeviceShareLink",node,"r",denied))
        assertEquals(rejected,parse("removeDeviceShare",node,"r",denied))
        assertNull(parse("createInviteLink",mesh,"r",denied))
    }
    @Test fun removalWaitsForFinalResultAndKeepsRejection() {
        val first=j("""{"action":"removeDeviceShare","responseid":"r","nodeid":"node//n","publicid":"p","removed":{"publicid":"p"}}""")
        val done=j("""{"action":"removeDeviceShare","responseid":"r","result":"OK"}""")
        val missing=j("""{"action":"removeDeviceShare","responseid":"r","result":"Invalid device share identifier."}""")
        assertNull(parse("removeDeviceShare",node,"r",first))
        assertEquals(AgentInvitationOutcome.ShareRemoved,parse("removeDeviceShare",node,"r",done))
        assertEquals(AgentInvitationOutcome.Rejected("Invalid device share identifier."),parse("removeDeviceShare",node,"r",missing))
    }
    @Test fun staleOrUncorrelatedRepliesAreIgnored() {
        assertNull(parse("inviteAgent",mesh,"new",ok))
        assertNull(parse("createInviteLink",mesh,"r",ok))
        assertNull(parse("inviteAgent",mesh,"r",j("""{"action":"inviteAgent","result":"ok"}""")))
    }
    @Test fun exchangeCompletesOnceAndIgnoresLateReplies() {
        val exchange=AgentInvitationExchange("inviteAgent",mesh,"r")
        assertEquals(AgentInvitationOutcome.EmailAccepted,exchange.receive(ok))
        assertNull(exchange.receive(ok))
        assertNull(exchange.expire())
    }
    @Test fun timeoutIsUnknownNotRejection() {
        val exchange=AgentInvitationExchange("inviteAgent",mesh,"r")
        assertEquals(AgentInvitationOutcome.Unknown,exchange.expire())
        assertNull(exchange.receive(ok))
    }
    @Test fun unsafeOrIncompleteLinksAreMalformed() {
        listOf("http://example.test/agentinvite?c=x","https://user:pw@example.test/agentinvite?c=x","https://example.test/other?c=x","https://example.test/agentinvite","https://example.test/agentinvite?c=x y").forEach {
            assertEquals(it,AgentInvitationOutcome.Malformed,parse("createInviteLink",mesh,"r",linkWith(it)))
        }
    }
    @Test fun nonTextResultIsMalformed() { assertEquals(AgentInvitationOutcome.Malformed,parse("inviteAgent",mesh,"r",j("""{"action":"inviteAgent","responseid":"r","result":1}"""))) }
}
