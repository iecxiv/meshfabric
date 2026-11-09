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
@RunWith(AndroidJUnit4::class) class ReadableMemberAddDialogTest {
    @get:Rule val compose=createComposeRule()
    private val gid="ugrp//group";private val uid="user//alice"
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture(missingName:Boolean=false):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val group=JSONObject().put("_id",gid).put("name","Equipo").put("desc","Madrid").put("links",JSONObject())
        val account=JSONObject().put("_id",uid).put("name",if(missingName) "" else "Alicia").put("email","alice@example.invalid").put("links",JSONObject())
        val baseline=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",258L),users=listOf(account),listedUserGroups=listOf(group),userGroupPhase="ready")
        val state=mutableStateOf(baseline)
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-member-add-test")
            @Suppress("UNCHECKED_CAST") val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>
            connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {UserGroupMemberAddDialog(session,state.value,group) {}}}
            }
        }
        fun assertNoIds() {for(id in listOf(gid,uid)) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        fun prepare() {
            compose.onNodeWithTag("member-add-candidate-"+uid).assertIsEnabled().performClick()
            compose.onNodeWithTag("member-add-prepare").assertIsEnabled().performClick()
            compose.onNodeWithTag("member-add-send").assertIsNotEnabled()
            compose.onNodeWithTag("member-add-consent").performScrollTo().performClick()
            compose.onNodeWithTag("member-add-send").assertIsEnabled()
        }
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun selectionAndReviewUseReadableNamesButKeepOriginalInternalToken() {Fixture().use {f->
        f.assertNoIds()
        compose.onNodeWithText("Alicia · alice@example.invalid").assertExists()
        f.prepare();f.assertNoIds()
        compose.onNodeWithText("Usuario: Alicia · alice@example.invalid").assertExists()
        val packet=profileMemberAddPacket("user//admin","https://example.invalid/",f.group,f.account,true)
        assertEquals("alice",profileUserGroupMemberAddWire(packet).getJSONArray("usernames").getString(0))
        assertEquals(gid,profileUserGroupMemberAddWire(packet).getString("ugrpid"))
    }}
    @Test fun equalNamesRequireDistinctContextAndEmailIsSearchable() {Fixture().use {f->
        val other=JSONObject(f.account.toString()).put("_id","user//other")
        compose.runOnIdle {f.state.value=f.baseline.copy(users=listOf(f.account,other))}
        compose.onNodeWithTag("member-add-candidate-"+uid).assertIsNotEnabled()
        val distinct=JSONObject(other.toString()).put("email","other@example.invalid")
        compose.runOnIdle {f.state.value=f.baseline.copy(users=listOf(f.account,distinct))}
        compose.onNodeWithTag("member-add-candidate-"+uid).assertIsEnabled()
        compose.onNodeWithTag("member-add-candidate-user//other").assertIsEnabled()
        compose.onNodeWithTag("member-add-search").performScrollTo().performTextInput("alice@example.invalid")
        compose.onNodeWithTag("member-add-candidate-"+uid).assertExists()
        compose.onNodeWithTag("member-add-candidate-user//other").assertDoesNotExist()
        f.assertNoIds()
    }}
    @Test fun missingNameCannotBeSelectedAndNeverFallsBackToId() {Fixture(true).use {f->
        f.assertNoIds()
        compose.onNodeWithTag("member-add-candidate-"+uid).assertIsNotEnabled()
        compose.onNodeWithTag("member-add-prepare").assertIsNotEnabled()
        compose.onNodeWithText("nombre no disponible").assertExists()
    }}
    @Test fun ambiguousGroupPermanentlyInvalidatesReviewedAddition() {Fixture().use {f->
        f.prepare()
        val other=JSONObject(f.group.toString()).put("_id","ugrp//other")
        compose.runOnIdle {f.state.value=f.baseline.copy(listedUserGroups=listOf(f.group,other))}
        compose.onNodeWithTag("member-add-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag("member-add-send").assertIsNotEnabled()
        compose.onNodeWithTag("member-add-consent").assertIsNotEnabled()
    }}
    @Test fun ambiguousAccountPermanentlyInvalidatesReviewedAddition() {Fixture().use {f->
        f.prepare()
        val other=JSONObject(f.account.toString()).put("_id","user//other")
        compose.runOnIdle {f.state.value=f.baseline.copy(users=listOf(f.account,other))}
        compose.onNodeWithTag("member-add-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag("member-add-send").assertIsNotEnabled()
    }}
    @Test fun lostAuthorityPermanentlyInvalidatesReviewedAddition() {Fixture().use {f->
        f.prepare()
        compose.runOnIdle {f.state.value=f.baseline.copy(user=JSONObject().put("_id","user//admin").put("siteadmin",0))}
        compose.onNodeWithTag("member-add-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag("member-add-send").assertIsNotEnabled()
    }}
}
