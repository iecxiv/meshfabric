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
class UserMeshGrantSessionTest {
    private val actor="user//admin"
    private val uid="user//target"
    private val mid="mesh//group"
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private fun set(target:Any,name:String,value:Any?) {field(target,name).set(target,value)}
    private fun get(target:Any,name:String):Any?=field(target,name).get(target)
    private fun user(linked:Boolean)=JSONObject().put("_id",uid).put("siteadmin",0).put("links",JSONObject().apply {if(linked) put(mid,JSONObject().put("rights",8))})
    private fun mesh(linked:Boolean)=JSONObject().put("_id",mid).put("links",JSONObject().put(actor,JSONObject().put("rights",0xffffffffL)).apply {if(linked) put(uid,JSONObject().put("rights",8))})
    private class MemorySocket:WebSocket {
        val sent=mutableListOf<JSONObject>()
        override fun request():Request=Request.Builder().url("https://example.invalid/").build()
        override fun queueSize():Long=0L
        override fun send(text:String):Boolean {sent.add(JSONObject(text));return true}
        override fun send(bytes:ByteString):Boolean=false
        override fun close(code:Int,reason:String?):Boolean=true
        override fun cancel() {}
    }
    private inner class Harness:AutoCloseable {
        val client=MeshCentralClient()
        val session=ProfileSession(client)
        val wire=MemorySocket()
        val oldTags:Map<String,String>
        lateinit var operation:JSONObject
        init {
            set(client,"serverUrl","https://example.invalid/");set(client,"authCookie","offline-test")
            @Suppress("UNCHECKED_CAST")
            val connected=get(client,"_connectionState") as MutableStateFlow<Boolean>
            connected.value=true
            set(session,"binding",Triple(client.serverUrl,client.authCookie,false));set(session,"socket",wire)
            session.state.value=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",2),users=listOf(user(false)),groups=listOf(mesh(false)))
            assertNull(get(client,"webSocket"))
            session.refresh();oldTags=tags()
            reply("users",oldTags.getValue("users"),false);reply("meshes",oldTags.getValue("meshes"),false)
            assertTrue(session.state.value.canWrite)
        }
        fun receive(p:JSONObject) {synchronized(session) {session.javaClass.getDeclaredMethod("receive",JSONObject::class.java).apply {isAccessible=true}.invoke(session,p)}}
        fun tags()=wire.sent.filter {it.optString("action") in setOf("users","meshes")}.associate {it.getString("action") to it.getString("tag")}
        fun begin() {
            val s=session.state.value;val server=session.broadcastServerLabel()
            assertTrue(server.isNotBlank())
            val packet=profileUserMeshGrantPacket(actor,server,s.users.single(),s.groups.single(),8,true)
            assertFalse(session.submit(packet))
            assertTrue(session.submitUserMeshGrant(packet))
            operation=wire.sent.last()
            assertEquals("addmeshuser",operation.getString("action"))
            assertEquals(setOf("action","userids","meshid","meshadmin","responseid"),operation.keys().asSequence().toSet())
        }
        fun ack(id:String=operation.getString("responseid"),result:String="Added user target")=receive(JSONObject().put("action","addmeshuser").put("responseid",id).put("result",result).put("success",1).put("failed",0))
        fun reply(action:String,tag:String?,linked:Boolean=true) {
            val p=JSONObject().put("action",action).put(action,JSONArray().put(if(action=="users") user(linked) else mesh(linked)))
            if(tag!=null) p.put("tag",tag)
            receive(p)
        }
        fun unobserved() {assertNotEquals("userMeshGrantedObserved",session.state.value.notice);assertEquals("",session.state.value.grantedDirectUser);assertEquals("",session.state.value.grantedGroup);assertTrue(wire.sent.none {it.optString("action")=="usergroups"})}
        override fun close() {session.close();(get(client,"scope") as CoroutineScope).cancel()}
    }
    @Test fun wrongAckAndOldUntaggedOrResponseidOnlyDataCannotConfirm() {Harness().use {h->
        h.begin();val count=h.wire.sent.size;h.ack("wrong");assertEquals(count,h.wire.sent.size);h.unobserved()
        h.ack();val tags=h.tags();assertNotEquals(h.oldTags,tags)
        h.reply("users",h.oldTags.getValue("users"));h.reply("meshes",null)
        h.receive(JSONObject().put("action","users").put("responseid",tags.getValue("users")).put("users",JSONArray().put(user(false))))
        h.unobserved();assertTrue(h.session.state.value.busy)
        h.reply("meshes",tags.getValue("meshes"));h.unobserved()
        h.reply("meshes",tags.getValue("meshes"));h.unobserved()
        h.reply("users",tags.getValue("users"))
        assertEquals("userMeshGrantedObserved",h.session.state.value.notice)
    }}
    @Test fun bothReadOrdersRequireAckAndExactRights() {for(order in listOf(listOf("users","meshes"),listOf("meshes","users"))) Harness().use {h->
        h.begin();h.reply("users",h.oldTags.getValue("users"));h.reply("meshes",h.oldTags.getValue("meshes"));h.unobserved()
        h.ack();val tags=h.tags();h.reply(order[0],tags.getValue(order[0]));h.unobserved()
        h.reply(order[1],tags.getValue(order[1]))
        assertEquals("userMeshGrantedObserved",h.session.state.value.notice)
        assertEquals(uid,h.session.state.value.grantedDirectUser);assertEquals(mid,h.session.state.value.grantedDirectMesh)
        assertFalse(h.session.state.value.busy)
    }}
    @Test fun eitherMissingLinkPreventsObservation() {for(remaining in listOf("users","meshes")) Harness().use {h->
        h.begin();h.ack();val tags=h.tags()
        for(action in listOf("users","meshes")) h.reply(action,tags.getValue(action),action==remaining)
        h.unobserved();assertTrue(h.session.state.value.busy)
    }}
    @Test fun correlatedPermissionErrorIsNeverAnEmptySuccessfulRead() {Harness().use {h->
        h.begin();h.ack();val tags=h.tags()
        h.receive(JSONObject().put("action","users").put("responseid",tags.getValue("users")).put("result","Access denied"))
        h.reply("meshes",tags.getValue("meshes"));h.unobserved()
        assertTrue(h.session.state.value.readError);assertFalse(h.session.state.value.canWrite)
    }}
    @Test fun disconnectClearsPendingReadsAndNeverResends() {Harness().use {h->
        h.begin();h.ack();val tags=h.tags();val count=h.wire.sent.size
        h.session.stop();assertEquals("unknown",h.session.state.value.notice)
        h.reply("users",tags.getValue("users"));h.reply("meshes",tags.getValue("meshes"));h.unobserved()
        assertEquals("unknown",h.session.state.value.notice);assertEquals(count,h.wire.sent.size)
        assertEquals(1,h.wire.sent.count {it.optString("action")=="addmeshuser"})
    }}
    @Test fun actualOperationTimerLeavesOutcomeUnknownWithoutRetry() {Harness().use {h->
        h.begin();h.ack();val count=h.wire.sent.size
        val deadline=System.nanoTime()+26_000_000_000L
        while(h.session.state.value.busy && System.nanoTime()<deadline) Thread.sleep(100)
        assertFalse(h.session.state.value.busy);assertEquals("unknown",h.session.state.value.notice)
        h.unobserved();assertEquals(count,h.wire.sent.size)
        assertEquals(1,h.wire.sent.count {it.optString("action")=="addmeshuser"})
    }}

    @Test fun interleavedEventCannotReplaceCorrelatedUserSnapshot() {Harness().use {h->
        h.begin();h.ack();val tags=h.tags()
        h.reply("users",tags.getValue("users"),false)
        h.receive(JSONObject().put("action","event").put("event",JSONObject().put("action","accountchange").put("account",user(true))))
        h.reply("meshes",tags.getValue("meshes"),true)
        h.unobserved();assertTrue(h.session.state.value.busy)
    }}
    @Test fun identityRefreshStartsANewReadRoundWithoutGroupQueries() {Harness().use {h->
        h.begin();h.ack();val first=h.tags();h.reply("users",first.getValue("users"))
        h.receive(JSONObject().put("action","userinfo").put("userinfo",JSONObject().put("_id",actor).put("siteadmin",2)))
        val latest=h.tags();assertNotEquals(first,latest)
        h.reply("meshes",first.getValue("meshes"));h.reply("users",first.getValue("users"));h.unobserved()
        h.reply("meshes",latest.getValue("meshes"));h.unobserved()
        h.reply("users",latest.getValue("users"));assertEquals("userMeshGrantedObserved",h.session.state.value.notice)
        assertEquals("",h.session.state.value.grantedGroup);assertTrue(h.wire.sent.none {it.optString("action")=="usergroups"})
    }}
    @Test fun losingManageUsersStopsTheDirectChannelWithoutRetry() {Harness().use {h->
        h.begin();h.ack();val count=h.wire.sent.size
        h.receive(JSONObject().put("action","userinfo").put("userinfo",JSONObject().put("_id",actor).put("siteadmin",0)))
        assertEquals("unknown",h.session.state.value.notice);h.unobserved();assertEquals(count,h.wire.sent.size)
    }}
}
