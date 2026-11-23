package iecxiv.meshfabric.ui.desktop

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import org.junit.Rule
import org.junit.Test

class DesktopConsoleMessageTest {
    @get:Rule val compose = createComposeRule()
    @Test fun showsAgentTextAndRemovesItWhenCleared() {
        val message = mutableStateOf<String?>("Waiting for user to grant access...")
        compose.setContent { DesktopConsoleMessage(message.value) }
        compose.onNodeWithText("Waiting for user to grant access...").assertIsDisplayed()
        compose.runOnIdle { message.value = null }
        compose.waitForIdle()
        compose.onNodeWithTag("desktop-console-message").assertDoesNotExist()
    }
}
