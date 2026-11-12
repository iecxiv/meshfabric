package iecxiv.meshfabric.ui.devices
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import iecxiv.meshfabric.data.model.*
import iecxiv.meshfabric.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DeviceCardWhiteTextUiTest {
    @get:Rule val compose=createComposeRule()
    private val mode=mutableStateOf(AppThemeMode.DARK)
    private lateinit var node:MeshNode
    private var originalName=Color.Unspecified
    private var originalSecondary=Color.Unspecified
    private fun content(initial:AppThemeMode,online:Boolean,dynamic:Boolean=false) {
        mode.value=initial
        node=MeshNode(name="Equipo de prueba",conn=if(online) 1 else 0,osdesc="Linux",
            networkIpv4=listOf(DeviceIpv4("eth0","192.0.2.10")))
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {
                uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if(initial==AppThemeMode.LIGHT) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MeshCentralTheme(mode.value,dynamicColor=dynamic) {
                    originalSecondary=MaterialTheme.colorScheme.onSurfaceVariant
                    val source=CardDefaults.cardColors(containerColor=if(node.isOnline) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                    originalName=source.contentColor
                    Column(Modifier.fillMaxWidth()) {
                        DeviceCard(node,onOpenDesktop={},onOpenTerminal={},onOpenFiles={})
                    }
                }
            }
        }
    }
    private fun textColor(text:String):Color {
        val layouts=mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.isNotEmpty())
        return layouts.first().layoutInput.style.color
    }
    private fun verify(dark:Boolean) {
        compose.waitForIdle()
        assertEquals(if(dark) Color.White else originalName,textColor(node.name))
        assertEquals(if(dark) Color.White else originalSecondary,textColor(node.osDisplayName))
        val network=DeviceNetworkDisplay.text(node.networkIpv4,hidden=false)
        assertNotNull(network)
        assertEquals(if(dark) Color.White else originalSecondary,textColor(requireNotNull(network)))
        assertEquals(Color.White,textColor(if(node.isOnline) "ONLINE" else "OFFLINE"))
    }
    @Test fun darkOnlineWithLightSystemUsesWhite() {content(AppThemeMode.DARK,true);verify(true)}
    @Test fun darkOfflineWithLightSystemUsesWhite() {content(AppThemeMode.DARK,false);verify(true)}
    @Test fun lightOnlineWithDarkSystemPreservesOriginals() {content(AppThemeMode.LIGHT,true);verify(false)}
    @Test fun lightOfflineWithDarkSystemPreservesOriginals() {content(AppThemeMode.LIGHT,false);verify(false)}
    @Test fun dynamicDarkStillUsesWhite() {content(AppThemeMode.DARK,true,true);verify(true)}
    @Test fun themeSwitchRestoresOriginals() {
        content(AppThemeMode.DARK,false);verify(true)
        compose.runOnIdle {mode.value=AppThemeMode.LIGHT};verify(false)
        compose.runOnIdle {mode.value=AppThemeMode.DARK};verify(true)
    }
}
