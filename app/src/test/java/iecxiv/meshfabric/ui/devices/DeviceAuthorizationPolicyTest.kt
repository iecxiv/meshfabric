package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.ui.management.ProfileState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceAuthorizationPolicyTest {
    private val actor="user//admin"
    private val nodeId="node//a"
    private val meshId="mesh//a"
    private fun state():ProfileState {
        val admin=JSONObject().put("_id",actor).put("name","Admin").put("siteadmin",2)
        val user=JSONObject().put("_id","user//alice").put("name","Alice").put("siteadmin",0)
        val node=JSONObject().put("_id",nodeId).put("name","Equipo A").put("meshid",meshId).put("links",JSONObject().put(actor,JSONObject().put("rights",2)))
        val mesh=JSONObject().put("_id",meshId).put("name","Grupo A").put("links",JSONObject())
        return ProfileState(ready=true,user=admin,users=listOf(admin,user),nodes=listOf(node),groups=listOf(mesh),userGroups=emptyList())
    }
    @Test fun scopeContainsOnlyTheOpenedDevice() {
        val s=state();val other=JSONObject(s.nodes!!.single().toString()).put("_id","node//b")
        val scoped=generalAuthorizationScope(s.copy(nodes=s.nodes+other),actor,nodeId,meshId,true)
        assertEquals(listOf(nodeId),scoped?.nodes?.map {it.optString("_id")})
    }
    @Test fun rejectsMissingOrDuplicateDevice() {
        val s=state()
        assertNull(generalAuthorizationScope(s.copy(nodes=emptyList()),actor,nodeId,meshId,true))
        assertNull(generalAuthorizationScope(s.copy(nodes=s.nodes!!+s.nodes),actor,nodeId,meshId,true))
    }
    @Test fun rejectsMovedDeviceAndForeignDomain() {
        val s=state();s.nodes!!.single().put("meshid","mesh//b")
        assertNull(generalAuthorizationScope(s,actor,nodeId,meshId,true))
        assertNull(generalAuthorizationScope(state(),"user/other/admin",nodeId,meshId,true))
    }
    @Test fun disconnectedLoadingOrIncompleteReadsHaveNoScope() {
        val s=state()
        assertNull(generalAuthorizationScope(s,actor,nodeId,meshId,false))
        assertNull(generalAuthorizationScope(s.copy(loading=true),actor,nodeId,meshId,true))
        assertNull(generalAuthorizationScope(s.copy(readError=true),actor,nodeId,meshId,true))
        assertNull(generalAuthorizationScope(s.copy(detailsBusy=true),actor,nodeId,meshId,true))
    }
    @Test fun directAndInheritedLinksStaySeparate() {
        val s=state();val uid="user//alice"
        s.nodes!!.single().getJSONObject("links").put(uid,JSONObject().put("rights",8))
        s.groups.single().getJSONObject("links").put(uid,JSONObject().put("rights",16))
        val rows=generalAuthorizationRows(s).filter {it.subject==uid}
        assertEquals(2,rows.size);assertEquals(listOf(false,true),rows.map {it.inherited})
        assertEquals(listOf(8L,16L),rows.map {it.rights})
    }
    @Test fun malformedRightsAreNotPresentedAsZero() {
        val s=state();s.nodes!!.single().getJSONObject("links").put("user//alice",JSONObject().put("rights","8"))
        assertNull(generalAuthorizationRows(s).single {it.subject=="user//alice"}.rights)
    }
    @Test fun existingDirectAssignmentIsNotAnAddCandidate() {
        val s=state();val uid="user//alice"
        assertEquals(listOf(uid),generalAuthorizationCandidates(s,true).map {it.optString("_id")})
        s.nodes!!.single().getJSONObject("links").put(uid,JSONObject().put("rights",8))
        assertTrue(generalAuthorizationCandidates(s,true).isEmpty())
    }
    @Test fun grantRequiresDirectoryRoleAndDeviceAuthority() {
        val s=state();s.user!!.put("siteadmin",0)
        assertTrue(generalAuthorizationCandidates(s,true).isEmpty())
        s.user.put("siteadmin",2);s.nodes!!.single().put("links",JSONObject())
        assertTrue(generalAuthorizationCandidates(s,true).isEmpty())
    }
}
