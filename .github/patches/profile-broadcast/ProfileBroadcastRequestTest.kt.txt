package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileBroadcastRequestTest {
    private val server="https://example.invalid/"
    private fun actor()=JSONObject().put("_id","user//admin").put("siteadmin",2L)
    private fun state(u:JSONObject)=ProfileState(ready=true,user=u)
    @Test fun confirmedScopedRequestIsAllowed() {val u=actor();val p=profileBroadcastPacket(profileBroadcastScope(u,server),"Maintenance",true);assertTrue(profileBroadcastAllowed(state(u),p,server))}
    @Test fun confirmationIsRequired() {val u=actor();val p=profileBroadcastPacket(profileBroadcastScope(u,server),"Maintenance");assertFalse(profileBroadcastAllowed(state(u),p,server))}
    @Test fun changedRealmsBlockSending() {val u=actor().put("groups",JSONArray().put("realm-a"));val p=profileBroadcastPacket(profileBroadcastScope(u,server),"Maintenance",true);u.put("groups",JSONArray().put("realm-b"));assertFalse(profileBroadcastAllowed(state(u),p,server))}
    @Test fun injectedRecipientIsRejected() {val u=actor();val p=profileBroadcastPacket(profileBroadcastScope(u,server),"Maintenance",true).put("userid","user//person");assertFalse(profileBroadcastAllowed(state(u),p,server))}
    @Test fun roleLossOrServerChangeBlocksSending() {val u=actor();val p=profileBroadcastPacket(profileBroadcastScope(u,server),"Maintenance",true);assertFalse(profileBroadcastAllowed(state(u),p,"https://other.invalid/"));u.put("siteadmin",0);assertFalse(profileBroadcastAllowed(state(u),p,server))}
}
