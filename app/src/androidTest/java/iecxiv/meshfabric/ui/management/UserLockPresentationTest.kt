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
@RunWith(AndroidJUnit4::class) class UserLockPresentationTest {
    @get:Rule val compose=createComposeRule()
    private val uid="user//alice"
    private fun field(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply {isAccessible=true}
    private inner class Fixture(unlocking:Boolean=false,fullTarget:Boolean=false,selfAccount:Boolean=false):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val target=JSONObject().put("_id",uid).put("name","Alicia").put("realname","Alicia García").put("email","alice@example.invalid").put("siteadmin",if(fullTarget) 0xffffffffL else if(unlocking) 32L else 0L)
        val baseline=ProfileState(ready=true,user=JSONObject().put("_id",if(selfAccount) uid else "user//admin").put("siteadmin",2L),users=listOf(target))
        val state=mutableStateOf(baseline)
        init {
            field(client,"serverUrl").set(client,"https://example.invalid/");field(client,"authCookie").set(client,"offline-lock-presentation")
            @Suppress("UNCHECKED_CAST") val connected=field(client,"_connectionState").get(client) as MutableStateFlow<Boolean>;connected.value=true
            field(session,"binding").set(session,Triple(client.serverUrl,client.authCookie,false))
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    UserAdministrationDialog(session,state.value,ProfileForm(true,if(unlocking) "unlock" else "lock",uid,"Alicia","alice@example.invalid"),onDismiss={})
                }}
            }
        }
        fun noIds() {for(id in listOf(uid,"user//admin")) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)}
        override fun close() {assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session));session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()}
    }
    @Test fun lockUsesReadableContextAndRetainsExistingConsentGate() {Fixture().use {f->
        compose.onNodeWithText("Bloquear usuario").assertExists()
        compose.onNodeWithText("Alicia · Alicia García · alice@example.invalid").assertExists()
        compose.onAllNodesWithTag("profile-user-list-icon",useUnmergedTree=true).assertCountEquals(1)
        compose.onNodeWithTag("user-admin-confirm").assertHasClickAction().assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithTag("user-admin-confirm").assertHasClickAction().assertIsEnabled();f.noIds()
    }}
    @Test fun unlockUsesTheSameReadableContextAndConsent() {Fixture(unlocking=true).use {f->
        compose.onNodeWithText("Desbloquear usuario").assertExists()
        compose.onNodeWithText("Alicia · Alicia García · alice@example.invalid").assertExists()
        compose.onNodeWithTag("user-admin-confirm").assertHasClickAction().assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithTag("user-admin-confirm").assertHasClickAction().assertIsEnabled();f.noIds()
    }}
    @Test fun identifierIsVisibleOnlyAfterExplicitExpansion() {Fixture().use {f->
        f.noIds();compose.onNode(hasClickAction() and hasAnyAncestor(hasTestTag("profile-user-identifier")),useUnmergedTree=true).performScrollTo().performClick();compose.onNodeWithText(uid).assertExists()
    }}
    @Test fun ownAccountRemainsBlocked() {Fixture(selfAccount=true).use {f->
        compose.onNode(isToggleable()).performClick();compose.onNodeWithTag("user-admin-confirm").assertHasClickAction().assertIsNotEnabled();f.noIds()
    }}
    @Test fun fullAdministratorRemainsBlocked() {Fixture(fullTarget=true).use {f->
        compose.onNode(isToggleable()).performClick();compose.onNodeWithTag("user-admin-confirm").assertHasClickAction().assertIsNotEnabled();f.noIds()
    }}
    @Test fun changedLockStateCannotBeSubmittedUnderTheOldOperation() {Fixture().use {f->
        compose.onNode(isToggleable()).performClick();compose.onNodeWithTag("user-admin-confirm").assertHasClickAction().assertIsEnabled()
        compose.runOnIdle {f.state.value=f.baseline.copy(users=listOf(JSONObject(f.target.toString()).put("siteadmin",32L)))}
        compose.waitForIdle();compose.onNodeWithTag("user-admin-confirm").assertHasClickAction().assertIsNotEnabled();f.noIds()
    }}
}
