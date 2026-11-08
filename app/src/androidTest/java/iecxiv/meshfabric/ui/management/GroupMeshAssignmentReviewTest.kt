package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupMeshAssignmentReviewTest {
    private val gid="ugrp//group"
    private val mid="mesh//one"
    private val actor="user//admin"
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",256L),listedUserGroups=listOf(JSONObject().put("_id",gid).put("links",JSONObject().put(mid,JSONObject().put("rights",8)))),groups=listOf(JSONObject().put("_id",mid).put("links",JSONObject().put(gid,JSONObject().put("rights",8)).put(actor,JSONObject().put("rights",2)))),userGroupPhase="ready")
    @Test fun previewChecksBothLinksWithoutMutatingRecords() {val s=state();val before=s.groups.single().toString();assertTrue(profileGroupMeshReviewAllowed(s,gid,mid,"https://example.invalid/"));assertEquals(before,s.groups.single().toString())}
    @Test fun missingRightsAreUnknownRatherThanZero() {assertNull(profileGroupMeshReportedRights(null,gid));assertNull(profileGroupMeshReportedRights(JSONObject().put("links",JSONObject()),gid))}
    @Test fun mismatchAndRefreshPreventEligibility() {val s=state();assertFalse(profileGroupMeshReviewAllowed(s.copy(loading=true),gid,mid,"https://example.invalid/"));s.groups.single().getJSONObject("links").getJSONObject(gid).put("rights",16);assertFalse(profileGroupMeshReviewAllowed(s,gid,mid,"https://example.invalid/"))}
    @Test fun signedAndUnknownBitsRemainVisible() {val record=JSONObject().put("links",JSONObject().put(gid,JSONObject().put("rights",0x80000008L)));assertEquals(0x80000008L,profileGroupMeshReportedRights(record,gid));record.getJSONObject("links").getJSONObject(gid).put("rights",-1);assertEquals(0xffffffffL,profileGroupMeshReportedRights(record,gid))}
}
