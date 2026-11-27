package iecxiv.meshfabric.ui.terminal

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TerminalKeyBarTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.applicationContext.getSharedPreferences("terminal_key_bar", Context.MODE_PRIVATE)
    private fun desc(id: Int) = context.getString(id)
    @Before fun clearScroll() { prefs.edit().clear().commit() }

    @Test fun singleScrollableBarSendsEveryKeyOnce() {
        val sent = mutableListOf<String>()
        var latest = 0
        compose.setContent { MaterialTheme { Box(Modifier.width(360.dp)) { TerminalKeyBar(true, true, { sent.add(it) }, { latest++ }, {}) } } }
        compose.onNodeWithContentDescription(desc(R.string.terminal_key_enter))
            .assertHeightIsAtLeast(32.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription(desc(R.string.terminal_key_left)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(desc(R.string.terminal_ctrl_more)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.terminal_ctrl_shortcut, "A")).performClick()
        compose.onNodeWithContentDescription(desc(R.string.terminal_latest)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("enter", "left", "ctrl-a"), sent); assertEquals(1, latest) }
    }

    @Test fun noExpanderRemains() {
        compose.setContent { MaterialTheme { TerminalKeyBar(true, true, {}, {}, {}) } }
        compose.onNodeWithContentDescription(desc(R.string.terminal_keys_more)).assertDoesNotExist()
        compose.onNodeWithContentDescription(desc(R.string.terminal_keys_less)).assertDoesNotExist()
        compose.onNodeWithTag("terminal-chevron-0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun disconnectedKeysDisabledButHistoryAvailable() {
        compose.setContent { MaterialTheme { Box(Modifier.width(360.dp)) { TerminalKeyBar(false, true, {}, {}, {}) } } }
        compose.onNodeWithContentDescription(desc(R.string.terminal_key_enter)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(desc(R.string.terminal_latest)).performScrollTo().assertIsEnabled()
    }

    @Test fun arrowAndEnterIconsShareSizeAndEnterIsCentered() {
        compose.setContent { MaterialTheme { TerminalKeyBar(true, true, {}, {}, {}) } }
        for (rotation in listOf(0, 90, 180, 270)) {
            compose.onNodeWithTag("terminal-arrow-$rotation", useUnmergedTree = true)
                .assertWidthIsEqualTo(20.dp).assertHeightIsEqualTo(20.dp)
        }
        compose.onNodeWithTag("terminal-enter-icon", useUnmergedTree = true)
            .assertWidthIsEqualTo(20.dp).assertHeightIsEqualTo(20.dp)
        val chip = compose.onNodeWithContentDescription(desc(R.string.terminal_key_enter)).getUnclippedBoundsInRoot()
        val icon = compose.onNodeWithTag("terminal-enter-icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(((chip.left + chip.right) / 2).value, ((icon.left + icon.right) / 2).value, 1f)
        assertEquals(((chip.top + chip.bottom) / 2).value, ((icon.top + icon.bottom) / 2).value, 1f)
    }

    @Test fun largeLabelsAndHomeEndStylesAreConsistent() {
        compose.setContent { MaterialTheme { TerminalKeyBar(true, true, {}, {}, {}) } }
        val latestLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(context.getString(R.string.terminal_latest_glyph), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(latestLayouts) }
        assertEquals(20.sp, latestLayouts.single().layoutInput.style.fontSize)
        assertFalse(latestLayouts.single().didOverflowHeight)
        val homeLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        val endLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(context.getString(R.string.terminal_key_home), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(homeLayouts) }
        compose.onNodeWithText(context.getString(R.string.terminal_key_end), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(endLayouts) }
        val homeStyle = homeLayouts.single().layoutInput.style
        val endStyle = endLayouts.single().layoutInput.style
        assertEquals(homeStyle.fontSize, endStyle.fontSize)
        assertEquals(homeStyle.lineHeight, endStyle.lineHeight)
        assertEquals(homeStyle.fontWeight, endStyle.fontWeight)
        assertFalse(endLayouts.single().didOverflowHeight)
    }

    @Test fun increasedFontScaleKeepsLabelsUnclipped() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) { MaterialTheme { TerminalKeyBar(true, true, {}, {}, {}) } }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(context.getString(R.string.terminal_latest_glyph), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertFalse(layouts.single().didOverflowHeight)
        compose.onNodeWithContentDescription(desc(R.string.terminal_key_enter))
            .assertHeightIsAtLeast(32.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test fun horizontalScrollPositionIsStoredAndRestored() {
        val visible = mutableStateOf(true)
        compose.setContent { MaterialTheme { if (visible.value) Box(Modifier.width(240.dp)) { TerminalKeyBar(true, true, {}, {}, {}) } } }
        compose.onNodeWithContentDescription(desc(R.string.terminal_latest)).performScrollTo()
        compose.waitForIdle()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertTrue(prefs.getInt("scroll_x", 0) > 0)
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(desc(R.string.terminal_latest)).assertIsDisplayed()
        compose.onNodeWithContentDescription(desc(R.string.terminal_key_esc)).assertIsNotDisplayed()
    }
}
