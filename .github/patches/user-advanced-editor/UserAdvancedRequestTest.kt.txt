package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserAdvancedRequestTest {
    private fun actor()=JSONObject().put("_id","user//admin").put("siteadmin",0xffffffffL)
    private fun target()=JSONObject().put("_id","user//person").put("quota",2048L)
    private fun state(u:JSONObject)=ProfileState(ready=true,user=actor(),users=listOf(u))
    @Test fun changedConsentPreservesQuotaAndSnapshot() {
        val u=target();val before=userAdvancedSnapshot(u)
        val draft=JSONObject(before.toString()).put("consent",128L)
        val p=userAdvancedPacket(u.getString("_id"),before,draft)
        assertEquals(2048L,p.getLong("quota"));assertTrue(userAdvancedAllowed(state(u),u,p))
    }
    @Test fun staleQuotaRejectsEntireEdit() {
        val u=target();val before=userAdvancedSnapshot(u)
        val p=userAdvancedPacket(u.getString("_id"),before,JSONObject(before.toString()).put("consent",1L))
        u.put("quota",4096L);assertFalse(userAdvancedAllowed(state(u),u,p))
    }
    @Test fun unknownFieldIsRejected() {
        val u=target();val before=userAdvancedSnapshot(u)
        val p=userAdvancedPacket(u.getString("_id"),before,JSONObject(before.toString()).put("consent",1L)).put("phone","123")
        assertFalse(userAdvancedAllowed(state(u),u,p))
    }
    @Test fun removingConsentMatchesAbsentServerValue() { assertTrue(userEditFieldMatches("consent",JSONObject(),JSONObject().put("consent",0L))) }
}
