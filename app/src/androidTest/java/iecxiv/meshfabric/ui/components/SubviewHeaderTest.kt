package iecxiv.meshfabric.ui.components
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.window.DialogProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.ui.management.AccountOptionSurface
import iecxiv.meshfabric.ui.management.UserActionSurface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class SubviewHeaderTest {
    @get:Rule val rule=createComposeRule()
    @Test fun headerHasOneBackButtonAndRetainsActions() {
        var backs=0
        rule.setContent {MaterialTheme {SubviewHeader(onBack={backs++},title={Text("Details")},actions={Text("Options")})}}
        rule.onAllNodesWithTag("subview-back").assertCountEquals(1)
        rule.onNodeWithText("Details").assertIsDisplayed()
        rule.onNodeWithText("Options").assertIsDisplayed()
        rule.onNodeWithTag("subview-back").performClick()
        rule.runOnIdle {assertEquals(1,backs)}
    }
    @Test fun userWrapperDoesNotDuplicateTheBackButton() {
        rule.setContent {MaterialTheme {UserActionSurface(onDismissRequest={},inline=true,title={Text("User")},text={},confirmButton={})}}
        rule.onAllNodesWithTag("user-action-back").assertCountEquals(1)
        rule.onAllNodesWithTag("subview-header").assertCountEquals(1)
    }
    @Test fun accountOptionUsesTheSharedHeader() {
        rule.setContent {MaterialTheme {AccountOptionSurface(inline=true,onDismissRequest={},properties=DialogProperties(),title={Text("Account")},text={},confirmButton={})}}
        rule.onAllNodesWithTag("subview-back").assertCountEquals(1)
        rule.onNodeWithText("Account").assertIsDisplayed()
    }
}
