package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserGroupMemberAddRequestTest {
    private val uid="user//alice"
    private val gid="ugrp//group"
    private val server="https://example.invalid/"
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",258L),users=listOf(JSONObject().put("_id",uid).put("links",JSONObject())),listedUserGroups=listOf(JSONObject().put("_id",gid).put("links",JSONObject())),userGroupPhase="ready")
    private fun packet(s:ProfileState)=JSONObject().put("action","addusertousergroup").put("ugrpid",gid).put("usernames",JSONArray().put("alice")).put("_addActor",s.id).put("_addServer",server).put("_addGroupBefore",profileUserGroupDeleteSnapshot(s.listedUserGroups!!.single())).put("_addUser",uid).put("_addUserBefore",profileUserGroupDeleteSnapshot(s.users.single())).put("_addConfirmed",true)
    @Test fun exactSingleAccountWireIsAllowed() {val s=state();val p=packet(s);assertTrue(profileUserGroupMemberAddAllowed(s,p,server));assertEquals(setOf("action","ugrpid","usernames"),profileUserGroupMemberAddWire(p).keys().asSequence().toSet())}
    @Test fun duplicateMembershipAndStaleCopiesAreBlocked() {val s=state();val p=packet(s);s.users.single().getJSONObject("links").put(gid,JSONObject().put("rights",0x80000001L));assertFalse(profileUserGroupMemberAddAllowed(s,p,server));assertFalse(profileUserGroupMemberAddAllowed(s,packet(s),server))}
    @Test fun externalGroupsOtherServersAndUnconfirmedRequestsAreBlocked() {val s=state();val p=packet(s);assertFalse(profileUserGroupMemberAddAllowed(s,p,"https://other.invalid/"));assertFalse(profileUserGroupMemberAddAllowed(s,JSONObject(p.toString()).put("_addConfirmed",false),server));s.listedUserGroups!!.single().put("membershipType","oidc");assertFalse(profileUserGroupMemberAddAllowed(s,packet(s),server))}
    @Test fun acknowledgementRequiresExactlyOneAdditionAndNoFailures() {assertTrue(profileUserGroupMemberAddAck(JSONObject().put("result","ok").put("added",1).put("failed",0)));assertFalse(profileUserGroupMemberAddAck(JSONObject().put("result","ok")));assertFalse(profileUserGroupMemberAddAck(JSONObject().put("result","ok").put("added",1).put("failed",1)))}
    @Test fun readbackRequiresAcknowledgementAndTheExpectedLink() {val g=JSONObject().put("links",JSONObject().put(uid,JSONObject().put("userid",uid).put("rights",1)));assertTrue(profileUserGroupMemberAddObserved(g,uid,true));assertFalse(profileUserGroupMemberAddObserved(g,uid,false));g.getJSONObject("links").getJSONObject(uid).put("userid","user//other");assertFalse(profileUserGroupMemberAddObserved(g,uid,true))}
}
