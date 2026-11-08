package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupMeshGrantRequestTest {
    private val gid="ugrp//group"
    private val mid="mesh//one"
    private val actor="user//admin"
    private val server="https://example.invalid/"
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",256L),listedUserGroups=listOf(JSONObject().put("_id",gid).put("links",JSONObject())),groups=listOf(JSONObject().put("_id",mid).put("links",JSONObject().put(actor,JSONObject().put("rights",0xffffffffL)))),userGroupPhase="ready")
    private fun packet(s:ProfileState)=JSONObject().put("action","addmeshuser").put("meshid",mid).put("userids",JSONArray().put(gid)).put("meshadmin",264).put("_grantActor",actor).put("_grantServer",server).put("_grantGroupBefore",profileUserGroupDeleteSnapshot(s.listedUserGroups!!.single())).put("_grantDeviceBefore",profileUserGroupDeleteSnapshot(s.groups.single())).put("_grantConfirmed",true)
    @Test fun newAssignmentUsesAnExactGroupIdentifier() {val s=state();val p=packet(s);assertTrue(profileGroupMeshGrantAllowed(s,p,server));assertEquals(setOf("action","meshid","userids","meshadmin"),profileGroupMeshGrantWire(p).keys().asSequence().toSet());assertEquals(gid,profileGroupMeshGrantWire(p).getJSONArray("userids").getString(0))}
    @Test fun eitherExistingSideBlocksOverwritingUnknownRights() {val a=state();a.listedUserGroups!!.single().getJSONObject("links").put(mid,JSONObject().put("rights",0x80000008L));assertFalse(profileGroupMeshGrantAllowed(a,packet(a),server));val b=state();b.groups.single().getJSONObject("links").put(gid,JSONObject().put("rights",8));assertFalse(profileGroupMeshGrantAllowed(b,packet(b),server))}
    @Test fun directFullAuthorityIsRequired() {val s=state();s.groups.single().getJSONObject("links").getJSONObject(actor).put("rights",2);assertFalse(profileGroupMeshGrantAllowed(s,packet(s),server));s.groups.single().getJSONObject("links").remove(actor);assertFalse(profileGroupMeshGrantAllowed(s,packet(s),server))}
    @Test fun confirmationSingleTargetAndExactWhitelistAreRequired() {val s=state();val p=packet(s);assertFalse(profileGroupMeshGrantAllowed(s,JSONObject(p.toString()).put("_grantConfirmed",false),server));assertFalse(profileGroupMeshGrantAllowed(s,JSONObject(p.toString()).put("userid","user//other"),server));assertFalse(profileGroupMeshGrantAllowed(s,JSONObject(p.toString()).put("userids",JSONArray().put(gid).put("ugrp//other")),server))}
    @Test fun successTextIsNotRequiredToBeOkButCountersAreMandatory() {val p=JSONObject().put("action","addmeshuser").put("responseid","request").put("result","Added user group").put("success",1).put("failed",0);assertTrue(profileGroupMeshGrantAck(p,"request"));assertFalse(profileGroupMeshGrantAck(p,"other"));assertFalse(profileGroupMeshGrantAck(JSONObject().put("action","addmeshuser").put("responseid","request").put("result","ok"),"request"));p.put("failed",1);assertFalse(profileGroupMeshGrantAck(p,"request"))}
    @Test fun bothRecordsMustShowTheRequestedRightsAfterAcknowledgement() {val g=JSONObject().put("_id",gid).put("links",JSONObject().put(mid,JSONObject().put("rights",264)));val m=JSONObject().put("_id",mid).put("links",JSONObject().put(gid,JSONObject().put("rights",264)));assertTrue(profileGroupMeshGrantObserved(g,m,gid,mid,264,true));assertFalse(profileGroupMeshGrantObserved(g,m,gid,mid,264,false));assertFalse(profileGroupMeshGrantObserved(null,m,gid,mid,264,true));m.getJSONObject("links").getJSONObject(gid).put("rights",8);assertFalse(profileGroupMeshGrantObserved(g,m,gid,mid,264,true))}

    @Test fun grantReadbackRequiresAckAndBothFreshReads() {
        val s=state()
        s.listedUserGroups!!.single().getJSONObject("links").put(mid,JSONObject().put("rights",264))
        s.groups.single().getJSONObject("links").put(gid,JSONObject().put("rights",264))
        for(ack in listOf(false,true)) for(groupRead in listOf(false,true)) for(deviceRead in listOf(false,true)) {
            assertEquals(ack && groupRead && deviceRead,profileGroupMeshGrantReadbackReady(s,gid,mid,264,ack,groupRead,deviceRead))
        }
        assertFalse(profileGroupMeshGrantReadbackReady(s,gid,mid,8,true,true,true))
        assertFalse(profileGroupMeshGrantReadbackReady(s.copy(listedUserGroups=null),gid,mid,264,true,true,true))
    }
    @Test fun grantReplyClassificationDoesNotUseGenericOkText() {
        val reply=JSONObject().put("action","addmeshuser").put("responseid","request").put("result","Added user group").put("success",1).put("failed",0)
        assertTrue(profileGroupMeshGrantAck(reply,"request"))
        assertFalse(profileGroupMeshGrantReplyRejected(reply))
        assertFalse(profileGroupMeshGrantAck(reply,"other"))
        assertFalse(profileGroupMeshGrantReplyRejected(JSONObject().put("result","ok")))
        assertFalse(profileGroupMeshGrantAck(JSONObject().put("action","addmeshuser").put("responseid","request").put("result","ok"),"request"))
        assertTrue(profileGroupMeshGrantReplyRejected(JSONObject(reply.toString()).put("failed",1)))
        assertTrue(profileGroupMeshGrantReplyRejected(JSONObject(reply.toString()).put("error","denied")))
        assertFalse(profileGroupMeshGrantAck(JSONObject(reply.toString()).put("error","denied"),"request"))
    }

    @Test fun uiDraftRequiresExplicitConfirmationAndKeepsItsSnapshots() {
        val s=state()
        val draft=requireNotNull(profileGroupMeshGrantDraft(s,gid,mid,264,actor,server))
        assertEquals(false,draft.getBoolean("_grantConfirmed"))
        assertFalse(profileGroupMeshGrantAllowed(s,draft,server))
        val confirmed=JSONObject(draft.toString()).put("_grantConfirmed",true)
        assertTrue(profileGroupMeshGrantAllowed(s,confirmed,server))
        assertFalse(draft.getBoolean("_grantConfirmed"))
        assertFalse(profileGroupMeshGrantAllowed(s,confirmed,"https://other.invalid/"))
        s.groups.single().getJSONObject("links").getJSONObject(actor).put("rights",8)
        assertFalse(profileGroupMeshGrantAllowed(s,confirmed,server))
    }
    @Test fun uiDraftRejectsInvalidContextAndExistingAssignments() {
        val s=state()
        assertNull(profileGroupMeshGrantDraft(s,gid,mid,264,"user//other",server))
        assertNull(profileGroupMeshGrantDraft(s,gid,mid,264,actor,""))
        assertNull(profileGroupMeshGrantDraft(s.copy(busy=true),gid,mid,264,actor,server))
        assertNull(profileGroupMeshGrantDraft(s.copy(userGroupPhase="loading"),gid,mid,264,actor,server))
        assertNull(profileGroupMeshGrantDraft(s.copy(listedUserGroups=null),gid,mid,264,actor,server))
        assertNull(profileGroupMeshGrantDraft(s,gid,"mesh//missing",264,actor,server))
        val frozen=requireNotNull(profileGroupMeshGrantDraft(s,gid,mid,264,actor,server))
        s.listedUserGroups!!.single().getJSONObject("links").put(mid,JSONObject().put("rights",8))
        assertNull(profileGroupMeshGrantDraft(s,gid,mid,264,actor,server))
        assertFalse(profileGroupMeshGrantAllowed(s,JSONObject(frozen.toString()).put("_grantConfirmed",true),server))
    }
}
