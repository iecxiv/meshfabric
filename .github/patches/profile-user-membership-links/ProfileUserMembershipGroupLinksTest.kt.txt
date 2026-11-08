package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileUserMembershipGroupLinksTest {
    private val uid="user//alice"
    private val gid="ugrp//group"
    private fun user()=JSONObject().put("_id",uid).put("links",JSONObject())
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",2L))
    @Test fun accountAndGroupLinksAreCombinedWithoutDuplicates() {val u=user();u.getJSONObject("links").put(gid,JSONObject().put("rights",1));val g=JSONObject().put("_id",gid).put("links",JSONObject().put(uid,JSONObject().put("rights",1)));assertEquals(listOf(gid),profileUserMembershipGroupIds(state().copy(userGroups=listOf(g)),u))}
    @Test fun administrativeRealmsAreNotMemberships() {val u=user().put("groups",JSONArray().put("administrators"));assertEquals(emptyList<String>(),profileUserMembershipGroupIds(state(),u))}
    @Test fun missingDataIsNotAnEmptyMembershipList() {val u=JSONObject().put("_id",uid);assertNull(profileUserMembershipGroupIds(state(),u))}
    @Test fun foreignDomainAndMalformedLinksAreIgnored() {val u=user();u.getJSONObject("links").put("ugrp/other/group",JSONObject()).put("mesh//group",JSONObject()).put(gid,JSONObject.NULL);assertEquals(emptyList<String>(),profileUserMembershipGroupIds(state(),u))}
    @Test fun busyOrUnauthorizedViewsDoNotEnumerateMemberships() {val u=user();assertNull(profileUserMembershipGroupIds(state().copy(busy=true),u));assertNull(profileUserMembershipGroupIds(state().copy(user=JSONObject().put("_id","user//admin").put("siteadmin",0L)),u))}
}
