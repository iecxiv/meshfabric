package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralShareRevokePolicyTest {
    private val node="node//a";private val actor="user//admin"
    private val share=GeneralShare("secret-public-id","Invitado",actor,2L,true,null,1800000000000L)
    private fun build(current:List<GeneralShare> = listOf(share),rights:Long=524296L,owner:String=actor)=generalShareRevokePacket(node,"request",owner,share,current,rights)
    @Test fun exactSingleTargetAndNoBulkMutation() {val p=build();assertEquals("removeDeviceShare",p.get("action").asString);assertEquals(node,p.get("nodeid").asString);assertEquals(share.id,p.get("publicid").asString);assertEquals(4,p.entrySet().size);assertFalse(p.has("nodeids"))}
    @Test fun missingEntryCannotBeRevoked() {assertTrue(runCatching {build(emptyList())}.isFailure)}
    @Test fun duplicateIdentifierCannotBeRevoked() {assertTrue(runCatching {build(listOf(share,share))}.isFailure)}
    @Test fun changedGuestInvalidatesReview() {assertTrue(runCatching {build(listOf(share.copy(guest="Otro")))}.isFailure)}
    @Test fun changedOwnerInvalidatesReview() {assertTrue(runCatching {build(listOf(share.copy(owner="user//other")))}.isFailure)}
    @Test fun changedScopeInvalidatesReview() {assertTrue(runCatching {build(listOf(share.copy(protocols=7L,viewOnly=false)))}.isFailure)}
    @Test fun changedExpiryInvalidatesReview() {assertTrue(runCatching {build(listOf(share.copy(expires=1800000001000L)))}.isFailure)}
    @Test fun anotherAccountCannotRevokeThisEntry() {assertTrue(runCatching {build(owner="user//other")}.isFailure)}
    @Test fun missingRightsCannotBeReplacedByManagement() {for(rights in listOf(4L,8L,524288L)) assertTrue(runCatching {build(rights=rights)}.isFailure)}
    @Test fun crossDomainActorIsRejected() {assertTrue(runCatching {build(owner="user/site/admin")}.isFailure)}
    @Test fun readbackRequiresExactRequestCorrelation() {val p=JsonObject().apply {addProperty("action","deviceShares");addProperty("nodeid",node);add("deviceShares",JsonArray())};assertNull(generalShareRevokeList(p,node,"request"));p.addProperty("responseid","other");assertNull(generalShareRevokeList(p,node,"request"));p.addProperty("responseid","request");assertTrue(requireNotNull(generalShareRevokeList(p,node,"request")).isEmpty())}
    @Test fun successAcknowledgementIsNotAbsenceProof() {val p=JsonObject().apply {addProperty("action","removeDeviceShare");addProperty("responseid","request");addProperty("result","ok")};assertNull(generalShareRevokeList(p,node,"request"))}
    @Test fun wrongReadbackNodeCannotConfirmAbsence() {val p=JsonObject().apply {addProperty("action","deviceShares");addProperty("nodeid","node//b");addProperty("responseid","request");add("deviceShares",JsonArray())};assertNull(generalShareRevokeList(p,node,"request"))}
    @Test fun fingerprintDoesNotExposeCredential() {val fingerprint=generalShareFingerprint(share.id);assertEquals(16,fingerprint.length);assertNotEquals(share.id,fingerprint);assertNotEquals(fingerprint,generalShareFingerprint("another-id"))}
}
