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
    @Test fun cityAnchorKeepsStableWidth() {
        var city by mutableStateOf(solarCities.first {it.id=="lima"})
        compose.setContent {MaterialTheme {Box(Modifier.fillMaxSize().testTag("picker_host")) {AppearanceCitySelector(city,260.dp,{city=it})}}}
        compose.waitForIdle()
        compose.onNodeWithTag("appearance_city_selector").assertWidthIsEqualTo(260.dp)
        val small=compose.onNodeWithTag("appearance_city_selector").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle {city=solarCities.first {it.id=="canarias"}};compose.waitForIdle()
        val large=compose.onNodeWithTag("appearance_city_selector").fetchSemanticsNode().boundsInRoot.width
        assertEquals(small,large,1f)
        assertTrue(large<compose.onNodeWithTag("picker_host").fetchSemanticsNode().boundsInRoot.width)
        compose.onNodeWithTag("appearance_city_selector").performClick()
        val menu=compose.onNodeWithTag("appearance_city_menu").fetchSemanticsNode().boundsInRoot.width
        assertEquals(large,menu,1f)
    }
    @Test fun citySearchStillSelectsTheCorrectId() {
        var selected:SolarCity?=null
        compose.setContent {MaterialTheme {AppearanceCitySelector(null,260.dp,{selected=it})}}
        compose.onNodeWithTag("appearance_city_selector").performClick()
        compose.onNodeWithTag("appearance_city_search").performTextInput("Lima")
        compose.onNodeWithTag("appearance_city_option_lima").performClick()
        compose.runOnIdle {assertEquals("lima",selected?.id)}
    }
    @Test fun cityArrowDisappearsAfterSelection() {
            var city by mutableStateOf<SolarCity?>(null)
            compose.setContent {MaterialTheme {AppearanceCitySelector(city,260.dp,{city=it})}}
            compose.onNodeWithTag("appearance_city_dropdown_arrow",useUnmergedTree=true).assertExists()
            compose.onNodeWithTag("appearance_city_selector").performClick()
            compose.onNodeWithTag("appearance_city_search").performTextInput("Lima")
            compose.onNodeWithTag("appearance_city_option_lima").performClick()
            compose.onNodeWithTag("appearance_city_dropdown_arrow",useUnmergedTree=true).assertDoesNotExist()
            compose.onNodeWithTag("appearance_city_selector").assertWidthIsEqualTo(260.dp).performClick()
            compose.onNodeWithTag("appearance_city_menu").assertExists()
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
