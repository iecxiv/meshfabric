package iecxiv.meshfabric.ui.management
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserMeshGrantRequestTest {
    private val actor="user//admin";private val uid="user//target";private val mid="mesh//group";private val server="https://example.invalid/"
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",2),users=listOf(JSONObject().put("_id",uid).put("siteadmin",0).put("links",JSONObject())),groups=listOf(JSONObject().put("_id",mid).put("links",JSONObject().put(actor,JSONObject().put("rights",0xffffffffL)))))
    private fun packet(s:ProfileState,confirmed:Boolean=true)=profileUserMeshGrantPacket(actor,server,s.users.single(),s.groups.single(),8,confirmed)
    private fun copy(p:JSONObject)=JSONObject(p.toString())
    @Test fun exactPacketConfirmationAndWireAreRequired() {
        val s=state();val p=packet(s)
        assertTrue(profileUserMeshGrantAllowed(s,p,server))
        assertFalse(profileUserMeshGrantAllowed(s,packet(s,false),server))
        assertFalse(profileUserMeshGrantAllowed(s,p,"https://other.invalid/"))
        assertFalse(profileUserMeshGrantAllowed(s,copy(p).put("remove",true),server))
        assertFalse(profileGroupMeshGrantAllowed(s,p,server))
        assertEquals(setOf("action","meshid","userids","meshadmin"),profileUserMeshGrantWire(p).keys().asSequence().toSet())
        assertEquals(uid,profileUserMeshGrantWire(p).getJSONArray("userids").getString(0))
    }
    @Test fun eitherExistingLinkBlocksInitialGrantEvenWithFreshSnapshots() {
        for(side in listOf("users","meshes")) {
            val s=state()
            if(side=="users") s.users.single().getJSONObject("links").put(mid,JSONObject().put("rights",8)) else s.groups.single().getJSONObject("links").put(uid,JSONObject().put("rights",8))
            assertFalse(profileUserMeshGrantAllowed(s,packet(s),server))
        }
        val s=state();s.users.single().remove("links")
        assertFalse(profileUserMeshGrantAllowed(s,packet(s),server))
    }
    @Test fun staleIncompleteOrInsufficientAuthorityCannotGrant() {
        val s=state();val p=packet(s)
        for(blocked in listOf(s.copy(loading=true),s.copy(readError=true),s.copy(busy=true),s.copy(detailsBusy=true),s.copy(users=emptyList()),s.copy(groups=emptyList()))) assertFalse(profileUserMeshGrantAllowed(blocked,p,server))
        s.groups.single().getJSONObject("links").getJSONObject(actor).put("rights",2)
        assertFalse(profileUserMeshGrantAllowed(s,p,server))
        assertFalse(profileUserMeshGrantAllowed(s,packet(s),server))
    }
    @Test fun selfOtherDomainsGroupsMultipleTargetsAndInvalidRightsAreBlocked() {
        val s=state();val p=packet(s)
        for(id in listOf(actor,"user/other/target","ugrp//target")) assertFalse(profileUserMeshGrantAllowed(s,copy(p).put("userids",JSONArray().put(id)),server))
        assertFalse(profileUserMeshGrantAllowed(s,copy(p).put("userids",JSONArray().put(uid).put("user//second")),server))
        for(rights in listOf<Any>(0,2,0xffffffffL,0x80000008L,8.5,"8")) assertFalse(profileUserMeshGrantAllowed(s,copy(p).put("meshadmin",rights),server))
    }
    @Test fun ackUsesExactCountersNotHumanReadableResult() {
        val p=JSONObject().put("action","addmeshuser").put("responseid","request").put("result","Added user target").put("success",1).put("failed",0)
        assertTrue(profileUserMeshGrantAck(p,"request"))
        assertFalse(profileUserMeshGrantAck(p,"other"))
        for(bad in listOf(copy(p).put("success",0),copy(p).put("failed",1),copy(p).put("success","1"),copy(p).put("error","denied"))) assertFalse(profileUserMeshGrantAck(bad,"request"))
    }
    @Test fun exactRightsOnBothRecordsAndTwoReadsAfterAckAreMandatory() {
        val s=state()
        s.users.single().getJSONObject("links").put(mid,JSONObject().put("rights",8))
        s.groups.single().getJSONObject("links").put(uid,JSONObject().put("rights",8))
        for(ack in listOf(false,true)) for(ur in listOf(false,true)) for(mr in listOf(false,true)) assertEquals(ack && ur && mr,profileUserMeshGrantReadbackReady(s,uid,mid,8,ack,ur,mr))
        for(blocked in listOf(s.copy(ready=false),s.copy(loading=true),s.copy(readError=true),s.copy(users=emptyList()),s.copy(groups=emptyList()),s.copy(users=s.users+s.users))) assertFalse(profileUserMeshGrantReadbackReady(blocked,uid,mid,8,true,true,true))
        s.groups.single().getJSONObject("links").getJSONObject(uid).put("rights",16)
        assertFalse(profileUserMeshGrantReadbackReady(s,uid,mid,8,true,true,true))
        s.groups.single().getJSONObject("links").remove(uid)
        assertFalse(profileUserMeshGrantReadbackReady(s,uid,mid,8,true,true,true))
    }
}
