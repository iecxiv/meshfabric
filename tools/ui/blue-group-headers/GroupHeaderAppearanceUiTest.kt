package iecxiv.meshfabric.ui.theme

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
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.devices.DeviceCard
import iecxiv.meshfabric.ui.devices.GroupHeaderCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GroupHeaderAppearanceUiTest {
    @get:Rule val compose = createComposeRule()
    private val mode = mutableStateOf(AppThemeMode.DARK)
    private val expanded = mutableStateOf(false)
    private var groupColor = Color.Unspecified
    private var deviceColor = Color.Unspecified
    private var density = 1f
    private fun content(initial: AppThemeMode, systemDark: Boolean, dynamic: Boolean = false) {
        mode.value = initial
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MeshCentralTheme(themeMode = mode.value, dynamicColor = dynamic) {
                    density = LocalDensity.current.density
                    groupColor = MaterialTheme.colorScheme.primaryContainer
                    deviceColor = if (LocalAppDarkTheme.current) CardDefaults.cardColors().containerColor
                        else MaterialTheme.colorScheme.surface
                    Column(Modifier.width(300.dp)) {
                        Box(Modifier.fillMaxWidth().testTag("closed")) {
                            GroupHeaderCard(MeshGroup(name = "Grupo cerrado"), 1, 1, expanded.value) {
                                expanded.value = !expanded.value
                            }
                        }
                        Box(Modifier.fillMaxWidth().testTag("open")) {
                            GroupHeaderCard(MeshGroup(name = "Grupo abierto"), 1, 1, true) {}
                        }
                        Box(Modifier.fillMaxWidth().testTag("device")) {
                            DeviceCard(MeshNode(name = "Equipo"), onOpenDesktop = {}, onOpenTerminal = {}, onOpenFiles = {})
                        }
                        Card(Modifier.size(64.dp).testTag("event-reference")) { Spacer(Modifier.size(64.dp)) }
                    }
                }
            }
        }
    }
    private fun pixel(tag: String): Color {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        return pixels[(8 * density).toInt(), (24 * density).toInt()]
    }
    private fun close(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.015f)
        assertEquals(expected.green, actual.green, 0.015f)
        assertEquals(expected.blue, actual.blue, 0.015f)
        assertEquals(expected.alpha, actual.alpha, 0.015f)
    }
    private fun verify(dark: Boolean) {
        compose.waitForIdle()
        close(groupColor, pixel("closed"))
        close(groupColor, pixel("open"))
        close(deviceColor, pixel("device"))
        if (dark) close(pixel("event-reference"), pixel("device"))
    }
    @Test fun forcedDarkWithLightSystemKeepsGroupPrimaryContainer() {
        content(AppThemeMode.DARK, false); verify(true)
        assertNotEquals(groupColor, deviceColor)
    }
    @Test fun systemDarkKeepsGroupPrimaryContainer() {
        content(AppThemeMode.SYSTEM, true); verify(true)
    }
    @Test fun dynamicDarkUsesActivePrimaryContainer() {
        content(AppThemeMode.DARK, false, true); verify(true)
    }
    @Test fun forcedLightWithDarkSystemIsUnchanged() {
        content(AppThemeMode.LIGHT, true); verify(false)
    }
    @Test fun switchingThemeRestoresEachPalette() {
        content(AppThemeMode.DARK, false); verify(true)
        compose.runOnIdle { mode.value = AppThemeMode.LIGHT }; verify(false)
        compose.runOnIdle { mode.value = AppThemeMode.DARK }; verify(true)
    }
    @Test fun expandingKeepsGroupAndDeviceBackgrounds() {
        content(AppThemeMode.DARK, false); verify(true)
        compose.onNodeWithText("Grupo cerrado").performClick()
        compose.runOnIdle { assertTrue(expanded.value) }
        verify(true)
        compose.onNodeWithText("Grupo cerrado").performClick()
        compose.runOnIdle { assertFalse(expanded.value) }
        verify(true)
    }
}
