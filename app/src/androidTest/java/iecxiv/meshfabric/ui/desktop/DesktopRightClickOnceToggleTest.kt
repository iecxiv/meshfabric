package iecxiv.meshfabric.ui.desktop

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DesktopRightClickOnceToggleTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun cleanPreferences() {
        context.getSharedPreferences("desktop_right_click_assist", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun badgeFollowsTheToggleAndIsRemembered() {
        val state = DesktopRightClickOnceState(context)
        compose.setContent { MaterialTheme { DesktopRightClickOnceToggle(state) } }
        compose.onNodeWithTag("desktop-right-click-once-badge", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("desktop-right-click-once-toggle").performClick()
        compose.onNodeWithTag("desktop-right-click-once-badge", useUnmergedTree = true).assertExists()
        assertTrue(DesktopRightClickOnceState(context).enabled)
        compose.onNodeWithTag("desktop-right-click-once-toggle").performClick()
        compose.onNodeWithTag("desktop-right-click-once-badge", useUnmergedTree = true).assertDoesNotExist()
        assertFalse(DesktopRightClickOnceState(context).enabled)
    }
}
