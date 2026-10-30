package iecxiv.meshfabric.ui.terminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TerminalKeyBarTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun compactBarExpandsAndSendsOnce() {
        val sent = mutableListOf<String>()
        var latest = 0
        compose.setContent { MaterialTheme { TerminalKeyBar(true, true, { sent.add(it) }, { latest++ }, {}) } }
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_key_enter))
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(listOf("enter"), sent) }
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_key_left)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_keys_more)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_key_left)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_ctrl_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.terminal_ctrl_shortcut, "A")).performClick()
        compose.runOnIdle { assertEquals(listOf("enter", "ctrl-a"), sent) }
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_latest)).performClick()
        compose.runOnIdle { assertEquals(1, latest); assertEquals(2, sent.size) }
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_keys_less)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_key_left)).assertDoesNotExist()
    }
    @Test fun disconnectedKeysDisabledButHistoryAvailable() {
        compose.setContent { MaterialTheme { TerminalKeyBar(false, true, {}, {}, {}) } }
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_key_enter)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_keys_more)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_latest)).assertIsEnabled()
    }
    @Test fun arrowSizesAndLargeLabelsAreConsistent() {
        compose.setContent { MaterialTheme { TerminalKeyBar(true, true, {}, {}, {}) } }
        fun assertLargeText(resource: Int) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(context.getString(resource), useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
            assertEquals(20.sp, layouts.single().layoutInput.style.fontSize)
            assertFalse(layouts.single().didOverflowHeight)
        }
        assertLargeText(R.string.terminal_key_enter_glyph)
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_keys_more)).performClick()
        for (rotation in listOf(0, 90, 180, 270)) {
            compose.onNodeWithTag("terminal-arrow-$rotation", useUnmergedTree = true)
                .assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        }
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
        assertLargeText(R.string.terminal_latest_glyph)
    }

    @Test fun increasedFontScaleKeepsLabelsUnclipped() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)
            ) { MaterialTheme { TerminalKeyBar(true, true, {}, {}, {}) } }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(context.getString(R.string.terminal_key_enter_glyph), useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
        assertFalse(layouts.single().didOverflowHeight)
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_key_enter))
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test fun expanderUsesTheSameInvertedChevron() {
        compose.setContent { MaterialTheme { TerminalKeyBar(true, true, {}, {}, {}) } }
        compose.onNodeWithTag("terminal-chevron-0", useUnmergedTree = true)
            .assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_keys_more)).performClick()
        compose.onNodeWithTag("terminal-chevron-180", useUnmergedTree = true)
            .assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
        compose.onNodeWithTag("terminal-chevron-0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.terminal_keys_less)).performClick()
        compose.onNodeWithTag("terminal-chevron-0", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("terminal-chevron-180", useUnmergedTree = true).assertDoesNotExist()
    }

}
