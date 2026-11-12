package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.ui.theme.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class LogCardOutlineUiTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val mode=mutableStateOf(AppThemeMode.DARK)
    private var outline=Color.Unspecified
    private var background=Color.Unspecified
    private var density=1f
    @After fun closeScope() {scope.cancel()}
    private fun content(activity:Boolean,initial:AppThemeMode,dynamic:Boolean=false) {
        mode.value=initial
        val journal=ActivityJournal({"Equipo"},{"Operador"})
        journal.event(JSONObject().put("action","changenode").put("eventid","outline").put("nodeid","node//a").put("msg","Actualizado"))
        val feedback=RemoteActionFeedback(scope,{false},{_,_->null})
        val record=ServerEventRecord("changenode","Actualizado","Operador","node//a","Equipo",null)
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(initial==AppThemeMode.LIGHT) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MeshCentralTheme(mode.value,dynamicColor=dynamic) {
                    density=LocalDensity.current.density
                    outline=MaterialTheme.colorScheme.outlineVariant
                    background=if(activity) appCardColors(CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)).containerColor
                        else appCardColors(CardDefaults.cardColors()).containerColor
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        ManagementCard(Modifier.testTag("reference")) {Spacer(Modifier.height(24.dp))}
                        if(activity) ActivityTimeline(journal,feedback,Modifier.weight(1f))
                        else ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.READY,listOf(record)),"",{},true,{},Modifier.weight(1f))
                    }
                }
            }
        }
    }
    private fun close(expected:Color,actual:Color) {
        assertEquals(expected.red,actual.red,0.02f)
        assertEquals(expected.green,actual.green,0.02f)
        assertEquals(expected.blue,actual.blue,0.02f)
    }
    private fun verify(activity:Boolean) {
        compose.waitForIdle()
        val tag=if(activity) "activity-card-event-outline" else "log-event-row-0"
        val card=compose.onNodeWithTag(tag,useUnmergedTree=true).captureToImage().toPixelMap()
        val reference=compose.onNodeWithTag("reference",useUnmergedTree=true).captureToImage().toPixelMap()
        close(reference[reference.width/2,0],card[card.width/2,0])
        close(outline,card[card.width/2,0])
        close(background,card[card.width/2,(2*density).toInt()])
    }
    @Test fun forcedDarkActivityMatchesManagement() {content(true,AppThemeMode.DARK);verify(true)}
    @Test fun forcedDarkEventsMatchesManagement() {content(false,AppThemeMode.DARK);verify(false)}
    @Test fun forcedLightActivityMatchesManagement() {content(true,AppThemeMode.LIGHT);verify(true)}
    @Test fun forcedLightEventsMatchesManagement() {content(false,AppThemeMode.LIGHT);verify(false)}
    @Test fun dynamicDarkEventsUsesActiveOutline() {content(false,AppThemeMode.DARK,true);verify(false)}
    @Test fun activityThemeSwitchRecomputesOutline() {
        content(true,AppThemeMode.DARK);verify(true)
        compose.runOnIdle {mode.value=AppThemeMode.LIGHT};verify(true)
        compose.runOnIdle {mode.value=AppThemeMode.DARK};verify(true)
    }
}
