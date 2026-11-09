package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserGroupDeleteRequestTest {
    private val server="https://example.invalid/"
    private fun group()=JSONObject().put("_id","ugrp//one").put("name","Group").put("links",JSONObject())
    private fun state(g:JSONObject,site:Long=256)=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",site),listedUserGroups=listOf(g),userGroupPhase="ready")
    private fun packet(g:JSONObject)=profileUserGroupDeletePacket("user//admin",server,g,"Group",true)
    @Test fun confirmedDeletionHasAnExactWireWhitelist() {val g=group();val p=packet(g);assertTrue(profileUserGroupDeleteAllowed(state(g),p,server));assertEquals(setOf("action","ugrpid"),profileUserGroupDeleteWire(p).keys().asSequence().toSet())}
    @Test fun missingOrUnknownLinksAreRejected() {val missing=group().apply {remove("links")};assertFalse(profileUserGroupDeleteShape("user//admin",missing));val unknown=group().put("links",JSONObject().put("node//device",JSONObject()));assertFalse(profileUserGroupDeleteShape("user//admin",unknown))}
    @Test fun externalGroupWithUsersIsBlocked() {val g=group().put("membershipType","oidc").put("links",JSONObject().put("user//person",JSONObject()));assertFalse(profileUserGroupDeleteAllowed(state(g),packet(g),server))}
    @Test fun changedLinksAndMissingTargetNeverConfirmDeletion() {val g=group();val p=packet(g);g.getJSONObject("links").put("user//person",JSONObject());assertFalse(profileUserGroupDeleteAllowed(state(g),p,server));assertFalse(profileUserGroupDeleteAllowed(state(g).copy(listedUserGroups=emptyList()),p,server))}
    @Test fun confirmationIdentityServerRoleAndWhitelistAreChecked() {val g=group();val p=packet(g);assertFalse(profileUserGroupDeleteAllowed(state(g),JSONObject(p.toString()).put("_deleteConfirmed",false),server));assertFalse(profileUserGroupDeleteAllowed(state(g),JSONObject(p.toString()).put("_deleteActor","user//other"),server));assertFalse(profileUserGroupDeleteAllowed(state(g),p,"https://other.invalid/"));assertFalse(profileUserGroupDeleteAllowed(state(g,2),p,server));assertFalse(profileUserGroupDeleteAllowed(state(g),JSONObject(p.toString()).put("domain","other"),server))}
    @Test fun crossDomainLinksAreBlockedAndSnapshotsAreOrderIndependent() {val g=group().put("links",JSONObject().put("user/other/person",JSONObject()));assertFalse(profileUserGroupDeleteShape("user//admin",g));assertEquals(profileUserGroupDeleteSnapshot(JSONObject("{\"a\":1,\"b\":{\"x\":2,\"y\":3}}")),profileUserGroupDeleteSnapshot(JSONObject("{\"b\":{\"y\":3,\"x\":2},\"a\":1}")))}
}
