package iecxiv.meshfabric.ui.terminal

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TerminalKeyEditorTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.applicationContext.getSharedPreferences("terminal_key_bar", Context.MODE_PRIVATE)
    private fun desc(id: Int) = context.getString(id)
    private fun savedKeys() = prefs.getString("keys", "")!!.split(',')
    @Before fun clear() { prefs.edit().clear().commit() }
    private fun showBar() = compose.setContent { MaterialTheme { Box(Modifier.width(360.dp)) { TerminalKeyBar(true, true, {}, {}, {}) } } }
    private fun openEditor() { compose.onNodeWithTag("terminal-key-editor-button").performScrollTo().performClick() }

    @Test fun wrenchButtonIsTheLastControl() {
        showBar()
        compose.onNodeWithTag("terminal-wrench-icon", useUnmergedTree = true).assertExists()
        val wrench = compose.onNodeWithTag("terminal-key-editor-button").getUnclippedBoundsInRoot()
        val latest = compose.onNodeWithContentDescription(desc(R.string.terminal_latest)).getUnclippedBoundsInRoot()
        val enter = compose.onNodeWithContentDescription(desc(R.string.terminal_key_enter)).getUnclippedBoundsInRoot()
        assertTrue(wrench.left >= latest.right - 1.dp)
        assertTrue(latest.left > enter.left)
    }

    @Test fun editorRemovesAddsAndPersistsKeys() {
        showBar()
        openEditor()
        compose.onNodeWithTag("terminal-key-remove-esc").performClick()
        compose.onNodeWithTag("terminal-key-editor-save").performClick()
        compose.onNodeWithContentDescription(desc(R.string.terminal_key_esc)).assertDoesNotExist()
        assertFalse(savedKeys().contains("esc"))
        openEditor()
        compose.onNodeWithTag("terminal-key-add-esc").performScrollTo().performClick()
        compose.onNodeWithTag("terminal-key-editor-save").performClick()
        compose.onNodeWithContentDescription(desc(R.string.terminal_key_esc)).assertExists()
        assertEquals("esc", savedKeys().last())
    }

    @Test fun dragHandleReordersKeys() {
        showBar()
        openEditor()
        compose.onNodeWithTag("terminal-key-handle-esc").performTouchInput {
            down(center)
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, 60f))
            moveBy(Offset(0f, 90f))
            moveBy(Offset(0f, 120f))
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("terminal-key-editor-save").performClick()
        assertTrue(savedKeys().indexOf("esc") > 0)
    }

    @Test fun cancelKeepsStoredKeysUntouched() {
        showBar()
        openEditor()
        compose.onNodeWithTag("terminal-key-remove-esc").performClick()
        compose.onNodeWithText(if (java.util.Locale.getDefault().language == "es") "Cancelar" else "Cancel").performClick()
        compose.onNodeWithContentDescription(desc(R.string.terminal_key_esc)).assertExists()
        assertNull(prefs.getString("keys", null))
    }
}
