package iecxiv.meshfabric.ui.management
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
class RegistroSectionTabsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun bothTabsAreVisibleAndEventsCallbackUsesIndexOne() {
        var activity="";var events="";var selected=-1
        compose.setContent {MaterialTheme {
            activity=stringResource(R.string.registro_activity);events=stringResource(R.string.registro_events)
            RegistroSectionTabs(0) {selected=it}
        }}
        compose.onNodeWithText(activity).assertIsDisplayed()
        compose.onNodeWithText(events).assertIsDisplayed().performClick()
        compose.runOnIdle {assertEquals(1,selected)}
    }
    @Test fun activityCallbackUsesIndexZero() {
        var label="";var selected=-1
        compose.setContent {MaterialTheme {label=stringResource(R.string.registro_activity);RegistroSectionTabs(1) {selected=it}}}
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle {assertEquals(0,selected)}
    }
}
