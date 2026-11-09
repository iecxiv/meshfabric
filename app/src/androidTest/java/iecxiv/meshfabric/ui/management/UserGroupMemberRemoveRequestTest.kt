package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserGroupMemberRemoveRequestTest {
    private val server="https://example.invalid/"
    private val id="user//person"
    private fun group()=JSONObject().put("_id","ugrp//one").put("name","Group").put("links",JSONObject().put(id,JSONObject().put("rights",1)))
    private fun state(g:JSONObject,site:Long=256)=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",site),listedUserGroups=listOf(g),userGroupPhase="ready")
    private fun packet(g:JSONObject)=profileUserGroupMemberRemovePacket("user//admin",server,g,id,true)
    @Test fun groupAdministrationAloneAllowsTheExactRequest() {val g=group();val p=packet(g);assertTrue(profileUserGroupMemberRemoveAllowed(state(g),p,server));assertEquals(setOf("action","ugrpid","userid"),profileUserGroupMemberRemoveWire(p).keys().asSequence().toSet())}
    @Test fun externalGroupsAndRoleLossAreBlocked() {val g=group().put("membershipType","oidc");assertFalse(profileUserGroupMemberRemoveAllowed(state(g),packet(g),server));val manual=group();assertFalse(profileUserGroupMemberRemoveAllowed(state(manual,2),packet(manual),server))}
    @Test fun staleLinksAndMissingGroupsAreBlocked() {val g=group();val p=packet(g);g.getJSONObject("links").getJSONObject(id).put("rights",3);assertFalse(profileUserGroupMemberRemoveAllowed(state(g),p,server));assertFalse(profileUserGroupMemberRemoveAllowed(state(g).copy(listedUserGroups=emptyList()),p,server))}
    @Test fun identityServerPreflightAndConfirmationAreChecked() {val g=group();val p=packet(g);assertFalse(profileUserGroupMemberRemoveAllowed(state(g).copy(userGroupPhase="loading"),p,server));assertFalse(profileUserGroupMemberRemoveAllowed(state(g),p,"https://other.invalid/"));assertFalse(profileUserGroupMemberRemoveAllowed(state(g),JSONObject(p.toString()).put("_memberActor","user//other"),server));assertFalse(profileUserGroupMemberRemoveAllowed(state(g),JSONObject(p.toString()).put("_memberConfirmed",false),server))}
    @Test fun mismatchedIdentityAndExtraFieldsAreRejected() {val g=group();g.getJSONObject("links").getJSONObject(id).put("userid","user//other");assertFalse(profileUserGroupMemberRemoveAllowed(state(g),packet(g),server));val manual=group();assertFalse(profileUserGroupMemberRemoveAllowed(state(manual),packet(manual).put("rights",1),server))}
    @Test fun readErrorsNeverProveRemoval() {assertTrue(profileUserGroupMemberReadValid(JSONObject()));assertFalse(profileUserGroupMemberReadValid(JSONObject().put("error","denied")));assertFalse(profileUserGroupMemberReadValid(JSONObject().put("err","denied")));assertFalse(profileUserGroupMemberReadValid(JSONObject().put("result","denied")))}
    @Test fun acknowledgementAndExplicitLinksAreBothRequired() {val g=group();assertFalse(profileUserGroupMemberRemovalObserved(g,id,true));g.getJSONObject("links").remove(id);assertFalse(profileUserGroupMemberRemovalObserved(g,id,false));assertTrue(profileUserGroupMemberRemovalObserved(g,id,true));g.remove("links");assertFalse(profileUserGroupMemberRemovalObserved(g,id,true))}
}
