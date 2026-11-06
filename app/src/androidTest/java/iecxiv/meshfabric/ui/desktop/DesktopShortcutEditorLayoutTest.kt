package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopShortcutEditorLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun content(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    var modifiers by remember { mutableStateOf<Set<Int>>(emptySet()) }
                    var selected by remember { mutableStateOf<Int?>(null) }
                    var expanded by remember { mutableStateOf(false) }
                    Box(Modifier.width(240.dp)) {
                        DesktopShortcutEditorControls(modifiers, selected, expanded,
                            onModifierChange = { key, checked -> modifiers = if (checked) modifiers + key else modifiers - key },
                            onExpandedChange = { expanded = it },
                            onSelected = { selected = it; expanded = false })
                    }
                }
            }
        }
    }
    private fun assertPickerFits() {
        val row = compose.onNodeWithTag("shortcut-editor-row").fetchSemanticsNode().boundsInRoot
        val picker = compose.onNodeWithTag("shortcut-key-picker").fetchSemanticsNode().boundsInRoot
        val modifiers = compose.onNodeWithTag("shortcut-modifier-viewport").fetchSemanticsNode().boundsInRoot
        assertTrue("Selector extends past row", picker.right <= row.right + 1f && picker.left >= row.left - 1f)
        assertEquals(row.width * 0.5f, picker.width, 1f)
        assertTrue("Modifier viewport must retain width", modifiers.width > 0f)
        assertTrue(modifiers.right <= picker.left)
        assertEquals(row.center.y, picker.center.y, 1f)
    }
    @Test fun narrowRowKeepsWholeSelectorVisible() {
        content(1f)
        assertPickerFits()
        compose.onNodeWithText("Ninguna tecla").assertIsDisplayed()
    }
    @Test fun largeFontKeepsSelectorVisibleAndLabelUnclipped() {
        content(2f)
        assertPickerFits()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Ninguna tecla", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertFalse(results.single().hasVisualOverflow)
    }
    @Test fun dropdownOpensFromSelectorAndSelectionUpdatesIt() {
        content(1f)
        compose.onNodeWithText("Ninguna tecla").performClick()
        compose.onNodeWithText("Backspace").assertIsDisplayed().performClick()
        compose.onNodeWithText("Backspace").assertIsDisplayed()
        compose.onAllNodesWithText("Ninguna tecla").assertCountEquals(0)
        assertPickerFits()
    }
}
