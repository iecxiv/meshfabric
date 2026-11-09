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
@RunWith(AndroidJUnit4::class) class ReadableUserGroupDialogsTest {
    @get:Rule val compose=createComposeRule()
    private val gid="ugrp//one";private val uid="user//person";private val mid="mesh//office"
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture(val mode:String):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val group=JSONObject().put("_id",gid).put("name","Equipo").put("desc","Madrid").put("links",JSONObject().put(uid,JSONObject()).put(mid,JSONObject()))
        val member=JSONObject().put("_id",uid).put("name","Ana").put("email","ana@example.invalid")
        val mesh=JSONObject().put("_id",mid).put("name","Oficina")
        val baseline=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",256L),users=listOf(member),groups=listOf(mesh),listedUserGroups=listOf(group),userGroupPhase="ready")
        val state=mutableStateOf(baseline)
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-user-group-test")
            @Suppress("UNCHECKED_CAST") val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>
            connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    when(mode) {
                        "delete"->UserGroupDeleteDialog(session,state.value,group) {}
                        "edit"->UserGroupEditDialog(session,state.value,group) {}
                        else->UserGroupMemberRemoveDialog(session,state.value,group,uid) {}
                    }
                }}
            }
        }
        fun assertNoIds() {for(id in listOf(gid,uid,mid)) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        fun ambiguous()=baseline.copy(listedUserGroups=listOf(group,JSONObject(group.toString()).put("_id","ugrp//duplicate")))
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun deleteStillRequiresExactNameReviewAndSeparateConsent() {Fixture("delete").use {f->
        f.assertNoIds()
        compose.onNodeWithTag("ug-delete-prepare").assertIsNotEnabled()
        compose.onNodeWithTag("ug-delete-name").performScrollTo().performTextInput("equipo")
        compose.onNodeWithTag("ug-delete-prepare").assertIsNotEnabled()
        compose.onNodeWithTag("ug-delete-name").performTextReplacement("Equipo ")
        compose.onNodeWithTag("ug-delete-prepare").assertIsNotEnabled()
        compose.onNodeWithTag("ug-delete-name").performTextReplacement("Equipo")
        compose.onNodeWithTag("ug-delete-prepare").assertIsEnabled().performClick()
        compose.onNodeWithTag("ug-delete-send").assertIsNotEnabled()
        compose.onNodeWithTag("ug-delete-consent").performScrollTo().performClick()
        compose.onNodeWithTag("ug-delete-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.ambiguous()}
        compose.onNodeWithTag("ug-delete-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag("ug-delete-send").assertIsNotEnabled()
    }}
    @Test fun deletionImpactUsesNamesWithoutRawIdentifiers() {Fixture("delete").use {f->
        compose.onNodeWithText("Ver todos los enlaces recibidos").performScrollTo().performClick()
        compose.onNodeWithText("Usuario: «Ana»").assertExists()
        compose.onNodeWithText("Grupo de dispositivos: «Oficina»").assertExists()
        f.assertNoIds()
    }}
    @Test fun editingKeepsReviewAndRejectsAmbiguousTarget() {Fixture("edit").use {f->
        f.assertNoIds()
        compose.onNodeWithTag("ug-edit-name").performScrollTo().performTextReplacement("Equipo nuevo")
        compose.onNodeWithTag("ug-edit-prepare").assertIsEnabled().performClick()
        compose.onNodeWithTag("ug-edit-send").assertIsNotEnabled()
        compose.onNodeWithTag("ug-edit-consent").performScrollTo().performClick()
        compose.onNodeWithTag("ug-edit-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.ambiguous()}
        compose.onNodeWithTag("ug-edit-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag("ug-edit-send").assertIsNotEnabled()
    }}
    @Test fun memberRemovalUsesFrozenReadableIdentityAndBlocksMissingName() {Fixture("member").use {f->
        f.assertNoIds()
        compose.onNodeWithText("Usuario: Ana · ana@example.invalid").assertExists()
        compose.onNodeWithTag("ug-member-prepare").assertIsEnabled().performClick()
        compose.onNodeWithTag("ug-member-send").assertIsNotEnabled()
        compose.onNodeWithTag("ug-member-consent").performScrollTo().performClick()
        compose.onNodeWithTag("ug-member-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.baseline.copy(users=listOf(JSONObject(f.member.toString()).apply {remove("name")}))}
        compose.onNodeWithTag("ug-member-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag("ug-member-send").assertIsNotEnabled()
    }}
}
