package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAccessAssignmentNavigationTest {
    private val gid="ugrp//group"
    private val mid="mesh//one"
    private fun state()=ProfileState(ready=true,user=JSONObject().put("_id","user//admin"),userGroups=listOf(JSONObject().put("_id",gid).put("name","Group")),groups=listOf(JSONObject().put("_id",mid).put("links",JSONObject().put(gid,JSONObject().put("rights",8)))))
    @Test fun previewUsesAnActuallyReceivedAssignmentPair() {assertEquals(gid to mid,profileAccessAssignmentPair(state(),gid,mid))}
    @Test fun absentOrMalformedAssignmentsAreNotLinks() {val s=state();s.groups.single().getJSONObject("links").put(gid,JSONObject.NULL);assertNull(profileAccessAssignmentPair(s,gid,mid));assertNull(profileAccessAssignmentPair(state().copy(groups=emptyList()),gid,mid))}
    @Test fun otherDomainsAndPendingOperationsDoNotNavigate() {assertNull(profileAccessAssignmentPair(state(),gid,"mesh/other/one"));assertNull(profileAccessAssignmentPair(state().copy(busy=true),gid,mid));assertNull(profileAccessAssignmentPair(state().copy(readError=true),gid,mid))}
    @Test fun previewDoesNotGrantRevocationAuthority() {val s=state();val pair=profileAccessAssignmentPair(s,gid,mid);assertNotNull(pair);assertFalse(profileGroupMeshReviewAllowed(s,gid,mid,"https://example.invalid/"))}
}
