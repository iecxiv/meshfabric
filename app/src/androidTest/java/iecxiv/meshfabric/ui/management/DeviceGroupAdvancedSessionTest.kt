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
class DeviceGroupAdvancedSessionTest {
    private val actor="user//admin";private val mid="mesh//one"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}
    private fun set(o:Any,n:String,v:Any?) {field(o,n).set(o,v)}
    private fun get(o:Any,n:String)=field(o,n).get(o)
    private fun mesh(days:Int=0,flags:Long=0x80000002L,consent:Long=0x80000000L,rights:Long=1L)=JSONObject().put("_id",mid).put("mtype",2).put("flags",flags).put("consent",consent).put("links",JSONObject().put(actor,JSONObject().put("rights",rights))).apply {if(days!=0) put("expireDevs",days)}
    private fun server()=JSONObject().put("devGroupSessionRecording",1).put("consent",0).put("autoremoveinactivedevices",90)
    private class MemorySocket:WebSocket {
        val sent=mutableListOf<JSONObject>();var accept=true
        override fun request():Request=Request.Builder().url("https://example.invalid/").build()
        override fun queueSize():Long=0L
        override fun send(text:String):Boolean {sent.add(JSONObject(text));return accept}
        override fun send(bytes:ByteString):Boolean=false
        override fun close(code:Int,reason:String?):Boolean=true
        override fun cancel() {}
    }
    private inner class Harness(val role:Long=0L):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client);val wire=MemorySocket()
        val oldTags:Map<String,String>;lateinit var operation:JSONObject
        init {
            set(client,"serverUrl","https://example.invalid/");set(client,"authCookie","offline-test")
            @Suppress("UNCHECKED_CAST")
            val connected=get(client,"_connectionState") as MutableStateFlow<Boolean>;connected.value=true
            set(session,"binding",Triple(client.serverUrl,client.authCookie,false));set(session,"socket",wire)
            session.state.value=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",role),server=server(),groups=listOf(mesh()))
            assertNull(get(client,"webSocket"));session.refresh();oldTags=tags()
            reply("meshes",oldTags.getValue("meshes"),mesh());oldTags["users"]?.let {reply("users",it)}
            assertTrue(session.state.value.canWrite)
        }
        fun receive(p:JSONObject) {synchronized(session) {session.javaClass.getDeclaredMethod("receive",JSONObject::class.java).apply {isAccessible=true}.invoke(session,p)}}
        fun tags()=wire.sent.filter {it.optString("action") in setOf("meshes","users")}.associate {it.getString("action") to it.getString("tag")}
        fun draft(changes:JSONObject=JSONObject().put("flags",0x80000002L).put("expireDevs",30),confirmed:Boolean=true):JSONObject {val s=session.state.value;return deviceGroupAdvancedPacket(s,s.groups.single(),changes,session.broadcastServerLabel(),confirmed)}
        fun begin(p:JSONObject=draft()) {
            assertFalse(session.submit(p));assertFalse(session.submitUserMeshEdit(p));assertFalse(session.submitGroupMeshEdit(p))
            assertFalse(session.submit(deviceGroupAdvancedWire(p)))
            assertTrue(session.submitDeviceGroupAdvanced(p));operation=wire.sent.last()
            assertEquals("editmesh",operation.getString("action"));assertEquals(mid,operation.getString("meshid"))
            assertTrue(operation.keys().asSequence().none {it.startsWith("_")})
            assertTrue(session.state.value.busy);assertEquals("",session.state.value.advancedEditedDeviceGroup)
        }
        fun ack(id:String=operation.getString("responseid"))=receive(JSONObject().put("action","editmesh").put("responseid",id).put("result","ok"))
        fun reply(action:String,tag:String?,record:JSONObject=mesh(30)) {val data=JSONArray();if(action=="meshes") data.put(record);val p=JSONObject().put("action",action).put(action,data);if(tag!=null) p.put("tag",tag);receive(p)}
        fun unobserved() {val s=session.state.value;assertNotEquals("deviceGroupAdvancedObserved",s.notice);assertEquals("",s.advancedEditedDeviceGroup);assertEquals("",s.editedGroup);assertEquals("",s.editedDirectUser);assertEquals("",s.grantedGroup)}
        fun observed() {val s=session.state.value;assertEquals("deviceGroupAdvancedObserved",s.notice);assertEquals(mid,s.advancedEditedDeviceGroup);assertFalse(s.busy);assertEquals(1,wire.sent.count {it.optString("action")=="editmesh"})}
        override fun close() {session.close();assertNull(get(client,"webSocket"));(get(client,"scope") as CoroutineScope).cancel()}
    }
    @Test fun ackAndExactCorrelatedReadAreBothRequired() {Harness().use {h->
        h.begin();assertEquals(setOf("action","meshid","flags","expireDevs","responseid"),h.operation.keys().asSequence().toSet())
        h.reply("meshes",h.oldTags.getValue("meshes"));h.unobserved();h.ack();h.unobserved()
        h.reply("meshes",h.tags().getValue("meshes"));h.observed()
    }}
    @Test fun consentUsesItsOwnExactFieldsAndZeroExpiryMayBeAbsent() {for(consentOnly in listOf(false,true)) Harness().use {h->
        val initial=h.session.state.value.groups.single();initial.put("expireDevs",30)
        val changes=if(consentOnly) JSONObject().put("consent",0x800001ffL) else JSONObject().put("flags",0x80000002L).put("expireDevs",0)
        h.begin(h.draft(changes));h.ack()
        val record=if(consentOnly) mesh(30,consent=0x800001ffL) else mesh()
        h.reply("meshes",h.tags().getValue("meshes"),record);h.observed()
        assertEquals(consentOnly,h.operation.has("consent"));assertEquals(!consentOnly,h.operation.has("flags"))
    }}
    @Test fun oldUntaggedWrongAckAndResponseidOnlyReadsCannotConfirm() {Harness().use {h->
        h.begin();val count=h.wire.sent.size;h.ack("wrong");assertEquals(count,h.wire.sent.size);h.unobserved()
        h.ack();val tag=h.tags().getValue("meshes");assertNotEquals(h.oldTags.getValue("meshes"),tag)
        h.reply("meshes",h.oldTags.getValue("meshes"));h.reply("meshes",null)
        h.receive(JSONObject().put("action","meshes").put("responseid",tag).put("meshes",JSONArray().put(mesh(30))))
        h.unobserved();h.reply("meshes",tag);h.observed()
    }}
    @Test fun extraUsersReadMayFinishInEitherOrder() {for(meshFirst in listOf(false,true)) Harness(2).use {h->
        h.begin();h.ack();val tags=h.tags();val first=if(meshFirst) "meshes" else "users";val last=if(meshFirst) "users" else "meshes"
        h.reply(first,tags.getValue(first));h.unobserved();h.reply(last,tags.getValue(last));h.observed()
    }}
    @Test fun immutableCorrelatedCopyCannotBeReplacedByMutableState() {Harness(2).use {h->
        h.begin();h.ack();val tags=h.tags();h.reply("meshes",tags.getValue("meshes"),mesh(30,flags=2))
        h.session.state.value=h.session.state.value.copy(groups=listOf(mesh(30)))
        h.reply("users",tags.getValue("users"));h.unobserved();assertTrue(h.session.state.value.busy)
    }}
    @Test fun wrongValuesMissingTargetAndReadDenialCannotConfirm() {for(wrong in listOf(mesh(29),mesh(30,flags=2),mesh(30).put("mtype",4),mesh(30).put("_id","mesh//other"))) Harness().use {h->
        h.begin();h.ack();h.reply("meshes",h.tags().getValue("meshes"),wrong);h.unobserved()
    };Harness().use {h->h.begin();h.ack();h.receive(JSONObject().put("action","meshes").put("tag",h.tags().getValue("meshes")).put("result","Access denied"));h.unobserved()}}
    @Test fun duplicateAckAndIdentityRefreshInvalidateTheOldRound() {for(identity in listOf(false,true)) Harness(2).use {h->
        h.begin();h.ack();val first=h.tags();h.reply("meshes",first.getValue("meshes"))
        if(identity) h.receive(JSONObject().put("action","userinfo").put("userinfo",JSONObject().put("_id",actor).put("siteadmin",2))) else h.ack()
        val latest=h.tags();assertNotEquals(first,latest);h.reply("users",first.getValue("users"));h.unobserved()
        h.reply("users",latest.getValue("users"));h.unobserved();h.reply("meshes",latest.getValue("meshes"));h.observed()
    }}
    @Test fun frozenInputsBindingAndConfirmationAreRevalidated() {Harness().use {h->
        assertFalse(h.session.submitDeviceGroupAdvanced(h.draft(confirmed=false)))
        val p=h.draft();h.session.state.value.groups.single().put("expireDevs",5)
        assertFalse(h.session.submitDeviceGroupAdvanced(p));set(h.client,"serverUrl","https://other.invalid/")
        assertFalse(h.session.submitDeviceGroupAdvanced(p));assertTrue(h.wire.sent.none {it.optString("action")=="editmesh"})
    }}
    @Test fun requestIsCopiedAndConcurrentSubmitIsRejected() {Harness().use {h->
        val p=h.draft();h.begin(p);p.put("expireDevs",100);assertFalse(h.session.submitDeviceGroupAdvanced(p));h.ack()
        h.reply("meshes",h.tags().getValue("meshes"));h.observed()
    }}
    @Test fun authorityOrServerPolicyLossLeavesOutcomeUnknownWithoutRetry() {for(capability in listOf(false,true)) Harness().use {h->
        h.begin();h.ack();val count=h.wire.sent.size
        if(capability) h.receive(JSONObject().put("action","serverinfo").put("serverinfo",server().put("consent",8)))
        else h.reply("meshes",h.tags().getValue("meshes"),mesh(30,rights=0))
        h.unobserved();assertEquals("unknown",h.session.state.value.notice);assertFalse(h.session.state.value.busy);assertEquals(count,h.wire.sent.size)
    }}
    @Test fun explicitDenialAndFailedSendNeverRetry() {Harness().use {h->
        h.begin();val count=h.wire.sent.size;h.receive(JSONObject().put("action","editmesh").put("responseid",h.operation.getString("responseid")).put("result","Access denied"))
        h.unobserved();assertEquals("rejected",h.session.state.value.notice);assertFalse(h.session.state.value.busy);assertEquals(count,h.wire.sent.size)
    };Harness().use {h->val p=h.draft();h.wire.accept=false;assertFalse(h.session.submitDeviceGroupAdvanced(p));h.unobserved();assertEquals("unknown",h.session.state.value.notice);assertEquals(1,h.wire.sent.count {it.optString("action")=="editmesh"})}}
    @Test fun disconnectAndLateRepliesNeverConfirmOrRetry() {Harness().use {h->
        h.begin();h.ack();val tag=h.tags().getValue("meshes");val count=h.wire.sent.size;h.session.stop()
        h.reply("meshes",tag);h.ack();h.unobserved();assertEquals("unknown",h.session.state.value.notice);assertEquals(count,h.wire.sent.size)
    }}
        @Test fun readDeadlineTerminatesAdvancedOperationAndRejectsLateEvidence() {for(role in listOf(0L,2L)) Harness(role).use {h->
        h.begin();h.ack();val tags=h.tags();val count=h.wire.sent.size
        synchronized(h.session) {h.session.javaClass.getDeclaredMethod("expireListReads",java.lang.Long.TYPE).apply {isAccessible=true}.invoke(h.session,get(h.session,"generation"))}
        h.unobserved();assertEquals("unknown",h.session.state.value.notice);assertFalse(h.session.state.value.busy);assertFalse(h.session.state.value.loading);assertTrue(h.session.state.value.readError);assertFalse(h.session.state.value.canWrite)
        h.reply("meshes",tags.getValue("meshes"));tags["users"]?.let {h.reply("users",it)};h.ack()
        h.unobserved();assertEquals("unknown",h.session.state.value.notice);assertEquals(count,h.wire.sent.size);assertEquals(1,h.wire.sent.count {it.optString("action")=="editmesh"})
    }}
    @Test fun ordinaryReadDeadlineStillReportsReadFailed() {Harness().use {h->
        h.session.refresh();val count=h.wire.sent.size
        synchronized(h.session) {h.session.javaClass.getDeclaredMethod("expireListReads",java.lang.Long.TYPE).apply {isAccessible=true}.invoke(h.session,get(h.session,"generation"))}
        assertEquals("readFailed",h.session.state.value.notice);assertTrue(h.session.state.value.readError);assertFalse(h.session.state.value.loading);assertFalse(h.session.state.value.busy);assertEquals(count,h.wire.sent.size);assertTrue(h.wire.sent.none {it.optString("action")=="editmesh"})
    }}
    @Test fun actualTimeoutLeavesAnUnknownOutcomeWithoutResending() {Harness().use {h->
        h.begin();h.ack();val count=h.wire.sent.size;val deadline=System.nanoTime()+26_000_000_000L
        while(h.session.state.value.busy && System.nanoTime()<deadline) Thread.sleep(100)
        h.unobserved();assertFalse(h.session.state.value.busy);assertEquals("unknown",h.session.state.value.notice);assertEquals(count,h.wire.sent.size)
    }}
}
