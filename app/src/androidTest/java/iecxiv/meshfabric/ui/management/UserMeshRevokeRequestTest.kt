package iecxiv.meshfabric.ui.management
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class UserMeshRevokeRequestTest {
    private val actor="user//admin";private val uid="user//target";private val mid="mesh//group";private val server="https://example.invalid/"
    private fun state():ProfileState {
        val user=JSONObject().put("_id",uid).put("siteadmin",0).put("links",JSONObject().put(mid,JSONObject().put("rights",8)))
        val mesh=JSONObject().put("_id",mid).put("links",JSONObject().put(uid,JSONObject().put("rights",8)).put(actor,JSONObject().put("rights",0xffffffffL)))
        return ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",2),users=listOf(user),groups=listOf(mesh))
    }
    private fun packet(s:ProfileState,confirmed:Boolean=true)=profileUserMeshRevokePacket(actor,server,s.users.single(),s.groups.single(),confirmed)
    @Test fun exactWireAndConfirmationAreRequired() {
        val s=state();val p=packet(s)
        assertTrue(profileUserMeshRevokeAllowed(s,p,server))
        assertFalse(profileUserMeshRevokeAllowed(s,packet(s,false),server))
        assertEquals(setOf("action","userid","meshid"),profileUserMeshRevokeWire(p).keys().asSequence().toSet())
        assertFalse(profileUserMeshRevokeAllowed(s,JSONObject(p.toString()).put("meshadmin",0),server))
        assertFalse(profileUserMeshRevokeAllowed(s,p,"https://other.invalid/"))
    }
    @Test fun incompleteMismatchedAndStaleRecordsAreBlocked() {
        val s=state();val p=packet(s)
        assertFalse(profileUserMeshRevokeAllowed(s.copy(busy=true),p,server))
        assertFalse(profileUserMeshRevokeAllowed(s.copy(readError=true),p,server))
        assertFalse(profileUserMeshRevokeAllowed(s.copy(users=emptyList()),p,server))
        s.groups.single().getJSONObject("links").getJSONObject(uid).put("rights",16)
        assertFalse(profileUserMeshRevokeAllowed(s,p,server))
        assertFalse(profileUserMeshRevokeAllowed(s,packet(s),server))
    }
    @Test fun unknownRightsAreOnlyRemovedAsOneExplicitWholeAssignment() {
        val s=state()
        s.users.single().getJSONObject("links").getJSONObject(mid).put("rights",0x80000008L)
        s.groups.single().getJSONObject("links").getJSONObject(uid).put("rights",0x80000008L)
        assertTrue(profileUserMeshRevokeAllowed(s,packet(s),server))
        s.groups.single().getJSONObject("links").remove(actor)
        assertFalse(profileUserMeshRevokeAllowed(s,packet(s),server))
    }
    @Test fun acknowledgementAndTwoReadsAndBothExistingRecordsAreRequired() {
        val s=state()
        s.users.single().getJSONObject("links").remove(mid)
        s.groups.single().getJSONObject("links").remove(uid)
        for(ack in listOf(false,true)) for(userRead in listOf(false,true)) for(meshRead in listOf(false,true)) assertEquals(ack && userRead && meshRead,profileUserMeshRevokeReadbackReady(s,uid,mid,ack,userRead,meshRead))
        assertFalse(profileUserMeshRevokeReadbackReady(s.copy(users=emptyList()),uid,mid,true,true,true))
        assertFalse(profileUserMeshRevokeReadbackReady(s.copy(groups=emptyList()),uid,mid,true,true,true))
        s.users.single().getJSONObject("links").put(mid,JSONObject().put("rights",8))
        assertFalse(profileUserMeshRevokeReadbackReady(s,uid,mid,true,true,true))
    }
    @Test fun responsesMustMatchThePendingRequest() {
        val p=JSONObject().put("action","removemeshuser").put("responseid","request").put("result","ok")
        assertTrue(profileUserMeshRevokeAck(p,"request"))
        assertFalse(profileUserMeshRevokeAck(p,"other"))
        assertFalse(profileUserMeshRevokeAck(JSONObject(p.toString()).put("error","denied"),"request"))
    }
}
