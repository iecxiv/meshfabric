package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralSharesPolicyTest {
    private val node="node//a"
    private fun reply()=JsonObject().apply {addProperty("action","deviceShares");addProperty("nodeid",node);addProperty("responseid","request");add("deviceShares",JsonArray())}
    private fun row()=JsonObject().apply {addProperty("publicid","private-id");addProperty("guestName","Invitado");addProperty("p",2);addProperty("viewOnly",true);addProperty("expireTime",1800000000000L)}
    @Test fun requestIsReadOnlyAndTargetsExactNode() {val p=generalSharesRequest(node,"request");assertEquals("deviceShares",p.get("action").asString);assertEquals(node,p.get("nodeid").asString);assertEquals(3,p.entrySet().size)}
    @Test fun guestSharingAndRemoteControlAreRequired() {for(rights in listOf(0L,4L,8L,524288L)) assertFalse(generalSharesAllowed(rights));assertTrue(generalSharesAllowed(524296L))}
    @Test fun explicitEmptyArrayConfirmsEmptyList() {assertTrue(requireNotNull(generalSharesReply(reply(),node,"request")).isEmpty())}
    @Test fun missingListCannotBeTreatedAsEmpty() {val p=reply();p.remove("deviceShares");assertTrue(runCatching {generalSharesReply(p,node,"request")}.isFailure)}
    @Test fun wrongDeviceIsIgnoredEvenWithMatchingRequest() {val p=reply();p.addProperty("nodeid","node//b");assertNull(generalSharesReply(p,node,"request"))}
    @Test fun wrongRequestIsIgnoredEvenWithMatchingNode() {assertNull(generalSharesReply(reply(),node,"other"))}
    @Test fun unboundReplyIsIgnored() {val p=reply();p.remove("nodeid");p.remove("responseid");assertNull(generalSharesReply(p,node,"request"))}
    @Test fun serverErrorCannotBecomeEmptySuccess() {val p=reply();p.addProperty("result","denied");assertTrue(runCatching {generalSharesReply(p,node,"request")}.isFailure)}
    @Test fun parsesReportedFieldsWithoutInventingMissingMetadata() {val p=reply();p.getAsJsonArray("deviceShares").add(row());val r=requireNotNull(generalSharesReply(p,node,"request")).single();assertEquals("Invitado",r.guest);assertEquals(true,r.viewOnly);assertNull(r.start);assertEquals("",r.owner)}
    @Test fun duplicateIdentifiersAreRejected() {val p=reply();p.getAsJsonArray("deviceShares").apply {add(row());add(row())};assertTrue(runCatching {generalSharesReply(p,node,"request")}.isFailure)}
    @Test fun contradictoryRowTargetIsRejected() {val p=reply();p.getAsJsonArray("deviceShares").add(row().apply {addProperty("nodeid","node//b")});assertTrue(runCatching {generalSharesReply(p,node,"request")}.isFailure)}
    @Test fun fractionalTimestampIsRejected() {val p=reply();p.getAsJsonArray("deviceShares").add(row().apply {addProperty("expireTime",1.5)});assertTrue(runCatching {generalSharesReply(p,node,"request")}.isFailure)}
}
