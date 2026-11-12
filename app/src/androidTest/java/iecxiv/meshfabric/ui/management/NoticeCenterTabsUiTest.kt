package iecxiv.meshfabric.ui.management

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
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
        compose.onNode(hasText("Avisos") or hasText("Notices")).assertIsSelected()
        compose.onNode(hasText("Broadcast")).performClick().assertIsSelected()
        compose.onNode(hasText("Mensajes") or hasText("Messages")).performClick().assertIsSelected()
        compose.onNode(hasText("Avisos") or hasText("Notices")).performClick().assertIsSelected()
    }

    @Test fun recipientNamesUseReadableLabelsOnly() {
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    NoticeRecipientName(noticeUserRecipientLabel("Ana",true))
                    NoticeRecipientName(noticeDeviceRecipientLabel("Portatil","Oficina",true))
                }
            }
        }
        compose.onNodeWithText("Ana").assertIsDisplayed()
        compose.onNodeWithText("Portatil (Oficina)").assertIsDisplayed()
        compose.onNodeWithText("user//ana").assertDoesNotExist()
        compose.onNodeWithText("node//portatil").assertDoesNotExist()
    }
}
