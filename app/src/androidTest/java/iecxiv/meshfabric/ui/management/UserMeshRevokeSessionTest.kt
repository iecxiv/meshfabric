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
class UserMeshRevokeSessionTest {
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
            session.state.value=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",2),users=listOf(user(true)),groups=listOf(mesh(true)))
            assertNull(get(client,"webSocket"))
            session.refresh();oldTags=tags()
            reply("users",oldTags.getValue("users"),true);reply("meshes",oldTags.getValue("meshes"),true)
            assertTrue(session.state.value.canWrite)
        }
        fun receive(p:JSONObject) {synchronized(session) {session.javaClass.getDeclaredMethod("receive",JSONObject::class.java).apply {isAccessible=true}.invoke(session,p)}}
        fun tags()=wire.sent.filter {it.optString("action") in setOf("users","meshes")}.associate {it.getString("action") to it.getString("tag")}
        fun begin() {
            val s=session.state.value;val server=session.broadcastServerLabel()
            assertTrue(server.isNotBlank())
            assertTrue(session.submitUserMeshRevoke(profileUserMeshRevokePacket(actor,server,s.users.single(),s.groups.single(),true)))
            operation=wire.sent.last()
            assertEquals("removemeshuser",operation.getString("action"))
            assertEquals(setOf("action","userid","meshid","responseid"),operation.keys().asSequence().toSet())
        }
        fun ack(id:String=operation.getString("responseid"),result:String="ok")=receive(JSONObject().put("action","removemeshuser").put("responseid",id).put("result",result))
        fun reply(action:String,tag:String?,linked:Boolean=false) {
            val p=JSONObject().put("action",action).put(action,JSONArray().put(if(action=="users") user(linked) else mesh(linked)))
            if(tag!=null) p.put("tag",tag)
            receive(p)
        }
        fun unobserved() {assertNotEquals("userMeshRevokedObserved",session.state.value.notice);assertEquals("",session.state.value.revokedDirectUser)}
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
        assertEquals("userMeshRevokedObserved",h.session.state.value.notice)
    }}
    @Test fun bothReadOrdersRequireAckAndBothAbsences() {for(order in listOf(listOf("users","meshes"),listOf("meshes","users"))) Harness().use {h->
        h.begin();h.reply("users",h.oldTags.getValue("users"));h.reply("meshes",h.oldTags.getValue("meshes"));h.unobserved()
        h.ack();val tags=h.tags();h.reply(order[0],tags.getValue(order[0]));h.unobserved()
        h.reply(order[1],tags.getValue(order[1]))
        assertEquals("userMeshRevokedObserved",h.session.state.value.notice)
        assertEquals(uid,h.session.state.value.revokedDirectUser);assertEquals(mid,h.session.state.value.revokedDirectMesh)
        assertFalse(h.session.state.value.busy)
    }}
    @Test fun eitherRemainingLinkPreventsObservation() {for(remaining in listOf("users","meshes")) Harness().use {h->
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
        assertEquals(1,h.wire.sent.count {it.optString("action")=="removemeshuser"})
    }}
    @Test fun actualOperationTimerLeavesOutcomeUnknownWithoutRetry() {Harness().use {h->
        h.begin();h.ack();val count=h.wire.sent.size
        val deadline=System.nanoTime()+26_000_000_000L
        while(h.session.state.value.busy && System.nanoTime()<deadline) Thread.sleep(100)
        assertFalse(h.session.state.value.busy);assertEquals("unknown",h.session.state.value.notice)
        h.unobserved();assertEquals(count,h.wire.sent.size)
        assertEquals(1,h.wire.sent.count {it.optString("action")=="removemeshuser"})
    }}
}
