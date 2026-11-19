package iecxiv.meshfabric.ui.theme

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class AppearanceSelectorLayoutTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun clearPreferences() { context.getSharedPreferences("automatic_appearance",Context.MODE_PRIVATE).edit().clear().commit() }
    @Before fun setup() { clearPreferences() }
    @After fun cleanup() { clearPreferences() }
    private fun render() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("appearance_test_root")) { AppearanceModeSettings(AppThemeMode.SYSTEM,{}) }
            }
        }
        compose.waitForIdle()
    }
    private fun choose(index:Int) {
        compose.onNodeWithTag("appearance_mode_selector").performClick()
        compose.onNodeWithTag("appearance_mode_option_$index").performClick()
        compose.waitForIdle()
    }
    private fun compact(tag:String) {
        val root=compose.onNodeWithTag("appearance_test_root").fetchSemanticsNode().boundsInRoot
        val bounds=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width<root.width)
        assertTrue(bounds.width<=280f*context.resources.displayMetrics.density+1f)
        assertEquals(root.center.x,bounds.center.x,1f)
    }
    @Test fun selectorIsCompactAndCentered() {
        render();compact("appearance_mode_selector")
    }
    @Test fun menuIconsAndLabelsHaveStableColumns() {
        render();compose.onNodeWithTag("appearance_mode_selector").performClick()
        val firstIcon=compose.onNodeWithTag("appearance_mode_icon_0",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val firstLabel=compose.onNodeWithTag("appearance_mode_label_0",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        for(index in 1..4) {
            val icon=compose.onNodeWithTag("appearance_mode_icon_$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val label=compose.onNodeWithTag("appearance_mode_label_$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertEquals(firstIcon.left,icon.left,1f);assertEquals(firstIcon.width,icon.width,1f)
            assertEquals(firstLabel.left,label.left,1f)
        }
    }
    @Test fun scheduledControlsStayCompact() {
        render();choose(3);compact("appearance_light_time");compact("appearance_dark_time")
    }
    @Test fun solarCityControlStaysCompact() {
        render();choose(4);compact("appearance_city_selector")
    }
    @Test fun menuReportsSelectedModeWithoutChangingIndex() {
        render();choose(3)
        compose.onNodeWithTag("appearance_mode_selector").performClick()
        compose.onNodeWithTag("appearance_mode_option_3").assertIsSelected()
        compose.onNodeWithTag("appearance_mode_option_0").assertIsNotSelected()
    }

}
