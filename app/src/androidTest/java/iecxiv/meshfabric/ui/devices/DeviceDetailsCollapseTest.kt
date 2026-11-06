package iecxiv.meshfabric.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Rule
import org.junit.Test

class DeviceDetailsCollapseTest {
    @get:Rule val compose = createComposeRule()
    private val node = MeshNode(id = "node//collapse-test", name = "Test device", meshid = "mesh//collapse-test", osdesc = "Test OS")

    private fun open() {
        compose.setContent { MaterialTheme { DeviceDetailsDialog(node, onDismiss = {}) } }
        compose.waitForIdle()
    }

    @Test fun identifiersAreCollapsedInitiallyAndCanBeExpandedAndCollapsed() {
        open()
        compose.onNodeWithText(node.id).assertDoesNotExist()
        val heading = compose.onNode(hasText("Identificación") or hasText("Identification"))
        heading.performScrollTo().performClick()
        compose.onNodeWithText(node.id).performScrollTo().assertIsDisplayed()
        heading.performScrollTo().performClick()
        compose.onNodeWithText(node.id).assertDoesNotExist()
    }

    @Test fun menuCanExpandAndCollapseAll() {
        open()
        val options = compose.onNode(hasContentDescription("Opciones de visualización") or hasContentDescription("Display options"))
        options.performClick()
        compose.onNode(hasText("Expandir todo") or hasText("Expand all")).performClick()
        compose.onNodeWithText(node.id).performScrollTo().assertIsDisplayed()
        options.performClick()
        compose.onNode(hasText("Contraer todo") or hasText("Collapse all")).performClick()
        compose.onNodeWithText(node.id).assertDoesNotExist()
    }
}
