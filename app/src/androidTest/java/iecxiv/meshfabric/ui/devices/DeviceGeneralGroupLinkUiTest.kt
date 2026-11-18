package iecxiv.meshfabric.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.data.model.MeshGroup
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceGeneralGroupLinkUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun receivedGroupIsAnEnabledLink() {
        var opened=0
        val target=GeneralGroupTarget(MeshGroup(id="mesh//a",name="Grupo A"),emptyList())
        compose.setContent {MaterialTheme {GeneralGroupLinkRow(target,true) {opened++}}}
        compose.onNodeWithTag("general_group_link").assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(1,opened)}
    }
    @Test fun unavailableGroupDoesNotOpen() {
        compose.setContent {MaterialTheme {GeneralGroupLinkRow(null,true) {fail("Unavailable group must not open")}}}
        compose.onNodeWithTag("general_group_link").assertIsNotEnabled()
        compose.onNodeWithText("Grupo no disponible").assertIsDisplayed()
    }
}
