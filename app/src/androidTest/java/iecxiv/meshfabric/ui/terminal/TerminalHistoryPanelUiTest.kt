package iecxiv.meshfabric.ui.terminal

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.TerminalConnectionStatus
import org.junit.Rule
import org.junit.Test

class TerminalHistoryPanelUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    @Composable private fun panel(visible: Boolean, expanded: Boolean, previous: Boolean = false, onExpanded: (Boolean) -> Unit = {}) {
        MaterialTheme {
            TerminalSessionPopup(status = TerminalConnectionStatus.DISCONNECTED, attempt = ManualReconnectState.IDLE,
                history = emptyList(), showingHistory = previous, truncated = false, expanded = expanded,
                deletionIds = emptySet(), onExpanded = onExpanded, onReconnect = {}, onCancel = {}, onCurrent = {},
                onSelect = {}, onRequestDelete = {}, onDismissDelete = {}, onDelete = {}, showBar = visible)
        }
    }
    @Test fun hiddenPanelDoesNotExposeItsActions() {
        compose.setContent { panel(false, true) }
        compose.onNodeWithText(text(R.string.terminal_reconnect_manual)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.terminal_history_empty)).assertDoesNotExist()
    }
    @Test fun expandedPanelDoesNotDuplicateReconnect() {
        compose.setContent { panel(true, true) }
        compose.onAllNodesWithText(text(R.string.terminal_reconnect_manual)).assertCountEquals(1)
    }
    @Test fun expandedPreviousHistoryDoesNotDuplicateReturn() {
        compose.setContent { panel(true, true, true) }
        compose.onAllNodesWithText(text(R.string.terminal_return_current)).assertCountEquals(1)
    }
    @Test fun onlyPanelArrowExpandsTheHistoryBody() {
        val expanded = mutableStateOf(false)
        compose.setContent { panel(true, expanded.value, onExpanded = { expanded.value = it }) }
        compose.onNodeWithText(text(R.string.terminal_history_empty)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.terminal_session_popup_title)).performClick()
        compose.onNodeWithText(text(R.string.terminal_history_empty)).assertIsDisplayed()
    }
}
