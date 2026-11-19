package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
class DeviceBatchResponsiveDialogTest {
    @get:Rule val compose=createComposeRule()
    private fun render(rows:()->Int) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("batch_test_host")) {
                    DeviceBatchAlertDialog(onDismissRequest={},title={DeviceBatchDialogTitle("Test")},
                        text={Column(Modifier.fillMaxWidth()) { repeat(rows()) { index -> Text("Row $index",Modifier.padding(vertical=6.dp).testTag("batch_row_$index")) } }},
                        confirmButton={TextButton(onClick={},modifier=Modifier.testTag("batch_test_confirm")) {Text("Confirm")}},
                        dismissButton={TextButton(onClick={},modifier=Modifier.testTag("batch_test_close")) {Text("Close")}})
                }
            }
        }
        compose.waitForIdle()
    }
    private fun scrollLimit():Float = compose.onNodeWithTag("device_batch_dialog_body",useUnmergedTree=true).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
    @Test fun shortContentDoesNotNeedScroll() {
        render { 1 }
        assertEquals(0f,scrollLimit(),1f)
        compose.onNodeWithTag("batch_test_confirm").assertIsDisplayed()
        compose.onNodeWithTag("batch_test_close").assertIsDisplayed()
    }
    @Test fun longContentScrollsButButtonsStayVisible() {
        render { 80 }
        assertTrue(scrollLimit()>0f)
        compose.onNodeWithTag("batch_test_confirm").assertIsDisplayed()
        compose.onNodeWithTag("batch_row_79").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("batch_test_close").assertIsDisplayed()
        compose.onNodeWithTag("batch_test_confirm").assertIsDisplayed()
    }
    @Test fun heightGrowsWithContent() {
        var count by mutableIntStateOf(1)
        render { count }
        val short=compose.onNodeWithTag("device_batch_dialog").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { count=3 };compose.waitForIdle()
        val taller=compose.onNodeWithTag("device_batch_dialog").fetchSemanticsNode().boundsInRoot.height
        assertTrue(taller>short)
    }
    @Test fun widthRespectsScreenAndTabletLimit() {
        render { 1 }
        val host=compose.onNodeWithTag("batch_test_host").fetchSemanticsNode().boundsInRoot.width
        val width=compose.onNodeWithTag("device_batch_dialog").fetchSemanticsNode().boundsInRoot.width
        val density=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue(width<=640f*density+1f)
        assertTrue(width<=host-24f*density+1f)
    }
}
