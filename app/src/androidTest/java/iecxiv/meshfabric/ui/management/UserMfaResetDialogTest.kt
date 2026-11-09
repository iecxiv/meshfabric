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
@RunWith(AndroidJUnit4::class) class UserMfaResetDialogTest {
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
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {UserMfaResetDialog(session,state.value,uid,{})}}
            }
        }
        fun prepare() {compose.onNodeWithTag("mfa-reset-name").performScrollTo().performTextReplacement("Alicia");compose.onNodeWithTag("mfa-reset-prepare").performClick()}
        fun approve() {compose.onNodeWithTag("mfa-reset-consent").performScrollTo().performClick();compose.onNodeWithTag("mfa-reset-effects").performScrollTo().performClick()}
        fun allowed(s:ProfileState=baseline)=userMfaResetContext(s,user,actor.getString("_id"),2L,server,server,true,actor,baseline.server.toString())
        fun noIds() {for(id in listOf(uid,"user//admin")) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {assertNull(field(client,"webSocket").get(client));session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
    @Test fun explicitEffectsExactNameAndTwoConsentsAreRequired() {Fixture().use {f->
        compose.onNodeWithText("También se borrará el teléfono",substring=true).assertExists()
        compose.onNodeWithText("actualiza passchange y elimina passtype",substring=true).assertExists()
        compose.onNodeWithTag("mfa-reset-name").performScrollTo().performTextReplacement(uid)
        compose.onNodeWithTag("mfa-reset-prepare").assertIsNotEnabled()
        f.prepare();compose.onNodeWithTag("mfa-reset-send").assertIsNotEnabled()
        compose.onNodeWithTag("mfa-reset-consent").performScrollTo().performClick()
        compose.onNodeWithTag("mfa-reset-send").assertIsNotEnabled()
        compose.onNodeWithTag("mfa-reset-effects").performScrollTo().performClick()
        compose.onNodeWithTag("mfa-reset-send").assertIsEnabled();assertTrue(f.packets.isEmpty());f.noIds()
    }}
    @Test fun phoneChangeInvalidatesReviewWithoutAutomaticRestoration() {Fixture().use {f->
        f.prepare();f.approve()
        compose.runOnIdle {f.state.value=f.baseline.copy(users=listOf(JSONObject(f.user.toString()).put("phone","changed")))};compose.waitForIdle()
        compose.onNodeWithTag("mfa-reset-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline};compose.waitForIdle()
        compose.onNodeWithTag("mfa-reset-send").assertIsNotEnabled();assertTrue(f.packets.isEmpty())
    }}
    @Test fun transportSendsOnlyOnceAndNeverTreatsCredentialsEventAsMfaSuccess() {Fixture().use {f->
        f.prepare();f.approve();compose.onNodeWithTag("mfa-reset-send").performClick()
        compose.onNodeWithTag("mfa-reset-unconfirmed").assertExists()
        compose.onNodeWithTag("mfa-reset-send").assertIsNotEnabled()
        assertEquals(1,f.packets.size)
        val p=f.packets.single()
        assertEquals(setOf("action","userid","pass","removeMultiFactor","resetNextLogin","responseid"),p.keys().asSequence().toSet())
        assertEquals("changeuserpass",p.getString("action"));assertEquals(uid,p.getString("userid"));assertEquals("",p.getString("pass"));assertTrue(p.getBoolean("removeMultiFactor"));assertTrue(p.getBoolean("resetNextLogin"))
        assertFalse(f.session.resetUserMfa(f.user,f.actor.getString("_id"),2L,f.server,f.actor,f.baseline.server.toString(),true))
        val receive=f.session.javaClass.getDeclaredMethod("receive",JSONObject::class.java).apply {isAccessible=true}
        receive.invoke(f.session,JSONObject().put("action","event").put("event",JSONObject().put("action","accountchange").put("msgid",75).put("account",JSONObject(f.user.toString()).put("phone",JSONObject.NULL))))
        assertTrue(f.session.state.value.busy);assertEquals("sending",f.session.state.value.notice)
        f.session.stop();assertEquals("unknown",f.session.state.value.notice);assertEquals(1,f.packets.size)
    }}
    @Test fun policyRejectsLocksMissingAuthorityAmbiguityAndChangedMetadata() {Fixture().use {f->
        assertTrue(f.allowed())
        assertFalse(f.allowed(f.baseline.copy(server=JSONObject().put("lock2factor",true))))
        assertFalse(f.allowed(f.baseline.copy(server=null)))
        assertFalse(f.allowed(f.baseline.copy(ready=false)))
        assertFalse(f.allowed(f.baseline.copy(detailsBusy=true)))
        assertFalse(f.allowed(f.baseline.copy(user=JSONObject(f.actor.toString()).put("siteadmin",0))))
        assertFalse(f.allowed(f.baseline.copy(users=listOf(f.user,JSONObject(f.user.toString())))))
        assertFalse(f.allowed(f.baseline.copy(users=listOf(JSONObject(f.user.toString()).put("passchange",10)))))
        assertFalse(f.session.resetUserMfa(f.user,f.actor.getString("_id"),2L,f.server,f.actor,f.baseline.server.toString(),false))
        assertTrue(f.packets.isEmpty())
    }}
    @Test fun packetPreservesExistingForcedChangeChoiceWithoutPretendingToPreserveTimestamp() {
        val u=JSONObject().put("_id",uid).put("passchange",-1L)
        assertTrue(userMfaResetPacket(u).getBoolean("resetNextLogin"))
        u.put("passchange",1234L);val p=userMfaResetPacket(u)
        assertFalse(p.getBoolean("resetNextLogin"));assertEquals("",p.getString("pass"));assertFalse(p.has("hint"));assertFalse(p.has("passchange"));assertFalse(p.has("passtype"));assertFalse(p.has("phone"))
    }
}
