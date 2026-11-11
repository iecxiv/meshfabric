package iecxiv.meshfabric.ui.management

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class NoticeCenterTabsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun navigateBetweenAllThreeSections() {
        compose.setContent {
            var selected by remember {mutableStateOf(NoticeSection.INBOX)}
            MaterialTheme {NoticeCenterTabs(selected) {selected=it}}
        }
        compose.onNodeWithTag("notice-tab-INBOX").assertIsSelected()
        compose.onNodeWithTag("notice-tab-BROADCAST").performClick().assertIsSelected()
        compose.onNodeWithTag("notice-tab-MESSAGES").performClick().assertIsSelected()
        compose.onNodeWithTag("notice-tab-INBOX").performClick().assertIsSelected()
    }
}
