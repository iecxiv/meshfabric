package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserGroupCreateRequestTest {
    private val server="https://example.invalid/"
    private fun state(site:Long=256)=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",site),listedUserGroups=emptyList(),userGroupPhase="ready")
    private fun packet(confirmed:Boolean=true)=profileUserGroupCreatePacket("user//admin",server,"Group","Description",confirmed)
    @Test fun emptyGroupRequestHasAnExactWireWhitelist() {val p=packet();assertTrue(profileUserGroupCreateAllowed(state(),p,server));assertEquals(setOf("action","name","desc"),profileUserGroupCreateWire(p).keys().asSequence().toSet())}
    @Test fun confirmationIsRequired() {assertFalse(profileUserGroupCreateAllowed(state(),packet(false),server))}
    @Test fun cloningCrossDomainAndAssignmentsAreRejected() {for(key in listOf("clone","domain","links","flags","consent")) assertFalse(profileUserGroupCreateAllowed(state(),packet().put(key,"injected"),server))}
    @Test fun actorServerAndRoleAreRevalidated() {assertFalse(profileUserGroupCreateAllowed(state(2),packet(),server));assertFalse(profileUserGroupCreateAllowed(state(),packet(),"https://other.invalid/"));assertFalse(profileUserGroupCreateAllowed(state(),packet().put("_groupActor","user//other"),server))}
    @Test fun invalidNamesAndDescriptionsAreRejected() {assertFalse(profileUserGroupCreateAllowed(state(),packet().put("name"," "),server));assertFalse(profileUserGroupCreateAllowed(state(),packet().put("name","a".repeat(65)),server));assertFalse(profileUserGroupCreateAllowed(state(),packet().put("desc","a".repeat(1025)),server))}
}
