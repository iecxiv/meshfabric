package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.model.SessionStatus
import iecxiv.meshfabric.ui.management.ManagementCard
import iecxiv.meshfabric.ui.theme.*
import kotlin.math.ceil
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DeviceCardOutlineUiTest {
    @get:Rule val compose=createComposeRule()
    private fun close(expected:Color,actual:Color) {
        assertEquals(expected.red,actual.red,0.02f)
        assertEquals(expected.green,actual.green,0.02f)
        assertEquals(expected.blue,actual.blue,0.02f)
    }
    private fun verify(dark:Boolean,online:Boolean,dynamic:Boolean=false,status:SessionStatus=SessionStatus.NONE) {
        val expanded=mutableStateOf(false)
        var outline=Color.Unspecified
        var background=Color.Unspecified
        var groupBackground=Color.Unspecified
        var density=1f
        val node=MeshNode(id="node//outline",name="Equipo",meshid="mesh//outline",conn=if(online) 1 else 0,osdesc="Windows")
        compose.setContent {
            MeshCentralTheme(if(dark) AppThemeMode.DARK else AppThemeMode.LIGHT,dynamicColor=dynamic) {
                val scheme=MaterialTheme.colorScheme
                outline=scheme.outlineVariant
                background=appCardColors(CardDefaults.cardColors(containerColor=if(online) scheme.surfaceVariant else scheme.surface)).containerColor
                groupBackground=scheme.primaryContainer
                density=LocalDensity.current.density
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ManagementCard(Modifier.testTag("reference")) {Spacer(Modifier.height(24.dp))}
                    Box(Modifier.fillMaxWidth().testTag("group")) {
                        GroupHeaderCard(MeshGroup(id="mesh//outline",name="Grupo"),if(online) 1 else 0,if(online) 0 else 1,expanded.value,{expanded.value=!expanded.value})
                    }
                    Box(Modifier.fillMaxWidth().testTag("device")) {
                        DeviceCard(node,desktopStatus=status,onOpenDesktop={},onOpenTerminal={},onOpenFiles={})
                    }
                }
            }
        }
        fun check() {
            compose.waitForIdle()
            val device=compose.onNodeWithTag("device",useUnmergedTree=true).captureToImage().toPixelMap()
            val reference=compose.onNodeWithTag("reference",useUnmergedTree=true).captureToImage().toPixelMap()
            val group=compose.onNodeWithTag("group",useUnmergedTree=true).captureToImage().toPixelMap()
            close(outline,device[0,device.height/2])
            close(reference[0,reference.height/2],device[0,device.height/2])
            close(background,device[ceil(density).toInt(),device.height/2])
            close(groupBackground,group[0,group.height/2])
            compose.onNodeWithText(if(online) "ONLINE" else "OFFLINE").assertExists()
        }
        check()
        compose.runOnIdle {expanded.value=true}
        check()
    }
    @Test fun darkOnline()=verify(true,true)
    @Test fun darkOffline()=verify(true,false)
    @Test fun lightOnline()=verify(false,true)
    @Test fun lightOffline()=verify(false,false)
    @Test fun dynamicDarkOnline()=verify(true,true,true)
    @Test fun dynamicLightOffline()=verify(false,false,true)
    @Test fun connectedSessionKeepsOuterOutline()=verify(true,false,status=SessionStatus.CONNECTED)
    @Test fun bufferedSessionKeepsOuterOutline()=verify(false,false,status=SessionStatus.DISCONNECTED_WITH_BUFFER)
}
