package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.WebSocket
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy
import java.util.Locale
@RunWith(AndroidJUnit4::class) class UserMessageDialogTest {
    @get:Rule val compose=createComposeRule()
    private val uid="user//alice"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}
    private inner class Fixture:AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val user=JSONObject().put("_id",uid).put("name","Alicia").put("email","alice@example.invalid").put("siteadmin",0).put("phone","+34600000000").put("passchange",-1).put("otpsecret",1)
        val actor=JSONObject().put("_id","user//admin").put("name","Administración").put("siteadmin",2L)
        val baseline=ProfileState(ready=true,user=actor,users=listOf(user),server=JSONObject())
        val state=mutableStateOf(baseline)
        val packets=mutableListOf<JSONObject>()
        val server:String
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-mfa-test")
            @Suppress("UNCHECKED_CAST") val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>;connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            server=session.broadcastServerLabel();session.state.value=baseline
            val socket=Proxy.newProxyInstance(WebSocket::class.java.classLoader,arrayOf(WebSocket::class.java)) {_,method,args->when(method.name) {
                "send"->{val text=args?.firstOrNull() as? String;if(text==null) false else {packets.add(JSONObject(text));true}}
                "close"->true
                "queueSize"->0L
                "toString"->"OfflineMfaSocket"
                else->null
            }} as WebSocket
            field(session,"socket").set(session,socket)
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {UserMessageDialog(session,state.value,uid,{})}}
            }
        }
        fun prepare() {compose.onNodeWithTag("user-message-input").performScrollTo().performTextReplacement("Aviso de prueba");compose.onNodeWithTag("user-message-prepare").performClick()}
        fun approve() {compose.onNodeWithTag("user-message-consent").performScrollTo().performClick()}
        fun allowed(s:ProfileState=baseline)=userMessageContext(s,user,actor.getString("_id"),2L,server,server,true,actor,baseline.server.toString())
        fun noIds() {for(id in listOf(uid,"user//admin")) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {assertNull(field(client,"webSocket").get(client));session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
    @Test fun recipientAndMessageAreReviewedBeforeSeparateConsent() {Fixture().use {f->
        compose.onNodeWithText("Destinatario: Alicia · alice@example.invalid").assertExists()
        compose.onNodeWithTag("user-message-prepare").assertIsNotEnabled()
        f.prepare();compose.onNodeWithTag("user-message-input").assertDoesNotExist()
        compose.onNodeWithTag("user-message-preview").assertTextEquals("Aviso de prueba")
        compose.onNodeWithTag("user-message-send").assertIsNotEnabled()
        f.approve();compose.onNodeWithTag("user-message-send").assertIsEnabled()
        assertTrue(f.packets.isEmpty());f.noIds()
        compose.onNodeWithText("Volver a redactar").performClick()
        compose.onNodeWithTag("user-message-prepare").performClick()
        compose.onNodeWithTag("user-message-send").assertIsNotEnabled()
    }}
    @Test fun targetChangeInvalidatesPermanently()=invalidates {s->s.copy(users=listOf(JSONObject(s.users.single().toString()).put("name","Otra cuenta")))}
    @Test fun authorityLossInvalidatesPermanently()=invalidates {s->s.copy(user=JSONObject(requireNotNull(s.user).toString()).put("siteadmin",0))}
    @Test fun disconnectInvalidatesPermanently()=invalidates {s->s.copy(ready=false)}
    @Test fun ambiguousIdentityInvalidatesPermanently()=invalidates {s->s.copy(users=s.users+JSONObject(s.users.single().toString()).put("_id","user//other"))}
    private fun invalidates(replace:(ProfileState)->ProfileState) {Fixture().use {f->
        f.prepare();f.approve();compose.onNodeWithTag("user-message-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=replace(f.baseline)};compose.waitForIdle()
        compose.onNodeWithTag("user-message-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline};compose.waitForIdle()
        compose.onNodeWithTag("user-message-send").assertIsNotEnabled()
        compose.onNodeWithTag("user-message-consent").assertIsNotEnabled();assertTrue(f.packets.isEmpty())
    }}
    @Test fun notifyUserNeverFallsBackToBroadcastOrClaimsDelivery() {Fixture().use {f->
        f.prepare();f.approve();compose.onNodeWithTag("user-message-send").performClick()
        compose.onNodeWithTag("user-message-unconfirmed").assertExists()
        compose.onNodeWithTag("user-message-send").assertIsNotEnabled()
        assertEquals(1,f.packets.size);val p=f.packets.single()
        assertEquals(setOf("action","userid","msg","responseid"),p.keys().asSequence().toSet())
        assertEquals("notifyuser",p.getString("action"));assertEquals(uid,p.getString("userid"));assertEquals("Aviso de prueba",p.getString("msg"))
        assertFalse(f.session.sendUserMessage(f.user,f.actor.getString("_id"),2L,f.server,f.actor,f.baseline.server.toString(),"Aviso de prueba",true))
        f.session.stop();assertEquals("unknown",f.session.state.value.notice);assertEquals(1,f.packets.size)
    }}
    @Test fun policyDoesNotInheritMfaLockAndRejectsMissingTargetsOrScopeChanges() {Fixture().use {f->
        assertTrue(f.allowed())
        assertFalse(f.allowed(f.baseline.copy(users=emptyList())))
        assertFalse(f.allowed(f.baseline.copy(detailsBusy=true)))
        val locked=f.baseline.copy(server=JSONObject().put("lock2factor",true))
        assertTrue(userMessageContext(locked,f.user,f.actor.getString("_id"),2L,f.server,f.server,true,f.actor,locked.server.toString()))
        val restricted=JSONObject(f.actor.toString()).put("groups",org.json.JSONArray().put("realm-one"))
        val scoped=f.baseline.copy(user=restricted)
        assertFalse(userMessageContext(scoped,f.user,restricted.getString("_id"),2L,f.server,f.server,true,restricted,scoped.server.toString()))
        val target=JSONObject(f.user.toString()).put("groups",org.json.JSONArray().put("realm-one"))
        assertTrue(userMessageContext(scoped.copy(users=listOf(target)),target,restricted.getString("_id"),2L,f.server,f.server,true,restricted,scoped.server.toString()))
        assertFalse(f.session.sendUserMessage(f.user,f.actor.getString("_id"),2L,f.server,f.actor,f.baseline.server.toString(),"Aviso",false));assertTrue(f.packets.isEmpty())
    }}
    @Test fun invalidMessagesCannotBecomePackets() {
        val u=JSONObject().put("_id",uid)
        for(text in listOf("","   ","x".repeat(513),"a"+0.toChar()+"b")) {assertFalse(userMessageValid(text));assertTrue(runCatching {userMessagePacket(u,text)}.isFailure)}
        val p=userMessagePacket(u,"x".repeat(512))
        assertEquals(512,p.getString("msg").length);assertEquals(setOf("action","userid","msg"),p.keys().asSequence().toSet())
    }
}
