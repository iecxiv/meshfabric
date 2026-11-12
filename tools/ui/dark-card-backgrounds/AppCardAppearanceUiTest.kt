package iecxiv.meshfabric.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.ui.management.AccountActionsCard
import iecxiv.meshfabric.ui.management.ManagementCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppCardAppearanceUiTest {
    @get:Rule val compose = createComposeRule()
    private var reference = Color.Unspecified
    private var original = Color.Unspecified
    private var inherited = Color.Unspecified
    private var inheritedDisabled = Color.Unspecified
    private var localDark = false

    @Composable private fun Cards() {
        reference = CardDefaults.cardColors().containerColor
        original = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        localDark = LocalAppDarkTheme.current
        val source = CardDefaults.cardColors(containerColor = original,
            contentColor = Color.Green, disabledContentColor = Color.Magenta)
        val resolved = appCardColors(source)
        inherited = resolved.contentColor
        inheritedDisabled = resolved.disabledContentColor
        assertEquals(if (localDark) reference else original, resolved.containerColor)
        assertEquals(if (localDark) reference else source.disabledContainerColor, resolved.disabledContainerColor)
        Column(Modifier.width(240.dp)) {
            Card(Modifier.size(64.dp).testTag("event-reference")) { Spacer(Modifier.size(64.dp)) }
            Card(Modifier.size(64.dp).testTag("native-card"), colors = resolved) { Spacer(Modifier.size(64.dp)) }
            Surface(Modifier.size(64.dp).testTag("surface-card"), color = appCardBackground(MaterialTheme.colorScheme.surface)) {}
            ManagementCard(Modifier.testTag("management-card")) { Spacer(Modifier.height(32.dp)) }
            Box(Modifier.testTag("account-card")) { AccountActionsCard { Spacer(Modifier.height(32.dp)) } }
        }
    }
    private fun pixel(tag: String): Color {
        val pixels = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
    private fun close(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.012f)
        assertEquals(expected.green, actual.green, 0.012f)
        assertEquals(expected.blue, actual.blue, 0.012f)
    }
    private fun verifyDark() {
        compose.waitForIdle()
        assertTrue(localDark)
        listOf("native-card", "surface-card", "management-card", "account-card").forEach {
            close(pixel("event-reference"), pixel(it))
        }
        assertEquals(Color.Green, inherited)
        assertEquals(Color.Magenta, inheritedDisabled)
    }
    private fun content(mode: AppThemeMode, systemDark: Boolean, dynamic: Boolean = false) {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MeshCentralTheme(themeMode = mode, dynamicColor = dynamic) { Cards() }
            }
        }
    }
    @Test fun forcedDarkWithLightSystemUsesEventPixels() {
        content(AppThemeMode.DARK, false); verifyDark()
    }
    @Test fun darkSystemModeUsesEventPixels() {
        content(AppThemeMode.SYSTEM, true); verifyDark()
    }
    @Test fun dynamicDarkUsesItsOwnEventReference() {
        content(AppThemeMode.DARK, false, true); verifyDark()
    }
    @Test fun forcedLightWithDarkSystemPreservesOriginals() {
        content(AppThemeMode.LIGHT, true)
        compose.waitForIdle()
        assertFalse(localDark)
        assertEquals(Color.Green, inherited)
        assertEquals(Color.Magenta, inheritedDisabled)
    }
    @Test fun lightSystemPreservesManagementSurface() {
        var surface = Color.Unspecified
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MeshCentralTheme(themeMode = AppThemeMode.SYSTEM) {
                    surface = MaterialTheme.colorScheme.surface
                    Cards()
                }
            }
        }
        compose.waitForIdle()
        assertFalse(localDark)
        close(surface, pixel("management-card"))
        close(surface, pixel("account-card"))
    }
    @Test fun switchingThemeRecomputesCardBackgrounds() {
        val mode = mutableStateOf(AppThemeMode.LIGHT)
        compose.setContent { MeshCentralTheme(themeMode = mode.value) { Cards() } }
        compose.waitForIdle(); assertFalse(localDark)
        compose.runOnIdle { mode.value = AppThemeMode.DARK }
        verifyDark()
        compose.runOnIdle { mode.value = AppThemeMode.LIGHT }
        compose.waitForIdle(); assertFalse(localDark)
    }
    @Test fun surfaceElevationDoesNotTintDarkReference() {
        var value = 9.dp
        compose.setContent { MeshCentralTheme(themeMode = AppThemeMode.DARK) { value = appCardTonalElevation(9.dp) } }
        compose.waitForIdle(); assertEquals(0.dp, value)
    }
    @Test fun lightSurfaceElevationIsUnchanged() {
        var value = 0.dp
        compose.setContent { MeshCentralTheme(themeMode = AppThemeMode.LIGHT) { value = appCardTonalElevation(9.dp) } }
        compose.waitForIdle(); assertEquals(9.dp, value)
    }
}
