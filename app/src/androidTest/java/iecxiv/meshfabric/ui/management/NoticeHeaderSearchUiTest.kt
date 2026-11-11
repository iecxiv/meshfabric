package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
class NoticeHeaderSearchUiTest {
    @get:Rule val compose=createComposeRule()
    @Composable private fun Harness() {
        var state by remember {mutableStateOf(NoticeSearchState())}
        var devices by remember {mutableStateOf(false)}
        MaterialTheme {Column {
            NoticeSearchAction(state) {state=state.toggle()}
            TextButton(onClick={devices=!devices;state=state.cleared()},modifier=Modifier.testTag("switch-recipient-type")) {Text(if(devices) "Devices" else "Users")}
            NoticeSearchField(state) {state=state.edit(it)}
            val labels=if(devices) listOf("PC (Oficina)","PC (Casa)") else listOf("Ana","Luis")
            labels.filter {it.contains(state.query,true)}.forEach {Text(it)}
        }}
    }
    @Test fun hiddenUntilHeaderActionAndClosingRestoresList() {
        compose.setContent {Harness()}
        compose.onNodeWithTag("notice-search-input").assertDoesNotExist()
        compose.onNodeWithTag("notice-search-toggle").performClick()
        compose.onNodeWithTag("notice-search-input").performTextInput("Ana")
        compose.onNodeWithText("Luis").assertDoesNotExist()
        compose.onNodeWithTag("notice-search-toggle").performClick()
        compose.onNodeWithTag("notice-search-input").assertDoesNotExist()
        compose.onNodeWithText("Luis").assertIsDisplayed()
        compose.onNodeWithTag("notice-search-toggle").performClick()
        compose.onNodeWithTag("notice-search-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
    }
    @Test fun changingRecipientTypeResetsSearchAndDeviceGroupIsSearchable() {
        compose.setContent {Harness()}
        compose.onNodeWithTag("notice-search-toggle").performClick()
        compose.onNodeWithTag("notice-search-input").performTextInput("Ana")
        compose.onNodeWithTag("switch-recipient-type").performClick()
        compose.onNodeWithTag("notice-search-input").assertDoesNotExist()
        compose.onNodeWithText("PC (Casa)").assertIsDisplayed()
        compose.onNodeWithTag("notice-search-toggle").performClick()
        compose.onNodeWithTag("notice-search-input").performTextInput("Oficina")
        compose.onNodeWithText("PC (Oficina)").assertIsDisplayed()
        compose.onNodeWithText("PC (Casa)").assertDoesNotExist()
    }
}
