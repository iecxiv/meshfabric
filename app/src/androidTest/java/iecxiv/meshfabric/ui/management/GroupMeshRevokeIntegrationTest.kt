package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupMeshRevokeIntegrationTest {
    private val gid="ugrp//group"
    private val mid="mesh//one"
    private val actor="user//admin"
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",256L),listedUserGroups=listOf(JSONObject().put("_id",gid).put("links",JSONObject())),groups=listOf(JSONObject().put("_id",mid).put("links",JSONObject())),userGroupPhase="ready")
    @Test fun bothReadsAndAcknowledgementAreMandatory() {val s=state();assertTrue(profileGroupMeshReadbackReady(s,gid,mid,true,true,true));assertFalse(profileGroupMeshReadbackReady(s,gid,mid,false,true,true));assertFalse(profileGroupMeshReadbackReady(s,gid,mid,true,false,true));assertFalse(profileGroupMeshReadbackReady(s,gid,mid,true,true,false))}
    @Test fun missingRecordsNeverProveSuccess() {val s=state();assertFalse(profileGroupMeshReadbackReady(s.copy(groups=emptyList()),gid,mid,true,true,true));assertFalse(profileGroupMeshReadbackReady(s.copy(listedUserGroups=emptyList()),gid,mid,true,true,true))}
    @Test fun eitherRemainingLinkPreventsSuccess() {val s=state();s.groups.single().getJSONObject("links").put(gid,JSONObject().put("rights",8));assertFalse(profileGroupMeshReadbackReady(s,gid,mid,true,true,true))}
    @Test fun frozenPacketRequiresSeparateConfirmationAndStaysStale() {val s=state();val g=s.listedUserGroups!!.single();val m=s.groups.single();g.getJSONObject("links").put(mid,JSONObject().put("rights",8));m.getJSONObject("links").put(gid,JSONObject().put("rights",8)).put(actor,JSONObject().put("rights",2));val p=profileGroupMeshRevokePacket(actor,"https://example.invalid/",g,m);assertFalse(profileGroupMeshRevokeAllowed(s,p,"https://example.invalid/"));p.put("_meshConfirmed",true);assertTrue(profileGroupMeshRevokeAllowed(s,p,"https://example.invalid/"));m.getJSONObject("links").getJSONObject(gid).put("rights",16);assertFalse(profileGroupMeshRevokeAllowed(s,p,"https://example.invalid/"))}
}
