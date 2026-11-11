package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
class ManagementConnectionSpacingTest {
    @get:Rule val compose=createComposeRule()
    private fun verify(scale:Float) {
        val ready=mutableStateOf(true);var density=1f
        compose.setContent {
            val original=LocalDensity.current
            density=original.density
            CompositionLocalProvider(LocalDensity provides Density(original.density,scale)) {MaterialTheme {
                Column(Modifier.width(240.dp).testTag("section-root")) {
                    ManagementConnectionStatus(ready.value,false,modifier=Modifier.padding(horizontal=16.dp),onReconnect={})
                    Column(Modifier.padding(managementConnectionContentPadding)) {Box(Modifier.fillMaxWidth().height(24.dp).testTag("first-content"))}
                }
            }}
        }
        fun verifyBounds():Float {
            val root=compose.onNodeWithTag("section-root").fetchSemanticsNode().boundsInRoot
            val row=compose.onNodeWithTag("management-connection-row").fetchSemanticsNode().boundsInRoot
            val content=compose.onNodeWithTag("first-content").fetchSemanticsNode().boundsInRoot
            assertEquals(root.top,row.top,0.5f)
            assertEquals(0f,content.top-row.bottom,0.5f)
            assertEquals(16f*density,content.left-root.left,0.5f)
            return content.top
        }
        verifyBounds()
        compose.runOnIdle {ready.value=false}
        compose.onNodeWithTag("management-reconnect").assertHeightIsAtLeast(48.dp)
        verifyBounds()
    }
    @Test fun compactOuterGapsStayStable()=verify(1f)
    @Test fun largeTextPreservesGapAndReconnectTarget()=verify(1.8f)
}
