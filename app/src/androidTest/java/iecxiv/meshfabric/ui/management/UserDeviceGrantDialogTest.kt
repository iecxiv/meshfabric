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
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
@RunWith(AndroidJUnit4::class) class UserDeviceGrantDialogTest {
    @get:Rule val compose=createComposeRule()
    private val uid="user//alice";private val nid="node//laptop";private val mid="mesh//office"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}
    private inner class Fixture(missing:Boolean=false):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val user=JSONObject().put("_id",uid).put("name","Alicia").put("email","alice@example.invalid").put("siteadmin",0)
        val node=JSONObject().put("_id",nid).put("name",if(missing) "" else "Portátil").put("desc","Personal").put("meshid",mid)
        val mesh=JSONObject().put("_id",mid).put("name","Oficina").put("desc","Madrid").put("mtype",2)
        val actor=JSONObject().put("_id","user//admin").put("name","Administración").put("siteadmin",2L).put("links",JSONObject().put(mid,JSONObject().put("rights",2L)))
        val baseline=ProfileState(ready=true,user=actor,users=listOf(user),nodes=listOf(node),groups=listOf(mesh),userGroups=emptyList())
        val state=mutableStateOf(baseline)
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-test")
            @Suppress("UNCHECKED_CAST")
            val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>;connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    UserAdministrationDialog(session,state.value,ProfileForm(true,"edit",uid,"Alicia","alice@example.invalid"),onDismiss={})
                }}
            }
            compose.onNodeWithText("Acciones y seguridad").performScrollTo().performClick()
            compose.onNodeWithText("Agregar dispositivo").performScrollTo().performClick()
        }
        fun prepare() {
            compose.onNodeWithTag("device-grant-candidate").performScrollTo().performClick()
            compose.onNodeWithTag("device-grant-prepare").performClick()
        }
        fun assertNoIds() {for(id in listOf(uid,nid,mid,"user//admin")) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun readableSearchFrozenDefaultAndSeparateConsent() {Fixture().use {f->
        compose.onNodeWithText("Portátil · Personal",substring=true).assertExists()
        compose.onNodeWithTag("device-grant-search").performScrollTo().performTextReplacement(nid)
        compose.onAllNodesWithTag("device-grant-candidate").assertCountEquals(0)
        compose.onNodeWithTag("device-grant-search").performTextReplacement("Madrid")
        compose.onAllNodesWithTag("device-grant-candidate").assertCountEquals(1)
        compose.onNodeWithTag("device-grant-search").performTextReplacement("")
        f.prepare()
        compose.onNodeWithText("Derechos congelados: 0x00000108").assertExists()
        compose.onNodeWithTag("device-grant-send").assertIsNotEnabled()
        compose.onNodeWithTag("device-grant-consent").performScrollTo().performClick()
        compose.onNodeWithTag("device-grant-send").assertIsEnabled()
        f.assertNoIds()
        compose.onNodeWithText("Volver a seleccionar").performClick()
        compose.onNodeWithTag("device-grant-prepare").assertIsNotEnabled()
    }}
    @Test fun unnamedNodeCannotBeSelected() {Fixture(true).use {f->
        compose.onNodeWithText("nombre no disponible",substring=true).assertExists()
        compose.onNodeWithTag("device-grant-candidate").assertIsNotEnabled()
        compose.onNodeWithTag("device-grant-prepare").assertIsNotEnabled()
        f.assertNoIds()
    }}
    @Test fun ambiguousNodeInvalidatesWithoutAutomaticRestoration()=invalidates {s->s.copy(nodes=s.nodes.orEmpty()+JSONObject(requireNotNull(s.nodes).single().toString()).put("_id","node//other"))}
    @Test fun ambiguousUserInvalidatesWithoutAutomaticRestoration()=invalidates {s->s.copy(users=s.users+JSONObject(s.users.single().toString()).put("_id","user//other"))}
    @Test fun renamedParentInvalidatesWithoutAutomaticRestoration()=invalidates {s->s.copy(groups=listOf(JSONObject(s.groups.single().toString()).put("name","Otro grupo")))}
    @Test fun authorityLossInvalidatesWithoutAutomaticRestoration()=invalidates {s->s.copy(user=JSONObject(requireNotNull(s.user).toString()).put("links",JSONObject()))}
    @Test fun disconnectInvalidatesWithoutAutomaticRestoration()=invalidates {s->s.copy(ready=false)}
    private fun invalidates(change:(ProfileState)->ProfileState) {Fixture().use {f->
        f.prepare();compose.onNodeWithTag("device-grant-consent").performScrollTo().performClick()
        compose.onNodeWithTag("device-grant-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=change(f.baseline)};compose.waitForIdle()
        compose.onNodeWithTag("device-grant-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline};compose.waitForIdle()
        compose.onNodeWithTag("device-grant-send").assertIsNotEnabled()
        compose.onNodeWithTag("device-grant-consent").assertIsNotEnabled()
        f.assertNoIds()
    }}
    @Test fun packetKeepsWireFieldsAndRejectsUnselectableBits() {
        val u=JSONObject().put("_id",uid);val n=JSONObject().put("_id",nid).put("name","Portátil")
        val p=userDeviceGrantPacket(u,n,264L)
        assertEquals(setOf("action","nodeid","nodename","userids","rights"),p.keys().asSequence().toSet())
        assertEquals("adddeviceuser",p.getString("action"));assertEquals(nid,p.getString("nodeid"));assertEquals(uid,p.getJSONArray("userids").getString(0));assertEquals(264L,p.getLong("rights"))
        for(value in listOf(0L,1L,2L,4L,0xffffffffL,0x80000000L)) assertFalse(userDeviceGrantRights(value))
    }
}
