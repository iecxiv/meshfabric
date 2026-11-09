package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserGroupMemberAddIntegrationPolicyTest {
    private fun group()=JSONObject().put("_id","ugrp//group").put("links",JSONObject())
    private fun account(id:String="user//alice")=JSONObject().put("_id",id).put("links",JSONObject())
    @Test fun candidateSelectionKeepsCaseRestrictions() {assertTrue(profileMemberAddCandidate("user//admin",group(),account()));assertFalse(profileMemberAddCandidate("user//admin",group(),account("user//Alice")))}
    @Test fun existingAndUnknownLinksAreNotSelectable() {val g=group();val u=account();u.getJSONObject("links").put("ugrp//group",JSONObject().put("rights",3));assertFalse(profileMemberAddCandidate("user//admin",g,u));u.remove("links");assertFalse(profileMemberAddCandidate("user//admin",g,u))}
    @Test fun packetRequiresSeparateConfirmation() {val g=group();val u=account();val s=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",258L),users=listOf(u),listedUserGroups=listOf(g),userGroupPhase="ready");val p=profileMemberAddPacket(s.id,"https://example.invalid/",g,u);assertFalse(profileUserGroupMemberAddAllowed(s,p,"https://example.invalid/"));p.put("_addConfirmed",true);assertTrue(profileUserGroupMemberAddAllowed(s,p,"https://example.invalid/"))}
}
