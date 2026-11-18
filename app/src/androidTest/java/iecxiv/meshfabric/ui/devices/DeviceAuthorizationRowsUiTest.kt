package iecxiv.meshfabric.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test

class DeviceAuthorizationRowsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun directAndInheritedOriginsAreVisible() {
        val rows=listOf(GeneralAuthorizationRow("user//alice","Alice",8,false),GeneralAuthorizationRow("user//alice","Alice",16,true))
        compose.setContent {MaterialTheme {Column {GeneralAuthorizationRows(rows,true)}}}
        compose.onNodeWithText("Directo al dispositivo").assertIsDisplayed()
        compose.onNodeWithText("Heredado del grupo de dispositivos").assertIsDisplayed()
        compose.onAllNodesWithTag("general_authorization_row").assertCountEquals(2)
    }
    @Test fun missingRightsAreExplicitlyUnverified() {
        compose.setContent {MaterialTheme {Column {GeneralAuthorizationRows(listOf(GeneralAuthorizationRow("user//alice","Alice",null,false)),true)}}}
        compose.onNodeWithText("Derechos no verificables en este enlace").assertIsDisplayed()
    }
}
