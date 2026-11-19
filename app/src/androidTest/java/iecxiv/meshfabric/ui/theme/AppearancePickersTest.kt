package iecxiv.meshfabric.ui.theme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class AppearancePickersTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cityAnchorGrowsWithName() {
        var city by mutableStateOf(solarCities.first {it.id=="lima"})
        compose.setContent {MaterialTheme {Box(Modifier.fillMaxSize().testTag("picker_host")) {AppearanceCitySelector(city,280.dp,{city=it})}}}
        compose.waitForIdle()
        val small=compose.onNodeWithTag("appearance_city_selector").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle {city=solarCities.first {it.id=="canarias"}};compose.waitForIdle()
        val large=compose.onNodeWithTag("appearance_city_selector").fetchSemanticsNode().boundsInRoot.width
        assertTrue(large>small)
        assertTrue(large<compose.onNodeWithTag("picker_host").fetchSemanticsNode().boundsInRoot.width)
    }
    @Test fun citySearchStillSelectsTheCorrectId() {
        var selected:SolarCity?=null
        compose.setContent {MaterialTheme {AppearanceCitySelector(null,280.dp,{selected=it})}}
        compose.onNodeWithTag("appearance_city_selector").performClick()
        compose.onNodeWithTag("appearance_city_search").performTextInput("Lima")
        compose.onNodeWithTag("appearance_city_option_lima").performClick()
        compose.runOnIdle {assertEquals("lima",selected?.id)}
    }
    @Test fun clockCancelDoesNotSave() {
        var saved:Int?=null;var dismissed=false
        compose.setContent {MaterialTheme {AppearanceAnalogTimeDialog("Test",420,1200,true,{dismissed=true},{saved=it})}}
        compose.onNodeWithTag("appearance_analog_clock").assertExists()
        compose.onNodeWithTag("appearance_time_cancel").performClick()
        compose.runOnIdle {assertTrue(dismissed);assertNull(saved)}
    }
    @Test fun clockSaveReturnsMinutes() {
        var saved:Int?=null
        compose.setContent {MaterialTheme {AppearanceAnalogTimeDialog("Test",420,1200,true,{},{saved=it})}}
        compose.onNodeWithTag("appearance_time_save").performClick()
        compose.runOnIdle {assertEquals(420,saved)}
    }
    @Test fun equalHoursDisableSave() {
        compose.setContent {MaterialTheme {AppearanceAnalogTimeDialog("Test",420,420,false,{},{})}}
        compose.onNodeWithTag("appearance_time_save").assertIsNotEnabled()
    }
}
