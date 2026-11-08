package iecxiv.meshfabric.ui.management
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class GroupMeshEditRequestTest {
    private val actor="user//admin";private val gid="ugrp//group";private val mid="mesh//one";private val server="https://example.invalid/"
    private fun state(rights:Long=0x80000008L):ProfileState {
        val subject=JSONObject().put("_id",gid).put("links",JSONObject().put(mid,JSONObject().put("rights",rights)).put("user//member",JSONObject().put("rights",1)))
        val mesh=JSONObject().put("_id",mid).put("links",JSONObject().put(gid,JSONObject().put("rights",rights)).put(actor,JSONObject().put("rights",0xffffffffL)).put("user//direct",JSONObject().put("rights",8)))
        return ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",256L),listedUserGroups=listOf(subject),groups=listOf(mesh),userGroupPhase="ready")
    }
    private fun packet(s:ProfileState,next:Long=0x80000010L,confirmed:Boolean=true)=profileGroupMeshEditPacket(actor,server,s.listedUserGroups!!.single(),s.groups.single(),next,confirmed)
    @Test fun isolatedEditPreservesUnknownBitsWithoutTouchingOtherAssignments() {
        val s=state();val p=packet(s)
        val groupBefore=s.listedUserGroups!!.single().toString();val meshBefore=s.groups.single().toString()
        assertTrue(profileGroupMeshEditAllowed(s,p,server))
        assertFalse(profileGroupMeshEditAllowed(s,packet(s,confirmed=false),server))
        assertFalse(profileGroupMeshEditAllowed(s,packet(s,16L),server))
        assertFalse(profileGroupMeshEditAllowed(s,packet(s,0x80000008L),server))
        val wire=profileGroupMeshEditWire(p)
        assertEquals(setOf("action","meshid","userids","meshadmin"),wire.keys().asSequence().toSet())
        assertEquals("addmeshuser",wire.getString("action"));assertEquals(gid,wire.getJSONArray("userids").getString(0));assertEquals(0x80000010L,wire.getLong("meshadmin"))
        assertEquals(groupBefore,s.listedUserGroups!!.single().toString());assertEquals(meshBefore,s.groups.single().toString())
        assertFalse(profileGroupMeshGrantAllowed(s,p,server));assertFalse(profileUserMeshEditAllowed(s,p,server));assertFalse(profileUserMeshGrantAllowed(s,p,server))
    }
    @Test fun bothExistingLinksMustBeConsistentAndUnchanged() {
        for(side in listOf("group","mesh")) {
            val s=state();val frozen=packet(s)
            if(side=="group") s.listedUserGroups!!.single().getJSONObject("links").remove(mid) else s.groups.single().getJSONObject("links").remove(gid)
            assertFalse(profileGroupMeshEditAllowed(s,frozen,server));assertFalse(profileGroupMeshEditAllowed(s,packet(s),server))
        }
        val mismatch=state();mismatch.groups.single().getJSONObject("links").getJSONObject(gid).put("rights",16)
        assertFalse(profileGroupMeshEditAllowed(mismatch,packet(mismatch),server))
        val changed=state();val frozen=packet(changed)
        changed.listedUserGroups!!.single().getJSONObject("links").getJSONObject(mid).put("rights",0x80000020L)
        changed.groups.single().getJSONObject("links").getJSONObject(gid).put("rights",0x80000020L)
        assertFalse(profileGroupMeshEditAllowed(changed,frozen,server))
    }
    @Test fun directFullAuthorityAndGroupAdministrationRoleAreRequired() {
        val s=state()
        for(rights in listOf(0L,2L,8L)) {
            s.groups.single().getJSONObject("links").getJSONObject(actor).put("rights",rights)
            assertFalse(profileGroupMeshEditAllowed(s,packet(s),server))
        }
        s.groups.single().getJSONObject("links").remove(actor)
        assertFalse(profileGroupMeshEditAllowed(s,packet(s),server))
        val noRole=state().copy(user=JSONObject().put("_id",actor).put("siteadmin",2))
        assertFalse(profileGroupMeshEditAllowed(noRole,packet(noRole),server))
    }
    @Test fun scopeSingleGroupTargetAndExactPacketWhitelistAreRequired() {
        val s=state();val p=packet(s)
        assertFalse(profileGroupMeshEditAllowed(s,p,"https://other.invalid/"))
        assertFalse(profileGroupMeshEditAllowed(s,JSONObject(p.toString()).put("_groupEditActor","user//other"),server))
        for(id in listOf("user//target","ugrp/other/group","ugrp//missing")) assertFalse(profileGroupMeshEditAllowed(s,JSONObject(p.toString()).put("userids",JSONArray().put(id)),server))
        assertFalse(profileGroupMeshEditAllowed(s,JSONObject(p.toString()).put("userids",JSONArray().put(gid).put("ugrp//other")),server))
        assertFalse(profileGroupMeshEditAllowed(s,JSONObject(p.toString()).put("meshid","mesh/other/one"),server))
        assertFalse(profileGroupMeshEditAllowed(s,JSONObject(p.toString()).put("userid","user//target"),server))
    }
    @Test fun incompleteDirectoriesNoOpZeroAndFullAdministratorAreBlocked() {
        val s=state();val p=packet(s)
        for(blocked in listOf(s.copy(ready=false),s.copy(loading=true),s.copy(busy=true),s.copy(readError=true),s.copy(detailsBusy=true),s.copy(userGroupPhase="loading"),s.copy(userGroupPhase="failed"),s.copy(listedUserGroups=null),s.copy(listedUserGroups=emptyList()),s.copy(groups=emptyList()))) assertFalse(profileGroupMeshEditAllowed(blocked,p,server))
        val simple=state(8L)
        for(next in listOf(0L,8L,0xffffffffL,0x80000010L)) assertFalse(profileGroupMeshEditAllowed(simple,packet(simple,next),server))
        val admin=state(0xffffffffL);assertFalse(profileGroupMeshEditAllowed(admin,packet(admin),server))
    }
    @Test fun acknowledgementAndTwoReadsRequireTheExactMaskIncludingUnknownBits() {
        val s=state(0x80000010L)
        for(ack in listOf(false,true)) for(gr in listOf(false,true)) for(mr in listOf(false,true)) assertEquals(ack && gr && mr,profileGroupMeshEditReadbackReady(s,gid,mid,0x80000010L,ack,gr,mr))
        for(blocked in listOf(s.copy(loading=true),s.copy(readError=true),s.copy(userGroupPhase="loading"),s.copy(listedUserGroups=null))) assertFalse(profileGroupMeshEditReadbackReady(blocked,gid,mid,0x80000010L,true,true,true))
        s.groups.single().getJSONObject("links").getJSONObject(gid).put("rights",16)
        assertFalse(profileGroupMeshEditReadbackReady(s,gid,mid,0x80000010L,true,true,true))
        val ack=JSONObject().put("action","addmeshuser").put("responseid","request").put("result","Updated group rights").put("success",1).put("failed",0)
        assertTrue(profileGroupMeshEditAck(ack,"request"));assertFalse(profileGroupMeshEditAck(ack,"other"))
        assertFalse(profileGroupMeshEditAck(JSONObject(ack.toString()).put("failed",1),"request"))
        assertFalse(profileGroupMeshEditAck(JSONObject().put("action","addmeshuser").put("responseid","request").put("result","ok"),"request"))
    }
}
