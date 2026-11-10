package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class AgentInvitationRepliesTest {
    private val mesh="mesh//group"
    private fun j(s:String):JsonObject=JsonParser.parseString(s).asJsonObject
    private fun parse(a:String,t:String,r:String,p:JsonObject)=AgentInvitationReplies.parse(a,t,r,p)
    private val link=j("""{"action":"createInviteLink","meshid":"mesh//group","responseid":"r","url":"https://example.test/agentinvite?c=secret","expire":24}""")
    private val ok=j("""{"action":"inviteAgent","responseid":"r","result":"ok"}""")
    @Test fun groupLinkContainsInstallationUrl() {assertEquals(AgentInvitationOutcome.GroupLink("https://example.test/agentinvite?c=secret",24),parse("createInviteLink",mesh,"r",link))}
    @Test fun emailAcceptanceIsNotDelivery() {assertEquals(AgentInvitationOutcome.EmailAccepted,parse("inviteAgent",mesh,"r",ok))}
    @Test fun wrongGroupIsMalformed() {assertEquals(AgentInvitationOutcome.Malformed,parse("createInviteLink","mesh//other","r",link))}
    @Test fun oldResponseIdIsIgnored() {assertNull(parse("createInviteLink",mesh,"old",link))}
    @Test fun sharingResponseCannotCompleteInstallation() {val p=j("""{"action":"deviceShares","responseid":"r","result":"Access denied"}""");assertNull(parse("createInviteLink",mesh,"r",p))}
    @Test fun sharingOperationIsOutsideParserScope() {assertEquals(AgentInvitationOutcome.Malformed,parse("createDeviceShareLink","node//device","r",ok))}
    @Test fun exchangeRejectsSharingOperations() {for(a in listOf("createDeviceShareLink","deviceShares","removeDeviceShare")) assertThrows(IllegalArgumentException::class.java) {AgentInvitationExchange(a,"node//device","r")}}
    @Test fun exchangeCompletesOnlyOnce() {val e=AgentInvitationExchange("inviteAgent",mesh,"r");assertEquals(AgentInvitationOutcome.EmailAccepted,e.receive(ok));assertNull(e.receive(ok));assertNull(e.expire())}
    @Test fun timeoutRemainsUnconfirmed() {val e=AgentInvitationExchange("inviteAgent",mesh,"r");assertEquals(AgentInvitationOutcome.Unknown,e.expire());assertNull(e.receive(ok))}
    @Test fun unsafeInstallationLinksAreRejected() {for(u in listOf("http://example.test/agentinvite?c=x","https://user:pw@example.test/agentinvite?c=x","https://example.test/sharing?c=x","https://example.test/agentinvite")) assertEquals(AgentInvitationOutcome.Malformed,parse("createInviteLink",mesh,"r",link.deepCopy().apply {addProperty("url",u)}))}
}
