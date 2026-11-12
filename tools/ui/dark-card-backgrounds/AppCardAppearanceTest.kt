package iecxiv.meshfabric.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class AppCardAppearanceTest {
    @Test fun lightKeepsOriginalIncludingAlpha() {
        val original = Color(0x4F12ABCD)
        assertEquals(original, resolveAppCardBackground(false, original, Color.Red))
    }
    @Test fun darkUsesEventReference() {
        assertEquals(Color.Blue, resolveAppCardBackground(true, Color.Red, Color.Blue))
    }
    @Test fun darkDoesNotReuseTheOriginalAlpha() {
        assertEquals(Color.Blue, resolveAppCardBackground(true, Color.Red.copy(alpha = 0.2f), Color.Blue))
    }
    @Test fun forcedDarkIgnoresLightSystem() { assertTrue(resolveAppDarkTheme(AppThemeMode.DARK, false)) }
    @Test fun forcedLightIgnoresDarkSystem() { assertFalse(resolveAppDarkTheme(AppThemeMode.LIGHT, true)) }
    @Test fun systemFollowsDark() { assertTrue(resolveAppDarkTheme(AppThemeMode.SYSTEM, true)) }
    @Test fun systemFollowsLight() { assertFalse(resolveAppDarkTheme(AppThemeMode.SYSTEM, false)) }
}
