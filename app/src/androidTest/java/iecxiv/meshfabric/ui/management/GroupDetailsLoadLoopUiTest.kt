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
class GroupDetailsLoadLoopUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun realGroupDialogDoesNotReloadWhenItsOwnRequestCompletes() {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val actor=JSONObject().put("_id","user//admin").put("siteadmin",4294967295L)
        val group=JSONObject().put("_id","mesh//office").put("name","Oficina").put("mtype",2)
        val ready=ProfileState(ready=true,user=actor,groups=listOf(group),nodes=emptyList())
        session.state.value=ready
        var requests=0
        try {
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    val live by session.state.collectAsState()
                    val display=rememberProfileDialogPresentation(live,"same-session","user//admin")
                    DeviceGroupDetailsDialog(session,display,"mesh//office",false,onDismiss={},onEdit={},onDelete={},onLoadDetails={
                        requests++;session.state.value=session.state.value.copy(loading=true,detailsBusy=true)
                    })
                }}
            }
            compose.waitForIdle();compose.runOnIdle {assertEquals(1,requests)}
            repeat(5) {
                compose.runOnIdle {session.state.value=ready}
                compose.waitForIdle();compose.runOnIdle {assertEquals(1,requests)}
                compose.onNodeWithText("Oficina").assertExists()
            }
            compose.onNodeWithText("Dispositivos",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Buscar dispositivo").assertExists()
            compose.onNodeWithText("Actualizar").performClick()
            compose.runOnIdle {assertEquals(2,requests);session.state.value=ready}
            compose.waitForIdle();compose.runOnIdle {assertEquals(2,requests)}
            compose.onNodeWithText("Buscar dispositivo").assertExists()
        } finally {
            session.close()
            val f=client.javaClass.getDeclaredField("scope").apply {isAccessible=true}
            (f.get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun newGenerationLoadsOnceButPresentationChangesDoNot() {
        var epoch by mutableLongStateOf(1)
        var loading by mutableStateOf(false)
        var requests=0
        compose.setContent {GroupDetailsAutoLoad("owner","group",epoch,true,loading,false,false,true) {requests++;loading=true}}
        compose.waitForIdle();compose.runOnIdle {assertEquals(1,requests);loading=false}
        compose.waitForIdle();compose.runOnIdle {assertEquals(1,requests);epoch=2}
        compose.waitForIdle();compose.runOnIdle {assertEquals(2,requests);loading=false}
        compose.waitForIdle();compose.runOnIdle {assertEquals(2,requests)}
    }
}
