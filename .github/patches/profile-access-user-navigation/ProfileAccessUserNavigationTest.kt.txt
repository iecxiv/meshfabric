package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAccessUserNavigationTest {
    private fun state(actorSite:Long=2,targetSite:Long=0,targetId:String="user//alice")=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",actorSite),users=listOf(JSONObject().put("_id",targetId).put("siteadmin",targetSite)))
    @Test fun onlyAnExistingAuthorizedUserCanBeOpened() {assertNotNull(profileAccessUserTarget(state(),"user//alice"));assertNull(profileAccessUserTarget(state(),"user//missing"));assertNull(profileAccessUserTarget(state(0),"user//alice"))}
    @Test fun domainAndFullAdministratorProtectionRemainIntact() {assertNull(profileAccessUserTarget(state(targetId="user/other/alice"),"user/other/alice"));assertNull(profileAccessUserTarget(state(targetSite=0xffffffffL),"user//alice"));assertNotNull(profileAccessUserTarget(state(actorSite=0xffffffffL,targetSite=0xffffffffL),"user//alice"))}
    @Test fun busyAndIncompleteReadsDoNotOpenEditors() {assertNull(profileAccessUserTarget(state().copy(busy=true),"user//alice"));assertNull(profileAccessUserTarget(state().copy(readError=true),"user//alice"))}
}
