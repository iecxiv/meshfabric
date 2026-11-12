package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.model.SessionStatus
import iecxiv.meshfabric.ui.management.LocalDeviceActionMenu
import iecxiv.meshfabric.ui.theme.LocalAppDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeviceOptionsVisualUiTest {
    @get:Rule val compose=createComposeRule()
    private val node=MeshNode(id="node//visual",name="Equipo",meshid="mesh//visual",conn=1)
    private fun heights(dark:Boolean) {
        compose.setContent { MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalAppDarkTheme provides dark) {
                Column(Modifier.width(360.dp)) {
                    DeviceOptionsRow(node)
                    SessionActionButton("Terminal",Icons.Default.Terminal,SessionStatus.NONE,{},Modifier.testTag("session_reference"))
                }
            }
        } }
        val reference=compose.onNodeWithTag("session_reference").fetchSemanticsNode().boundsInRoot.height
        for(key in listOf("general","events","details","console")) {
            val height=compose.onNodeWithTag("device_option_$key").fetchSemanticsNode().boundsInRoot.height
            assertEquals(key,reference,height,1f)
        }
    }
    @Test fun matchingHeightsLight()=heights(false)
    @Test fun matchingHeightsDark()=heights(true)
    @Test fun headerSpacingAndIndependentClicks() {
        var menuClicks=0
        var density=1f
        compose.setContent { MaterialTheme {
            density=LocalDensity.current.density
            CompositionLocalProvider(LocalDeviceActionMenu provides { _ ->
                IconButton(onClick={ menuClicks++ },modifier=Modifier.testTag("menu_reference")) { Icon(Icons.Default.MoreVert,null) }
            }) {
                DeviceCard(node,onOpenDesktop={},onOpenTerminal={},onOpenFiles={})
            }
        } }
        val toggle=compose.onNodeWithTag("device_options_toggle").fetchSemanticsNode().boundsInRoot
        val menu=compose.onNodeWithTag("menu_reference").fetchSemanticsNode().boundsInRoot
        assertEquals(32f*density,menu.center.x-toggle.center.x,1f)
        compose.onNodeWithTag("device_options_toggle").performClick()
        compose.onNodeWithTag("device_options_row").assertIsDisplayed()
        assertEquals(0,menuClicks)
        compose.onNodeWithTag("menu_reference").performClick()
        assertEquals(1,menuClicks)
        compose.onNodeWithTag("device_options_row").assertIsDisplayed()
    }
    @Test fun whiteDarkContentAndOnSurfaceLightContent() {
        val dark=androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            MaterialTheme(colorScheme=lightColorScheme(onSurface=Color.Magenta,primary=Color.Blue)) {
                CompositionLocalProvider(LocalAppDarkTheme provides dark.value) { DeviceOptionsRow(node) }
            }
        }
        fun count(color:Color):Int {
            val image=compose.onNodeWithTag("device_option_general").captureToImage().toPixelMap()
            return (0 until image.height).sumOf { y -> (0 until image.width).count { x ->
                val p=image[x,y]
                kotlin.math.abs(p.red-color.red)<0.02f && kotlin.math.abs(p.green-color.green)<0.02f && kotlin.math.abs(p.blue-color.blue)<0.02f && p.alpha>0.9f
            } }
        }
        assertTrue(count(Color.White)>0)
        assertEquals(0,count(Color.Blue))
        compose.runOnIdle { dark.value=false }
        assertTrue(count(Color.Magenta)>0)
        assertEquals(0,count(Color.Blue))
    }
}
