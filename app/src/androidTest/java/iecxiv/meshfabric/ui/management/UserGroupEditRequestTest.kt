package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserGroupEditRequestTest {
    private fun group()=JSONObject().put("_id","ugrp//group").put("name","Old").put("desc","Description").put("flags",0x80000002L).put("consent",511L).put("links",JSONObject())
    private fun state(g:JSONObject,site:Long=256)=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",site),listedUserGroups=listOf(g),userGroupPhase="ready")
    @Test fun renameSendsOnlyChangedFields() {val g=group();val s=state(g);val p=profileUserGroupEditPacket(s.id,g,"New","Description",true);assertTrue(profileUserGroupEditAllowed(s,p));assertEquals(setOf("action","ugrpid","name"),profileUserGroupEditWire(p).keys().asSequence().toSet());assertEquals(0x80000002L,g.getLong("flags"));assertFalse(profileUserGroupEditObserved(g,JSONObject().put("name","New")));assertTrue(profileUserGroupEditObserved(JSONObject(g.toString()).put("name","New"),JSONObject().put("name","New")))}
    @Test fun externalNameIsProtectedButDescriptionIsEditable() {val g=group().put("membershipType","oidc");val s=state(g);assertFalse(profileUserGroupEditAllowed(s,profileUserGroupEditPacket(s.id,g,"New","Description",true)));assertTrue(profileUserGroupEditAllowed(s,profileUserGroupEditPacket(s.id,g,"Old","Updated",true)))}
    @Test fun staleKnownCopyBlocksSending() {val g=group();val s=state(g);val p=profileUserGroupEditPacket(s.id,g,"New","Description",true);g.put("desc","Concurrent edit");assertFalse(profileUserGroupEditAllowed(s,p))}
    @Test fun foreignDomainAndRoleLossBlockSending() {val g=group();val s=state(g);val p=profileUserGroupEditPacket(s.id,g,"New","Description",true);assertFalse(profileUserGroupEditAllowed(state(g,2),p));val foreign=group().put("_id","ugrp/other/group");assertFalse(profileUserGroupEditAllowed(state(foreign),profileUserGroupEditPacket(s.id,foreign,"New","Description",true)))}
    @Test fun confirmationAndExactWhitelistAreRequired() {val g=group();val s=state(g);val p=profileUserGroupEditPacket(s.id,g,"New","Description");assertFalse(profileUserGroupEditAllowed(s,p));p.put("_groupConfirmed",true).put("consent",0);assertFalse(profileUserGroupEditAllowed(s,p))}
}
