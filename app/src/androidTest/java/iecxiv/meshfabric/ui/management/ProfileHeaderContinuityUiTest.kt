package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
class ProfileHeaderContinuityUiTest {
    @get:Rule val compose=createComposeRule()
    private fun verify(scale:Float) {
        val ready=ProfileState(ready=true,user=JSONObject().put("_id","user//admin"))
        val live=mutableStateOf(ready)
        compose.setContent {
            val configuration=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
            val density=Density(LocalDensity.current.density,scale)
            CompositionLocalProvider(LocalConfiguration provides configuration,LocalDensity provides density) {MaterialTheme {
                val state=rememberProfileDialogPresentation(live.value,"same-identity","user//admin")
                Column(Modifier.width(320.dp)) {
                    ProfileStableSectionHeader(header={Box(Modifier.fillMaxWidth().height(56.dp).testTag("profile-header"))},tabs={
                        Box(Modifier.fillMaxWidth().testTag("profile-tabs")) {ManagementSectionTabs(listOf("Mi cuenta","Grupos de dispositivos","Usuarios","Grupos"),1) {}}
                    })
                    ProfileManagementConnectionStatus(state,onReconnect={})
                    Box(Modifier.fillMaxWidth().height(20.dp).testTag("write-availability").semantics {if(!state.canWrite) disabled()})
                }
            }}
        }
        val header=compose.onNodeWithTag("profile-header").fetchSemanticsNode().boundsInRoot
        val before=compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot
        assertEquals(header.bottom,before.top,0.5f)
        for(next in listOf(ready.copy(loading=true),ProfileState(notice="disconnected"),ProfileState(notice="connecting"),ready.copy(loading=true),ready)) {
            compose.runOnIdle {live.value=next}
            val after=compose.onNodeWithTag("profile-tabs").fetchSemanticsNode().boundsInRoot
            assertEquals(before.top,after.top,0.5f);assertEquals(before.bottom,after.bottom,0.5f)
            compose.onNodeWithText("Datos anteriores; acciones bloqueadas",substring=true).assertDoesNotExist()
                      if(!next.ready || next.loading) compose.onNodeWithTag("write-availability").assertIsNotEnabled() else compose.onNodeWithTag("write-availability").assertIsEnabled()
        }
    }
    @Test fun tabsStayDirectlyBelowHeaderAcrossReconnect()=verify(1f)
    @Test fun largeFontDoesNotIntroduceTransientHeaderRows()=verify(1.8f)
}
