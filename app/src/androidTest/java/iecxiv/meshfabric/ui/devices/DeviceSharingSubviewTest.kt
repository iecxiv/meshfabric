package iecxiv.meshfabric.ui.devices
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import iecxiv.meshfabric.data.model.MeshNode
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DeviceSharingSubviewTest {
    @get:Rule val rule=createComposeRule()
    @Test fun formSelectorsAndDatesStayInline() {
        val f=mutableStateOf(ShareForm())
        rule.setContent {MaterialTheme {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {DeviceSharingForm(f.value,0xffffffffL,ZoneOffset.UTC,true) {f.value=it}}}}
        rule.onNodeWithTag("share_access").performScrollTo().performClick()
        rule.onNodeWithTag("share_access_5").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(ShareAccess.TERMINAL_FILES,f.value.access)}
        rule.onNodeWithTag("share_validity").performScrollTo().performClick()
        rule.onNodeWithTag("share_validity_1").performScrollTo().performClick()
        rule.onNodeWithTag("share_start").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("share_end").performScrollTo().assertIsDisplayed()
        rule.onNode(isDialog()).assertDoesNotExist()
    }
    @Test fun unlimitedIsSelectableWithoutDialog() {
        val f=mutableStateOf(ShareForm())
        rule.setContent {MaterialTheme {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {DeviceSharingForm(f.value,0xffffffffL,ZoneOffset.UTC,true) {f.value=it}}}}
        rule.onNodeWithTag("share_expiry").performScrollTo().performClick()
        rule.onNodeWithTag("share_expiry_0").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(0,f.value.expiry)}
        rule.onNode(isDialog()).assertDoesNotExist()
    }
    @Test fun generalInlineFrameHasSharedBackAndNoDialog() {
        var backs=0
        rule.setContent {MaterialTheme {DeviceGeneralFrame(MeshNode(id="node//test",name="Device"),{backs++},body={Text("Body")},footer={Text("Footer")},inline=true)}}
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onNodeWithTag("device_general_close").performClick()
        rule.runOnIdle {assertEquals(1,backs)}
    }
    @Test fun offlineSharingListUsesSubviewAndDisablesCreation() {
        var backs=0
        rule.setContent {MaterialTheme {DeviceSharingListScreen(MeshNode(id="node//test",name="Device"),onBack={backs++},onCreate={error("Offline creation")})}}
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onNodeWithTag("general_share_create").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("device_shares_close").performClick()
        rule.runOnIdle {assertEquals(1,backs)}
    }
    @Test fun reviewBackAndDiscardDoNotOpenDialogsOrSendRequests() {
        var backs=0
        rule.setContent {MaterialTheme {DeviceSharingCreateScreen(MeshNode(id="node//test",name="Device",meshid="mesh//a"),0xffffffffL,onBack={backs++},onCreated={error("No create expected")})}}
        rule.onNodeWithTag("share_guest").performScrollTo().performTextInput("Guest")
        rule.onNodeWithTag("general_share_create_confirm").performClick()
        rule.onNodeWithTag("share_guest").assertDoesNotExist()
        rule.onNodeWithTag("general_share_create_confirm").assertIsNotEnabled()
        rule.onNodeWithTag("device_share_create_close").performClick()
        rule.onNodeWithTag("share_guest").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("device_share_create_close").performClick()
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onAllNodesWithTag("subview-header").assertCountEquals(1)
        rule.onNodeWithTag("user-action-back").performClick()
        rule.onNodeWithTag("share_guest").performScrollTo().assertIsDisplayed()
        rule.runOnIdle {assertEquals(0,backs)}
    }

}
