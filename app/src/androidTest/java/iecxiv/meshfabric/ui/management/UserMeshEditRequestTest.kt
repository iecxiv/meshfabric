package iecxiv.meshfabric.ui.management
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class UserMeshEditRequestTest {
    private val actor="user//admin";private val uid="user//target";private val mid="mesh//group";private val server="https://example.invalid/"
    private fun state(rights:Long=0x80000008L):ProfileState {
        val user=JSONObject().put("_id",uid).put("siteadmin",0).put("links",JSONObject().put(mid,JSONObject().put("rights",rights)))
        val mesh=JSONObject().put("_id",mid).put("links",JSONObject().put(uid,JSONObject().put("rights",rights)).put(actor,JSONObject().put("rights",0xffffffffL)))
        return ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",2),users=listOf(user),groups=listOf(mesh))
    }
    private fun packet(s:ProfileState,next:Long=0x80000010L,confirmed:Boolean=true)=profileUserMeshEditPacket(actor,server,s.users.single(),s.groups.single(),next,confirmed)
    @Test fun exactEditAndConfirmationPreserveUnknownBits() {
        val s=state();val p=packet(s)
        assertTrue(profileUserMeshEditAllowed(s,p,server))
        assertFalse(profileUserMeshEditAllowed(s,packet(s,confirmed=false),server))
        assertFalse(profileUserMeshEditAllowed(s,packet(s,16L),server))
        assertFalse(profileUserMeshEditAllowed(s,packet(s,0x80000008L),server))
        assertEquals(setOf("action","meshid","userids","meshadmin"),profileUserMeshEditWire(p).keys().asSequence().toSet())
        assertEquals(0x80000010L,profileUserMeshEditWire(p).getLong("meshadmin"))
        assertFalse(profileUserMeshGrantAllowed(s,p,server));assertFalse(profileGroupMeshGrantAllowed(s,p,server))
    }
    @Test fun initialGrantRemovalAndFullAdministratorAreNotEdits() {
        for(side in listOf("users","meshes")) {
            val s=state()
            if(side=="users") s.users.single().getJSONObject("links").remove(mid) else s.groups.single().getJSONObject("links").remove(uid)
            assertFalse(profileUserMeshEditAllowed(s,packet(s),server))
        }
        val s=state(8L)
        assertFalse(profileUserMeshEditAllowed(s,packet(s,0L),server))
        assertFalse(profileUserMeshEditAllowed(s,packet(s,0xffffffffL),server))
        val admin=state(0xffffffffL);assertFalse(profileUserMeshEditAllowed(admin,packet(admin),server))
    }
    @Test fun staleOrInconsistentLinksAndInsufficientAuthorityBlock() {
        val s=state();val p=packet(s)
        s.groups.single().getJSONObject("links").getJSONObject(uid).put("rights",16)
        assertFalse(profileUserMeshEditAllowed(s,p,server));assertFalse(profileUserMeshEditAllowed(s,packet(s),server))
        val low=state();low.groups.single().getJSONObject("links").getJSONObject(actor).put("rights",2)
        assertFalse(profileUserMeshEditAllowed(low,packet(low),server))
        val frozen=state();val before=packet(frozen)
        frozen.users.single().getJSONObject("links").getJSONObject(mid).put("rights",0x80000020L)
        frozen.groups.single().getJSONObject("links").getJSONObject(uid).put("rights",0x80000020L)
        assertFalse(profileUserMeshEditAllowed(frozen,before,server))
    }
    @Test fun scopeBindingAndIncompleteStateAreEnforced() {
        val s=state();val p=packet(s)
        assertFalse(profileUserMeshEditAllowed(s,p,"https://other.invalid/"))
        for(blocked in listOf(s.copy(ready=false),s.copy(loading=true),s.copy(busy=true),s.copy(readError=true),s.copy(detailsBusy=true),s.copy(users=emptyList()),s.copy(groups=emptyList()))) assertFalse(profileUserMeshEditAllowed(blocked,p,server))
        for(id in listOf(actor,"user/other/target","ugrp//target")) assertFalse(profileUserMeshEditAllowed(s,JSONObject(p.toString()).put("userids",JSONArray().put(id)),server))
        assertFalse(profileUserMeshEditAllowed(s,JSONObject(p.toString()).put("remove",true),server))
    }
    @Test fun ackAndTwoReadsNeedExactRightsIncludingUnknownBits() {
        val s=state(0x80000010L)
        for(ack in listOf(false,true)) for(ur in listOf(false,true)) for(mr in listOf(false,true)) assertEquals(ack && ur && mr,profileUserMeshEditReadbackReady(s,uid,mid,0x80000010L,ack,ur,mr))
        assertFalse(profileUserMeshEditReadbackReady(s.copy(readError=true),uid,mid,0x80000010L,true,true,true))
        assertFalse(profileUserMeshEditReadbackReady(s.copy(users=emptyList()),uid,mid,0x80000010L,true,true,true))
        s.groups.single().getJSONObject("links").getJSONObject(uid).put("rights",16)
        assertFalse(profileUserMeshEditReadbackReady(s,uid,mid,0x80000010L,true,true,true))
        val ack=JSONObject().put("action","addmeshuser").put("responseid","request").put("success",1).put("failed",0)
        assertTrue(profileUserMeshEditAck(ack,"request"));assertFalse(profileUserMeshEditAck(ack,"old"))
    }
}
