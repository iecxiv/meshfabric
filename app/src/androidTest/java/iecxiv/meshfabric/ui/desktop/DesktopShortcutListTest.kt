package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesktopShortcutListTest {
    @get:Rule val compose = createComposeRule()
    private val first = DesktopShortcut(listOf(17, 65))
    private val second = DesktopShortcut(listOf(17, 67))
    private val third = DesktopShortcut(listOf(17, 86))
    private var current by mutableStateOf(listOf(first, second, third))
    private val sent = mutableListOf<DesktopShortcut>()

    private fun show() = compose.setContent {
        MaterialTheme { Column { DesktopShortcutList(current, true, { sent.add(it) }, { current = it }) } }
    }

    @Test fun crossRemovesOnlyThatShortcut() {
        show()
        compose.onNodeWithTag("desktop-shortcut-remove-1").performClick()
        compose.runOnIdle { assertEquals(listOf(first, third), current) }
    }

    @Test fun buttonStillSendsItsKeys() {
        show()
        compose.onNodeWithTag("desktop-shortcut-button-2").performClick()
        compose.runOnIdle { assertEquals(listOf(third), sent) }
    }

    @Test fun dragHandleMovesTheShortcutAndSavesOnRelease() {
        show()
        val rowPx = 44f * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        compose.onNodeWithTag("desktop-shortcut-handle-0").performTouchInput {
            down(center)
            moveBy(Offset(0f, rowPx * 0.8f))
            moveBy(Offset(0f, rowPx * 0.8f))
            moveBy(Offset(0f, rowPx * 0.8f))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(second, third, first), current) }
    }
}
