package iecxiv.meshfabric.ui.management

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerConsoleWebViewTest {
    @get:Rule val compose=createComposeRule()

    @Test fun localWebViewConsumesInitialAndUpdatedSnapshotsWithoutServer() {
        val output=mutableStateOf(listOf("LOCAL_VISOR_TEST_A"))
        compose.setContent { MaterialTheme { ServerConsoleTerminal(output.value,onClear={ output.value=emptyList() }) } }
        compose.waitUntil(timeoutMillis=15000) {
            compose.onAllNodes(hasText("written#1",substring=true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("written#1",substring=true).assertExists()
        compose.runOnIdle { output.value=listOf("LOCAL_VISOR_TEST_A","LOCAL_VISOR_TEST_B") }
        compose.waitUntil(timeoutMillis=10000) {
            compose.onAllNodes(hasText("written#2",substring=true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("written#2",substring=true).assertExists()
    }
}
