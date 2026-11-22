package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DeviceGroupSearchTest {
    @get:Rule val rule=createComposeRule()
    @Test fun magnifierChangesScopeAndPreservesDirectoryFilter() {
        val group=mutableStateOf(false);val tab=mutableIntStateOf(0)
        val search=DeviceGroupSearchState();val outer=mutableStateOf("outer-filter")
        val nodes=listOf(JSONObject().put("_id","a").put("meshid","one").put("name","Alpha"),JSONObject().put("_id","b").put("meshid","two").put("name","Alpha other group"),JSONObject().put("_id","c").put("meshid","one").put("name","Beta"))
        rule.setContent {MaterialTheme {Column {
            val target=profileSearchTarget(1,group.value,tab.intValue,false,search.advanced)
            ProfileSearchAction(target,if(target==ProfileSearchTarget.GROUP_DEVICES) search.open else true) {if(target==ProfileSearchTarget.GROUP_DEVICES) search.toggle()}
            if(target==ProfileSearchTarget.DIRECTORY) OutlinedTextField(outer.value,{outer.value=it},modifier=Modifier.testTag("outer-search"))
            if(group.value) {
                DeviceGroupDetailsTabs(tab.intValue) {tab.intValue=it}
                if(tab.intValue==1) {
                    DeviceGroupSearchField(search)
                    deviceGroupSearchNodes(nodes,"one",search.query).orEmpty().forEach {Text(it.optString("name"),Modifier.testTag("result-"+it.optString("_id")))}
                }
            }
        }}}
        rule.onNodeWithTag("outer-search").assertTextContains("outer-filter")
        rule.runOnIdle {group.value=true}
        rule.onNodeWithTag("outer-search").assertDoesNotExist()
        rule.onNodeWithTag("profile-directory-search-toggle").assertDoesNotExist()
        rule.onNode(hasText("Devices") or hasText("Dispositivos")).performClick()
        rule.onNodeWithTag("device-group-search-input").assertDoesNotExist()
        rule.onNodeWithTag("profile-directory-search-toggle").performClick()
        rule.onNodeWithTag("device-group-search-input").performTextInput("alpha")
        rule.onNodeWithTag("result-a").assertIsDisplayed()
        rule.onNodeWithTag("result-b").assertDoesNotExist()
        rule.onNodeWithTag("result-c").assertDoesNotExist()
        rule.onNodeWithTag("profile-directory-search-toggle").performClick()
        rule.onNodeWithTag("device-group-search-input").assertDoesNotExist()
        rule.onNodeWithTag("result-c").assertIsDisplayed()
        rule.onNode(hasText("Access") or hasText("Accesos")).performClick()
        rule.onNodeWithTag("profile-directory-search-toggle").assertDoesNotExist()
        rule.runOnIdle {group.value=false;assertEquals("outer-filter",outer.value)}
        rule.onNodeWithTag("outer-search").assertTextContains("outer-filter")
    }
}
