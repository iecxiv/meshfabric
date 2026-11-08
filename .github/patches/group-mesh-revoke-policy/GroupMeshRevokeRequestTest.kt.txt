package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupMeshRevokeRequestTest {
    private val gid="ugrp//group"
    private val mid="mesh//one"
    private val actor="user//admin"
    private val server="https://example.invalid/"
    private fun state(grant:Long=8,direct:Long=2)=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",256L),listedUserGroups=listOf(JSONObject().put("_id",gid).put("links",JSONObject().put(mid,JSONObject().put("rights",grant)))),groups=listOf(JSONObject().put("_id",mid).put("links",JSONObject().put(gid,JSONObject().put("rights",grant)).put(actor,JSONObject().put("rights",direct)))),userGroupPhase="ready")
    private fun packet(s:ProfileState)=JSONObject().put("action","removemeshuser").put("meshid",mid).put("userid",gid).put("_meshActor",actor).put("_meshServer",server).put("_meshGroupBefore",profileUserGroupDeleteSnapshot(s.listedUserGroups!!.single())).put("_meshDeviceBefore",profileUserGroupDeleteSnapshot(s.groups.single())).put("_meshConfirmed",true)
    @Test fun onlySelectedPairIsSent() {val s=state(0x80000008L);val p=packet(s);assertTrue(profileGroupMeshRevokeAllowed(s,p,server));assertEquals(setOf("action","meshid","userid"),profileGroupMeshRevokeWire(p).keys().asSequence().toSet());assertEquals(0x80000008L,s.listedUserGroups!!.single().getJSONObject("links").getJSONObject(mid).getLong("rights"))}
    @Test fun inconsistentRightsAndUnknownDirectAuthorityAreRejected() {val s=state();s.groups.single().getJSONObject("links").getJSONObject(gid).put("rights",16);assertFalse(profileGroupMeshRevokeAllowed(s,packet(s),server));val noDirect=state();noDirect.groups.single().getJSONObject("links").remove(actor);assertFalse(profileGroupMeshRevokeAllowed(noDirect,packet(noDirect),server))}
    @Test fun fullAdminGrantIsProtected() {val s=state(0xffffffffL,2);assertFalse(profileGroupMeshRevokeAllowed(s,packet(s),server));val full=state(0xffffffffL,0xffffffffL);assertTrue(profileGroupMeshRevokeAllowed(full,packet(full),server))}
    @Test fun staleDataAndMissingConfirmationAreRejected() {val s=state();val p=packet(s);assertFalse(profileGroupMeshRevokeAllowed(s,JSONObject(p.toString()).put("_meshConfirmed",false),server));s.groups.single().put("name","Changed");assertFalse(profileGroupMeshRevokeAllowed(s,p,server))}
    @Test fun acknowledgementMustBeCorrelated() {val p=JSONObject().put("action","removemeshuser").put("responseid","request").put("result","ok");assertTrue(profileGroupMeshRevokeAck(p,"request"));assertFalse(profileGroupMeshRevokeAck(p,"other"));assertFalse(profileGroupMeshRevokeAck(p.put("result","User not in group"),"request"))}
    @Test fun bothPresentRecordsMustExplicitlyLackTheLinks() {val g=JSONObject().put("_id",gid).put("links",JSONObject());val m=JSONObject().put("_id",mid).put("links",JSONObject());assertTrue(profileGroupMeshRevokeObserved(g,m,gid,mid,true));assertFalse(profileGroupMeshRevokeObserved(g,m,gid,mid,false));assertFalse(profileGroupMeshRevokeObserved(null,m,gid,mid,true));m.remove("links");assertFalse(profileGroupMeshRevokeObserved(g,m,gid,mid,true))}
}
