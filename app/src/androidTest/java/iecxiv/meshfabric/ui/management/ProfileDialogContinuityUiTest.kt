package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
class ProfileDialogContinuityUiTest {
    @get:Rule val compose=createComposeRule()
    private fun ready()=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",2),users=listOf(JSONObject().put("_id","user//alice").put("name","Alicia").put("email","alice@example.invalid")))
    @Test fun cachedDataNeverAuthorizesAndFreshRemovalWins() {
        val previous=ready()
        val paused=profileDialogPresentation(ProfileState(),previous,"user//admin")
        assertEquals("Alicia",paused.users.single().getString("name"));assertFalse(paused.ready);assertFalse(paused.canWrite)
        val loading=profileDialogPresentation(previous.copy(loading=true),previous,"user//admin")
        assertFalse(loading.canWrite)
        val fresh=previous.copy(users=emptyList())
        assertTrue(profileDialogPresentation(fresh,previous,"user//admin").users.isEmpty())
        assertTrue(profileDialogPresentation(ProfileState(),previous,"user//other").users.isEmpty())
        assertTrue(profileDialogPresentation(previous,previous,"user//other").users.isEmpty())
    }
    @Test fun realUserDialogKeepsDisclosureAndDraftThroughPause() {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val live=mutableStateOf(ready())
        try {
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    val display=rememberProfileDialogPresentation(live.value,"same-server","user//admin")
                    UserAdministrationDialog(session,display,ProfileForm(true,"edit","user//alice","Alicia","alice@example.invalid"),onDismiss={})
                }}
            }
            compose.onNodeWithText("Nombre real").performTextInput("Draft")
            compose.onNodeWithText("Información adicional").performScrollTo().performClick()
            compose.onNodeWithText("Dominio").assertExists()
            compose.runOnIdle {live.value=ProfileState()}
            compose.onNodeWithText("Dominio").assertExists()
            compose.onNodeWithText("Draft",substring=true).assertExists()
            compose.runOnIdle {live.value=ready().copy(loading=true)}
            compose.onNodeWithText("Dominio").assertExists()
            compose.runOnIdle {live.value=ready()}
            compose.onNodeWithText("Dominio").assertExists()
            compose.onNodeWithText("Draft",substring=true).assertExists()
        } finally {
            session.close()
            val f=client.javaClass.getDeclaredField("scope").apply {isAccessible=true}
            (f.get(client) as CoroutineScope).cancel()
        }
    }
}
