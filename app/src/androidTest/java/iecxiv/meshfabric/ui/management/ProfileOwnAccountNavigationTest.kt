package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
class ProfileOwnAccountNavigationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun twoSeparateOwnSecurityActions() {
        var password=0;var mfa=0
        compose.setContent {MaterialTheme {ProfileOwnAccountActions(true,{password++},{mfa++})}}
        val actions=compose.onAllNodesWithTag("profile-group-action")
        actions.assertCountEquals(2)
        actions[0].performClick();actions[1].performClick()
        compose.runOnIdle {assertEquals(1,password);assertEquals(1,mfa)}
    }
    @Test fun administrativeActionsHiddenOnlyForSelf() {
        val target=mutableStateOf("user/a/me")
        compose.setContent {MaterialTheme {Column {ProfileOtherUserActions("user/a/me",target.value) {
            Text("Send message");Text("Lock user");Text("Delete user")
        }}}}
        listOf("Send message","Lock user","Delete user").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        compose.runOnIdle {target.value="user/a/other"}
        listOf("Send message","Lock user","Delete user").forEach {compose.onNodeWithText(it).assertIsDisplayed()}
    }
    @Test fun passwordLaunchWaitsAndIsConsumedOnce() {
        val ready=mutableStateOf(false)
        val request=mutableStateOf<ProfileOwnAccountRequest?>(ProfileOwnAccountRequest("user/a/me","server","password"))
        var opens=0;var mode=-1
        compose.setContent {ProfileOwnAccountLaunch(request.value,"user/a/me","server",ready.value,false,{opens++;mode=it},{request.value=null})}
        compose.runOnIdle {assertEquals(0,opens);ready.value=true}
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(1,opens);assertEquals(7,mode);ready.value=false}
        compose.runOnIdle {ready.value=true}
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(1,opens)}
    }
    @Test fun staleLaunchIsDiscarded() {
        val request=mutableStateOf<ProfileOwnAccountRequest?>(ProfileOwnAccountRequest("user/a/me","old-server","mfa"))
        var opens=0
        compose.setContent {ProfileOwnAccountLaunch(request.value,"user/a/me","new-server",true,false,{opens++},{request.value=null})}
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(0,opens);assertEquals(null,request.value)}
    }
}
