package iecxiv.meshfabric.ui.management
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.WebSocket
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
@RunWith(AndroidJUnit4::class) class ProfileDetailReadsRegressionTest {
    private val mid="mesh//office";private val nid="node//laptop"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}
    private inner class Fixture:AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val requests=mutableListOf<JSONObject>()
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-detail-read-test")
            @Suppress("UNCHECKED_CAST") val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>;connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            session.state.value=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",2L))
            val socket=Proxy.newProxyInstance(WebSocket::class.java.classLoader,arrayOf(WebSocket::class.java)) {_,method,args->when(method.name) {
                "send"->{val text=args?.firstOrNull() as? String;if(text==null) false else {requests.add(JSONObject(text));true}}
                "close"->true
                "queueSize"->0L
                "toString"->"OfflineDetailReadSocket"
                else->null
            }} as WebSocket
            field(session,"socket").set(session,socket)
        }
        fun receive(p:JSONObject) {session.javaClass.getDeclaredMethod("receive",JSONObject::class.java).apply {isAccessible=true}.invoke(session,p)}
        fun nodes()=JSONObject().put("action","nodes").put("nodes",JSONObject().put(mid,JSONArray().put(JSONObject().put("_id",nid).put("name","Portátil"))))
        fun counts()=JSONObject().put("action","wssessioncount").put("wssessions",JSONObject())
        fun groups()=JSONObject().put("action","usergroups").put("ugroups",JSONObject.NULL)
        override fun close() {assertNull(field(client,"webSocket").get(client));session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
    @Test fun explicitEmptyGroupsRemainAvailableInEveryReplyOrderAndAfterRefresh() {
        val orders=listOf(listOf(0,1,2),listOf(0,2,1),listOf(1,0,2),listOf(1,2,0),listOf(2,0,1),listOf(2,1,0))
        for(order in orders) Fixture().use {f->
            repeat(2) {
                f.session.loadDetails();assertTrue(f.session.state.value.detailsBusy)
                assertNull(f.session.state.value.nodes);assertNull(f.session.state.value.userGroups)
                val replies=listOf(f.nodes(),f.groups(),f.counts())
                order.forEach {f.receive(replies[it])}
                val s=f.session.state.value
                assertFalse(s.detailsBusy);assertNotNull(s.userGroups);assertTrue(requireNotNull(s.userGroups).isEmpty())
                assertEquals(nid,requireNotNull(s.nodes).single().getString("_id"));assertEquals(mid,requireNotNull(s.nodes).single().getString("meshid"))
            }
        }
    }
    @Test fun missingMalformedAndRejectedGroupReadsStayUnknownNotEmpty() {
        val bad=listOf(JSONObject().put("action","usergroups"),JSONObject().put("action","usergroups").put("ugroups","bad"),JSONObject().put("action","usergroups").put("ugroups",JSONObject.NULL).put("result","denied"),JSONObject().put("action","usergroups").put("ugroups",JSONObject.NULL).put("error","denied"))
        for(reply in bad) Fixture().use {f->
            f.session.loadDetails();f.receive(f.nodes());f.receive(f.counts());f.receive(reply)
            assertNull(f.session.state.value.userGroups);assertNotNull(f.session.state.value.nodes);assertFalse(f.session.state.value.detailsBusy)
        }
    }
    @Test fun taggedDirectoryReadCannotConsumeTheUserDetailReply() {Fixture().use {f->
        f.session.loadDetails();f.session.loadUserGroupDirectory()
        val request=f.requests.single {it.optString("tag").startsWith("profile-user-groups:")}
        f.receive(f.groups().put("tag",request.getString("tag")))
        assertEquals("ready",f.session.state.value.userGroupPhase)
        assertTrue(requireNotNull(f.session.state.value.listedUserGroups).isEmpty())
        assertNull(f.session.state.value.userGroups);assertTrue(f.session.state.value.detailsBusy)
        f.receive(f.nodes());f.receive(f.counts());f.receive(f.groups())
        assertNotNull(f.session.state.value.userGroups);assertFalse(f.session.state.value.detailsBusy)
    }}
    @Test fun rejectedNodeReadDoesNotPublishItsPayload() {Fixture().use {f->
        f.session.loadDetails();f.receive(f.nodes().put("result","denied"));f.receive(f.groups());f.receive(f.counts())
        assertNull(f.session.state.value.nodes);assertFalse(f.session.state.value.detailsBusy)
    }}
}
