package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserContactRequestTest {
    private fun user()=JSONObject().put("_id","user//person").put("email","person@example.invalid").put("quota",4096L)
    private fun state(u:JSONObject)=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",0xffffffffL),users=listOf(u),server=JSONObject().put("emailcheck",true).put("features",0x02000000L))
    @Test fun phoneChangePreservesQuota() { val u=user();val p=userContactPacket(u.getString("_id"),userContactSnapshot(u),"phone","+34612345678");assertTrue(userContactAllowed(state(u),u,p));assertEquals(4096L,p.getLong("quota")) }
    @Test fun changedEmailInvalidatesPendingVerification() { val u=user();val p=userContactPacket(u.getString("_id"),userContactSnapshot(u),"emailVerified",true);u.put("email","other@example.invalid");assertFalse(userContactAllowed(state(u),u,p)) }
    @Test fun injectedPermissionsAreRejected() { val u=user();val p=userContactPacket(u.getString("_id"),userContactSnapshot(u),"phone","+34612345678").put("siteadmin",0xffffffffL);assertFalse(userContactAllowed(state(u),u,p)) }
    @Test fun missingVerificationFlagMeansFalse() { assertTrue(userContactFieldMatches("emailVerified",JSONObject(),JSONObject().put("emailVerified",false)));assertFalse(userContactFieldMatches("emailVerified",JSONObject().put("emailVerified","false"),JSONObject().put("emailVerified",false))) }
    @Test fun noSmsAndNoPhoneRejectsPhoneWrite() { val u=user();val s=state(u).copy(server=JSONObject().put("features",0));val p=userContactPacket(u.getString("_id"),userContactSnapshot(u),"phone","+34612345678");assertFalse(userContactAllowed(s,u,p)) }
    @Test fun storedPhoneCanBeRemovedWithoutSmsCapability() { val u=user().put("phone","+34612345678");val s=state(u).copy(server=JSONObject());val p=userContactPacket(u.getString("_id"),userContactSnapshot(u),"phone","");assertTrue(userContactAllowed(s,u,p)) }
    @Test fun disabledEmailCapabilityRejectsStatusWrite() { val u=user();val s=state(u).copy(server=JSONObject().put("emailcheck",false));val p=userContactPacket(u.getString("_id"),userContactSnapshot(u),"emailVerified",true);assertFalse(userContactAllowed(s,u,p)) }
}
