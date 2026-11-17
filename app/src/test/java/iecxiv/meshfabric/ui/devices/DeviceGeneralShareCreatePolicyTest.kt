package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralShareCreatePolicyTest {
    private val node="node//a";private val actor="user//admin";private val now=1800000000L
    private val meta=GeneralMeta("Equipo",emptyList(),524296L,"Linux","mesh//a")
    private val draft=generalShareCreateDraft("Invitado",meta.mesh,15,now)
    private fun response()=JsonObject().apply {addProperty("action","createDeviceShareLink");addProperty("responseid","write");addProperty("publicid","new-id");addProperty("url","https://server.test/site/sharing?id=new-id")}
    private fun readback()=JsonObject().apply {
        addProperty("action","deviceShares");addProperty("responseid","read");addProperty("nodeid",node)
        add("deviceShares",JsonArray().apply {add(JsonObject().apply {addProperty("publicid","new-id");addProperty("guestName",draft.guest);addProperty("userid",actor);addProperty("p",2);addProperty("viewOnly",true);addProperty("consent",9);addProperty("startTime",draft.start*1000L);addProperty("expireTime",draft.end*1000L)})})
    }
    private fun verified(p:JsonObject=readback(),before:Set<String> = emptySet())=generalShareCreateVerified(p,node,"read",actor,draft,"new-id",before)
    @Test fun packetGrantsOnlyViewOnlyDesktopWithPromptAndNotification() {val p=generalShareCreatePacket(node,"write",draft,meta,now);assertEquals(2,p.get("p").asInt);assertTrue(p.get("viewOnly").asBoolean);assertEquals(9,p.get("consent").asInt);assertEquals(node,p.get("nodeid").asString);assertFalse(p.has("nodeids"));assertFalse(p.has("expire"));assertFalse(p.has("recurring"))}
    @Test fun finiteReviewedTimesUseSeconds() {val p=generalShareCreatePacket(node,"write",draft,meta,now);assertEquals(now,p.get("start").asLong);assertEquals(now+900L,p.get("end").asLong)}
    @Test fun staleReviewIsBlocked() {assertTrue(runCatching {generalShareCreatePacket(node,"write",draft,meta,now+121L)}.isFailure)}
    @Test fun invalidDurationIsBlocked() {assertTrue(runCatching {generalShareCreateDraft("Invitado",meta.mesh,0,now)}.isFailure);assertTrue(runCatching {generalShareCreatePacket(node,"write",draft.copy(end=now+86400L),meta,now)}.isFailure)}
    @Test fun changedGroupAndLostRightsAreBlocked() {assertTrue(runCatching {generalShareCreatePacket(node,"write",draft,meta.copy(mesh="mesh//b"),now)}.isFailure);assertTrue(runCatching {generalShareCreatePacket(node,"write",draft,meta.copy(rights=8L),now)}.isFailure)}
    @Test fun desktopRestrictionIsRespectedExceptFullAdmin() {assertTrue(runCatching {generalShareCreatePacket(node,"write",draft,meta.copy(rights=524296L or 65536L),now)}.isFailure);assertTrue(runCatching {generalShareCreatePacket(node,"write",draft,meta.copy(rights=0xffffffffL),now)}.isSuccess)}
    @Test fun validServerUrlIsNotRewritten() {assertEquals("https://server.test/site/sharing?id=new-id",requireNotNull(generalShareCreateReply(response(),node,"write","https://server.test/site")).url)}
    @Test fun foreignOriginOrPathIsRejected() {for(url in listOf("https://other.test/site/sharing?id=new-id","https://server.test/sharing?id=new-id","https://server.test/site/other?id=new-id")) assertTrue(runCatching {generalShareCreatedUrl("https://server.test/site","new-id",url)}.isFailure)}
    @Test fun insecureCredentialBearingOrWrongIdUrlIsRejected() {for(url in listOf("http://server.test/site/sharing?id=new-id","https://u:p@server.test/site/sharing?id=new-id","https://server.test/site/sharing?id=other","https://server.test/site/sharing?id=new-id&auth=secret")) assertTrue(runCatching {generalShareCreatedUrl("https://server.test/site","new-id",url)}.isFailure)}
    @Test fun replyNeedsExactCorrelationAndConsistentDevice() {assertNull(generalShareCreateReply(response(),node,"other","https://server.test/site"));assertNull(generalShareCreateReply(response().apply {addProperty("nodeid","node//b")},node,"write","https://server.test/site"))}
    @Test fun verifiedNewEntryIsAccepted() {assertTrue(verified())}
    @Test fun preexistingIdentifierCannotConfirmNewCreation() {assertFalse(verified(before=setOf("new-id")))}
    @Test fun widerScopeCannotBeRevealed() {val p=readback();p.getAsJsonArray("deviceShares")[0].asJsonObject.addProperty("p",7);assertFalse(verified(p))}
    @Test fun missingConsentOrWrongOwnerCannotBeRevealed() {val p=readback();p.getAsJsonArray("deviceShares")[0].asJsonObject.remove("consent");assertFalse(verified(p));val q=readback();q.getAsJsonArray("deviceShares")[0].asJsonObject.addProperty("userid","user//other");assertFalse(verified(q))}
    @Test fun changedExpiryCannotBeRevealed() {val p=readback();p.getAsJsonArray("deviceShares")[0].asJsonObject.addProperty("expireTime",draft.end*1000L+1000L);assertFalse(verified(p))}
    @Test fun diagnosticStringRedactsLink() {val link=GeneralShareCreatedLink("secret","https://server.test/sharing?id=secret");assertFalse(link.toString().contains("secret"))}
}
