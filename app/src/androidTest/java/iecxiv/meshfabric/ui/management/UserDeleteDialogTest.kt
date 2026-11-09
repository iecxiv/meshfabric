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
@RunWith(AndroidJUnit4::class) class UserDeleteDialogTest {
    @get:Rule val compose=createComposeRule()
    private val uid="user//alice"
    private fun person()=JSONObject().put("_id",uid).put("name","Alicia").put("realname","Alicia García").put("email","alice@example.invalid").put("siteadmin",0)
    private fun base(u:JSONObject=person())=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("name","Administración").put("siteadmin",2L),users=listOf(u))
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture(missing:Boolean=false):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val baseline=base(if(missing) person().put("name","") else person())
        val state=mutableStateOf(baseline)
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-test")
            @Suppress("UNCHECKED_CAST")
            val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>;connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    UserAdministrationDialog(session,state.value,ProfileForm(true,"delete",uid,"Alicia","alice@example.invalid"),onDismiss={})
                }}
            }
        }
        fun assertNoIds() {for(id in listOf(uid,"user//admin","user//other")) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    private fun prepare() {
        compose.onNodeWithTag("user-delete-name").performScrollTo().performTextReplacement("Alicia")
        compose.onNodeWithTag("user-delete-prepare").performClick()
    }
    @Test fun exactNameReviewAndFinalConsentAreSeparate() {Fixture().use {f->
        compose.onNodeWithText("Alicia · Alicia García · alice@example.invalid").assertExists()
        compose.onNodeWithTag("user-delete-name").performScrollTo().performTextReplacement(uid)
        compose.onNodeWithTag("user-delete-prepare").assertIsNotEnabled()
        compose.onNodeWithTag("user-delete-name").performTextReplacement("alicia")
        compose.onNodeWithTag("user-delete-prepare").assertIsNotEnabled()
        prepare()
        compose.onNodeWithTag("user-delete-send").assertIsNotEnabled()
        compose.onNodeWithTag("user-delete-consent").performScrollTo().performClick()
        compose.onNodeWithTag("user-delete-send").assertIsEnabled()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithTag("user-delete-name").assertExists()
        prepare()
        compose.onNodeWithTag("user-delete-send").assertIsNotEnabled()
        f.assertNoIds()
    }}
    @Test fun missingNameNeverFallsBackToId() {Fixture(true).use {f->
        compose.onNodeWithText("nombre no disponible").assertExists()
        compose.onNodeWithTag("user-delete-name").assertIsNotEnabled()
        compose.onNodeWithTag("user-delete-prepare").assertIsNotEnabled()
        f.assertNoIds()
    }}
    @Test fun ambiguityPermanentlyInvalidatesFrozenReview()=assertInvalidation {s->s.copy(users=s.users+JSONObject(s.users.single().toString()).put("_id","user//other"))}
    @Test fun renamedTargetPermanentlyInvalidatesFrozenReview()=assertInvalidation {s->s.copy(users=listOf(JSONObject(s.users.single().toString()).put("name","Otra cuenta")))}
    @Test fun changedLinksPermanentlyInvalidateFrozenReview()=assertInvalidation {s->s.copy(users=listOf(JSONObject(s.users.single().toString()).put("links",JSONObject().put("mesh//one",JSONObject().put("rights",8)))))}
    @Test fun authorityLossPermanentlyInvalidatesFrozenReview()=assertInvalidation {s->s.copy(user=JSONObject(requireNotNull(s.user).toString()).put("siteadmin",0))}
    @Test fun disconnectPermanentlyInvalidatesFrozenReview()=assertInvalidation {s->s.copy(ready=false)}
    private fun assertInvalidation(replace:(ProfileState)->ProfileState) {Fixture().use {f->
        prepare();compose.onNodeWithTag("user-delete-consent").performScrollTo().performClick()
        compose.onNodeWithTag("user-delete-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=replace(f.baseline)};compose.waitForIdle()
        compose.onNodeWithTag("user-delete-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline};compose.waitForIdle()
        compose.onNodeWithTag("user-delete-send").assertIsNotEnabled()
        compose.onNodeWithTag("user-delete-consent").assertIsNotEnabled()
        f.assertNoIds()
    }}
    @Test fun packetRetainsExistingContractAndOperationName() {
        val before=person();val packet=userDeletePacket(before)
        assertEquals(setOf("action","userid","username"),packet.keys().asSequence().toSet())
        assertEquals("deleteuser",packet.getString("action"));assertEquals(uid,packet.getString("userid"));assertEquals("Alicia",packet.getString("username"))
        before.put("name","Otro nombre");assertEquals("Alicia",packet.getString("username"))
    }
    @Test fun contextRejectsMissingDuplicateForeignAndPrivilegedTargetsAndChangedServer() {
        val before=person();val s=base(before)
        fun allowed(state:ProfileState,target:JSONObject?=before,server:String="host")=userDeleteContext(state,target,"user//admin",2L,"host",server,true)
        assertTrue(allowed(s));assertFalse(allowed(s.copy(users=emptyList())));assertFalse(allowed(s.copy(users=listOf(before,JSONObject(before.toString())))))
        val foreign=person().put("_id","user/other/alice");assertFalse(allowed(base(foreign),foreign))
        val admin=person().put("siteadmin",0xffffffffL);assertFalse(allowed(base(admin),admin))
        assertFalse(allowed(s,server="other"));assertFalse(allowed(s.copy(loading=true)))
        val self=JSONObject(requireNotNull(s.user).toString())
        assertFalse(userDeleteContext(s.copy(users=listOf(self)),self,"user//admin",2L,"host","host",true))
    }
}
