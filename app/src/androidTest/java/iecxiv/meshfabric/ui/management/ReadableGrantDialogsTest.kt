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
@RunWith(AndroidJUnit4::class) class ReadableGrantDialogsTest {
    @get:Rule val compose=createComposeRule()
    private val actor="user//admin";private val mid="mesh//office"
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture(val groups:Boolean,missingName:Boolean=false,startLoading:Boolean=false):AutoCloseable {
        val uid=if(groups) "ugrp//target" else "user//target"
        val prefix=if(groups) "group-grant-" else "direct-grant-"
        val client=MeshCentralClient();val session=ProfileSession(client)
        val subject=JSONObject().put("_id",uid).put("name",if(missingName) "" else "Equipo").put("desc","Madrid").put("siteadmin",0).put("links",JSONObject())
        val mesh=JSONObject().put("_id",mid).put("name","Oficina").put("desc","Madrid").put("links",JSONObject().put(actor,JSONObject().put("rights",0xffffffffL)))
        val baseline=ProfileState(ready=true,user=JSONObject().put("_id",actor).put("siteadmin",if(groups) 256L else 2L),users=if(groups) emptyList() else listOf(subject),groups=listOf(mesh),listedUserGroups=if(groups) listOf(subject) else emptyList(),userGroupPhase="ready")
        val state=mutableStateOf(if(startLoading) baseline.copy(listedUserGroups=null,userGroupPhase="loading") else baseline)
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-grant-identity-test")
            @Suppress("UNCHECKED_CAST") val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>
            connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    if(groups) GroupMeshGrantDialog(session,state.value,uid) {} else UserMeshGrantDialog(session,state.value,uid) {}
                }}
            }
        }
        fun ambiguousSubject():ProfileState {
            val other=JSONObject(subject.toString()).put("_id",if(groups) "ugrp//other" else "user//other")
            return if(groups) baseline.copy(listedUserGroups=listOf(subject,other)) else baseline.copy(users=listOf(subject,other))
        }
        fun assertNoIds() {for(id in listOf(actor,uid,mid)) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    private fun verifyGrant(groups:Boolean) {Fixture(groups).use {f->
        f.assertNoIds()
        compose.onNodeWithTag(f.prefix+"candidate-"+mid).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag(f.prefix+"prepare").assertIsNotEnabled()
        compose.onNodeWithText("Avanzado: máscara hexadecimal").performScrollTo().performClick()
        compose.onNodeWithTag(f.prefix+"hex").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag(f.prefix+"prepare").assertIsEnabled()
        val duplicate=JSONObject(f.mesh.toString()).put("_id","mesh//other")
        compose.runOnIdle {f.state.value=f.baseline.copy(groups=listOf(f.mesh,duplicate))}
        compose.onNodeWithTag(f.prefix+"prepare").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag(f.prefix+"prepare").assertIsEnabled().performClick()
        f.assertNoIds()
        compose.onNodeWithText("Grupo de dispositivos: Oficina · Madrid").assertExists()
        compose.onNodeWithTag(f.prefix+"send").assertIsNotEnabled()
        compose.onNodeWithTag(f.prefix+"consent").performScrollTo().performClick()
        compose.onNodeWithTag(f.prefix+"send").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.ambiguousSubject()}
        compose.onNodeWithTag(f.prefix+"send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag(f.prefix+"send").assertIsNotEnabled()
        compose.onNodeWithTag(f.prefix+"consent").assertIsNotEnabled()
    }}
    private fun verifyMissingName(groups:Boolean) {Fixture(groups,true).use {f->
        f.assertNoIds()
        compose.onNodeWithText("nombre no disponible",substring=true).assertExists()
        compose.onNodeWithTag(f.prefix+"candidate-"+mid).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(f.prefix+"prepare").assertIsNotEnabled()
    }}
    @Test fun initialDirectoryReloadDoesNotFreezeNullSubject() {Fixture(true,startLoading=true).use {f->
        compose.onNodeWithTag("group-grant-loading").assertExists()
        compose.onAllNodesWithText("No se puede confirmar:",substring=true).assertCountEquals(0)
        compose.onNodeWithTag("group-grant-prepare").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline};compose.waitForIdle()
        compose.onNodeWithTag("group-grant-loading").assertDoesNotExist()
        compose.onNodeWithText("Grupo de usuarios: Equipo · Madrid").assertExists()
        compose.onNodeWithTag("group-grant-candidate-"+mid).performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Avanzado: máscara hexadecimal").performScrollTo().performClick()
        compose.onNodeWithTag("group-grant-hex").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("group-grant-prepare").assertIsEnabled()
        f.assertNoIds()
    }}
    @Test fun directoryReloadAfterReviewCannotReactivateConfirmation() {Fixture(true).use {f->
        compose.onNodeWithTag("group-grant-candidate-"+mid).performScrollTo().performClick()
        compose.onNodeWithText("Avanzado: máscara hexadecimal").performScrollTo().performClick()
        compose.onNodeWithTag("group-grant-hex").performScrollTo().performTextReplacement("8")
        compose.onNodeWithTag("group-grant-prepare").performClick()
        compose.onNodeWithTag("group-grant-consent").performScrollTo().performClick()
        compose.onNodeWithTag("group-grant-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.baseline.copy(listedUserGroups=null,userGroupPhase="loading")};compose.waitForIdle()
        compose.onNodeWithTag("group-grant-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline};compose.waitForIdle()
        compose.onNodeWithTag("group-grant-send").assertIsNotEnabled()
        compose.onNodeWithTag("group-grant-consent").assertIsNotEnabled()
    }}
    @Test fun genuinelyMissingNameStaysBlockedAfterInitialLoad() {Fixture(true,missingName=true,startLoading=true).use {f->
        compose.runOnIdle {f.state.value=f.baseline};compose.waitForIdle()
        compose.onNodeWithText("No se puede confirmar:",substring=true).assertExists()
        compose.onNodeWithTag("group-grant-candidate-"+mid).assertIsNotEnabled()
        compose.onNodeWithTag("group-grant-prepare").assertIsNotEnabled()
        f.assertNoIds()
    }}
    @Test fun userGrantUsesNamesAndPermanentlyInvalidatesAmbiguousReview()=verifyGrant(false)
    @Test fun groupGrantUsesNamesAndPermanentlyInvalidatesAmbiguousReview()=verifyGrant(true)
    @Test fun userGrantRejectsMissingNameWithoutIdFallback()=verifyMissingName(false)
    @Test fun groupGrantRejectsMissingNameWithoutIdFallback()=verifyMissingName(true)
}
