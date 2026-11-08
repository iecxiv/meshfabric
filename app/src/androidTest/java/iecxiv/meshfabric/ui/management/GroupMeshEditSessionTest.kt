package iecxiv.meshfabric.ui.management
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class GroupMeshEditSessionTest {
    private val actor="user//admin";private val gid="ugrp//group";private val mid="mesh//one"
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private fun set(target:Any,name:String,value:Any?) {field(target,name).set(target,value)}
    private fun get(target:Any,name:String)=field(target,name).get(target)
    private fun group(rights:Long,linked:Boolean=true)=JSONObject().put("_id",gid).put("links",JSONObject().put("user//member",JSONObject().put("rights",1)).apply {if(linked) put(mid,JSONObject().put("rights",rights))})
    private fun mesh(rights:Long,linked:Boolean=true)=JSONObject().put("_id",mid).put("links",JSONObject().put(actor,JSONObject().put("rights",0xffffffffL)).put("user//direct",JSONObject().put("rights",8)).apply {if(linked) put(gid,JSONObject().put("rights",rights))})
    private class MemorySocket:WebSocket {
        val sent=mutableListOf<JSONObject>()
        override fun request():Request=Request.Builder().url("https://example.invalid/").build()
        override fun queueSize():Long=0L
        override fun send(text:String):Boolean {sent.add(JSONObject(text));return true}
        override fun send(bytes:ByteString):Boolean=false
        override fun close(code:Int,reason:String?):Boolean=true
        override fun cancel() {}
    }
    private inner class Harness(val role:Long=256L):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client);val wire=MemorySocket()
        val oldTags:Map<String,String>
        lateinit var operation:JSONObject
        init {
            set(client,"serverUrl","https://example.invalid/");set(client,"authCookie","offline-test")
            @Suppress("UNCHECKED_CAST")
            val connected=get(client,"_connectionState") as MutableStateFlow<Boolean>
            connected.value=true
            set(session,"binding",Triple(client.serverUrl,client.authCookie,false));set(session,"socket",wire)
            session.state.value=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",role),listedUserGroups=listOf(group(0x80000008L)),groups=listOf(mesh(0x80000008L)),userGroupPhase="ready")
            assertNull(get(client,"webSocket"))
            session.refresh();session.loadUserGroupDirectory();oldTags=tags()
            reply("meshes",oldTags.getValue("meshes"),0x80000008L)
            oldTags["users"]?.let {reply("users",it)}
            reply("usergroups",oldTags.getValue("usergroups"),0x80000008L)
            assertTrue(session.state.value.canWrite)
        }
        fun receive(p:JSONObject) {synchronized(session) {session.javaClass.getDeclaredMethod("receive",JSONObject::class.java).apply {isAccessible=true}.invoke(session,p)}}
        fun tags()=wire.sent.filter {it.optString("action") in setOf("users","meshes","usergroups")}.associate {it.getString("action") to it.getString("tag")}
        fun begin() {
            val s=session.state.value
            val p=profileGroupMeshEditPacket(actor,session.broadcastServerLabel(),s.listedUserGroups!!.single(),s.groups.single(),0x80000010L,true)
            assertFalse(session.submit(p));assertFalse(session.submitUserMeshEdit(p));assertFalse(session.submitUserMeshGrant(p))
            assertTrue(session.submitGroupMeshEdit(p));operation=wire.sent.last()
            assertEquals(setOf("action","userids","meshid","meshadmin","responseid"),operation.keys().asSequence().toSet())
            assertEquals(gid,operation.getJSONArray("userids").getString(0));assertEquals(0x80000010L,operation.getLong("meshadmin"))
        }
        fun ack(id:String=operation.getString("responseid"))=receive(JSONObject().put("action","addmeshuser").put("responseid",id).put("result","Updated group rights").put("success",1).put("failed",0))
        fun reply(action:String,tag:String?,rights:Long=0x80000010L,linked:Boolean=true) {
            val data=JSONArray()
            if(action!="users") data.put(if(action=="usergroups") group(rights,linked) else mesh(rights,linked))
            val p=JSONObject().put("action",action).put(if(action=="usergroups") "ugroups" else action,data)
            if(tag!=null) p.put("tag",tag)
            receive(p)
        }
        fun unobserved() {val s=session.state.value;assertNotEquals("groupMeshEditedObserved",s.notice);assertEquals("",s.editedGroup);assertEquals("",s.grantedGroup);assertEquals("",s.revokedGroup);assertEquals("",s.editedDirectUser)}
        fun observed() {val s=session.state.value;assertEquals("groupMeshEditedObserved",s.notice);assertEquals(gid,s.editedGroup);assertEquals(mid,s.editedDeviceGroup);assertFalse(s.busy);assertEquals(1,wire.sent.count {it.optString("action")=="addmeshuser"})}
        override fun close() {session.close();assertNull(get(client,"webSocket"));(get(client,"scope") as CoroutineScope).cancel()}
    }
    @Test fun eitherReadOrderNeedsAcknowledgementAndExactBilateralRights() {for(order in listOf(listOf("meshes","usergroups"),listOf("usergroups","meshes"))) Harness().use {h->
        h.begin();h.reply("meshes",h.oldTags.getValue("meshes"));h.reply("usergroups",h.oldTags.getValue("usergroups"));h.unobserved()
        h.ack();val tags=h.tags();h.reply(order[0],tags.getValue(order[0]));h.unobserved();assertTrue(h.session.state.value.busy)
        h.reply(order[1],tags.getValue(order[1]));h.observed()
    }}
    @Test fun manageUsersReadCanFinishLastWithoutLosingObservation() {Harness(258L).use {h->
        h.begin();h.ack();val tags=h.tags()
        h.reply("meshes",tags.getValue("meshes"));h.reply("usergroups",tags.getValue("usergroups"));h.unobserved()
        h.reply("users",tags.getValue("users"));h.observed()
    }}
    @Test fun wrongAckOldUntaggedAndDuplicateRepliesCannotConfirm() {Harness().use {h->
        h.begin();val count=h.wire.sent.size;h.ack("wrong");assertEquals(count,h.wire.sent.size);h.unobserved()
        h.ack();val tags=h.tags();assertNotEquals(h.oldTags,tags)
        h.reply("usergroups",h.oldTags.getValue("usergroups"));h.reply("meshes",null)
        h.receive(JSONObject().put("action","meshes").put("responseid",tags.getValue("meshes")).put("meshes",JSONArray().put(mesh(0x80000010L))))
        h.unobserved();h.reply("meshes",tags.getValue("meshes"));h.reply("meshes",tags.getValue("meshes"));h.unobserved()
        h.reply("usergroups",tags.getValue("usergroups"));h.observed()
    }}
    @Test fun missingOrWrongRightsIncludingUnknownBitsCannotConfirm() {for(side in listOf("meshes","usergroups")) for(wrong in listOf(16L,0x80000008L,-1L)) Harness().use {h->
        h.begin();h.ack();val tags=h.tags()
        for(action in listOf("meshes","usergroups")) h.reply(action,tags.getValue(action),if(action==side && wrong>=0L) wrong else 0x80000010L,!(action==side && wrong<0L))
        h.unobserved();assertTrue(h.session.state.value.busy)
    }}
    @Test fun mutableStateCannotReplaceTheCorrelatedGroupSnapshot() {Harness().use {h->
        h.begin();h.ack();val tags=h.tags();h.reply("usergroups",tags.getValue("usergroups"),0x80000008L)
        synchronized(h.session) {h.session.state.value=h.session.state.value.copy(listedUserGroups=listOf(group(0x80000010L)))}
        h.reply("meshes",tags.getValue("meshes"));h.unobserved();assertTrue(h.session.state.value.busy)
    }}
    @Test fun identityRefreshOrDuplicateAckStartsANewRound() {for(identity in listOf(false,true)) Harness().use {h->
        h.begin();h.ack();val first=h.tags();h.reply("usergroups",first.getValue("usergroups"))
        if(identity) h.receive(JSONObject().put("action","userinfo").put("userinfo",JSONObject().put("_id",actor).put("siteadmin",256))) else h.ack()
        val latest=h.tags();assertNotEquals(first,latest)
        h.reply("meshes",first.getValue("meshes"));h.reply("meshes",latest.getValue("meshes"));h.unobserved()
        h.reply("usergroups",latest.getValue("usergroups"));h.observed()
    }}
    @Test fun directoryAndMeshDenialsAreNeverSuccessfulReads() {for(action in listOf("meshes","usergroups")) Harness().use {h->
        h.begin();h.ack();val tags=h.tags()
        h.receive(JSONObject().put("action",action).put("tag",tags.getValue(action)).put("responseid",tags.getValue(action)).put("result","Access denied"))
        val other=if(action=="meshes") "usergroups" else "meshes";h.reply(other,tags.getValue(other))
        h.unobserved();assertTrue(h.session.state.value.busy)
    }}
    @Test fun submissionRevalidatesFrozenStateAndBinding() {Harness().use {h->
        val s=h.session.state.value;val p=profileGroupMeshEditPacket(actor,h.session.broadcastServerLabel(),s.listedUserGroups!!.single(),s.groups.single(),0x80000010L,true)
        s.listedUserGroups!!.single().getJSONObject("links").getJSONObject(mid).put("rights",0x80000020L)
        s.groups.single().getJSONObject("links").getJSONObject(gid).put("rights",0x80000020L)
        assertFalse(h.session.submitGroupMeshEdit(p))
        set(h.client,"serverUrl","https://other.invalid/");assertFalse(h.session.submitGroupMeshEdit(p))
        assertTrue(h.wire.sent.none {it.optString("action")=="addmeshuser"})
    }}
    @Test fun rejectionAndRoleLossNeverRetry() {for(roleLoss in listOf(false,true)) Harness().use {h->
        h.begin();val count=h.wire.sent.size
        if(roleLoss) h.receive(JSONObject().put("action","userinfo").put("userinfo",JSONObject().put("_id",actor).put("siteadmin",2)))
        else h.receive(JSONObject().put("action","addmeshuser").put("responseid",h.operation.getString("responseid")).put("failed",1).put("success",0))
        assertEquals(if(roleLoss) "unknown" else "rejected",h.session.state.value.notice);h.unobserved();assertFalse(h.session.state.value.busy)
        assertEquals(count,h.wire.sent.size)
    }}
    @Test fun disconnectIgnoresLateRepliesAndNeverRetries() {Harness().use {h->
        h.begin();h.ack();val tags=h.tags();val count=h.wire.sent.size;h.session.stop()
        h.reply("meshes",tags.getValue("meshes"));h.reply("usergroups",tags.getValue("usergroups"))
        assertEquals("unknown",h.session.state.value.notice);h.unobserved();assertEquals(count,h.wire.sent.size)
    }}
    @Test fun realOperationTimeoutLeavesOutcomeUnknownWithoutRetry() {Harness().use {h->
        h.begin();h.ack();val count=h.wire.sent.size
        val deadline=System.nanoTime()+26_000_000_000L
        while(h.session.state.value.busy && System.nanoTime()<deadline) Thread.sleep(100)
        assertFalse(h.session.state.value.busy);assertEquals("unknown",h.session.state.value.notice)
        h.unobserved();assertEquals(count,h.wire.sent.size);assertEquals(1,h.wire.sent.count {it.optString("action")=="addmeshuser"})
    }}
}
