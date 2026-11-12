package iecxiv.meshfabric.ui.theme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import iecxiv.meshfabric.ui.management.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
class DarkContrastUiTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    @After fun closeScope() {scope.cancel()}
    private fun textColor(text:String):Color {
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.isNotEmpty())
        return layouts.first().layoutInput.style.color
    }
    private fun activity(dark:Boolean) {
        val journal=ActivityJournal({"Equipo"},{"Operador"})
        journal.sent("Notificación",JSONObject().put("action","toast").put("title","Prueba").put("nodeid","node/test").put("responseid","contrast"),true)
        val feedback=RemoteActionFeedback(scope,{false},{_,_->null})
        var expected=Color.Unspecified;var state=Color.Unspecified
        compose.setContent {MeshCentralTheme(if(dark) AppThemeMode.DARK else AppThemeMode.LIGHT) {
            expected=if(dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            state=MaterialTheme.colorScheme.onSurfaceVariant
            ActivityTimeline(journal,feedback,Modifier.fillMaxSize())
        }}
        compose.waitForIdle()
        assertEquals(expected,textColor("Por Operador"))
        assertEquals(expected,textColor("Enviada al servidor; visualización en el equipo no confirmada."))
        assertEquals(expected,textColor("Título: Prueba"))
        assertEquals(state,textColor("Solicitud enviada"))
    }
    @Test fun darkActivityUsesEventText()=activity(true)
    @Test fun lightActivityKeepsOriginalText()=activity(false)
    @Test fun forcedLightPreservesOutlineRoles() {
        var a=Color.Unspecified;var b=Color.Unspecified
        compose.setContent {MeshCentralTheme(AppThemeMode.LIGHT) {a=MaterialTheme.colorScheme.outline;b=MaterialTheme.colorScheme.outlineVariant}}
        compose.waitForIdle()
        assertEquals(lightColorScheme().outline,a);assertEquals(lightColorScheme().outlineVariant,b)
    }
    @Test fun dynamicDarkUsesBrighterOutlineRoles() {
        var a=Color.Unspecified;var b=Color.Unspecified;var foreground=Color.Unspecified;var text=Color.Unspecified
        compose.setContent {MeshCentralTheme(AppThemeMode.DARK,dynamicColor=true) {
            a=MaterialTheme.colorScheme.outline;b=MaterialTheme.colorScheme.outlineVariant
            foreground=MaterialTheme.colorScheme.onSurface;text=activityCardTextColor(Color.Gray)
        }}
        compose.waitForIdle()
        assertEquals(Color(0xFF94A3B8),a);assertEquals(Color(0xFF7C899D),b);assertEquals(foreground,text)
    }
    @Test fun switchingBackToLightRestoresOriginalRoles() {
        val mode=mutableStateOf(AppThemeMode.DARK);var outline=Color.Unspecified
        compose.setContent {MeshCentralTheme(mode.value) {outline=MaterialTheme.colorScheme.outline}}
        compose.waitForIdle();assertEquals(Color(0xFF94A3B8),outline)
        compose.runOnIdle {mode.value=AppThemeMode.LIGHT}
        compose.waitForIdle();assertEquals(lightColorScheme().outline,outline)
    }
}
