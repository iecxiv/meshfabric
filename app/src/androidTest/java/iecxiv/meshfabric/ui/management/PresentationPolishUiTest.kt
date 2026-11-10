package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class PresentationPolishUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun cardUsesSpaceAndDetailsShowTheUnadornedName() {
        val row=ServerEventRecord("manual","entry","operator","node//x","surface",1L,meshId="mesh//x",groupName="agent")
        compose.setContent {MaterialTheme {ServerEventsPanel(ServerEventsSnapshot(ServerEventsPhase.READY,listOf(row)),"",{},true,{})}}
        compose.onNodeWithTag("log-event-device-0",useUnmergedTree=true).assertTextEquals("surface (agent)")
        compose.onNodeWithTag("log-event-row-0").performClick();compose.onNodeWithTag("log-event-details-device").assertTextEquals("surface");compose.onNodeWithText("agent").assertExists()
    }
    @Test fun coloredEntryKeepsTheReferenceRowGeometry() {
        val event=JSONObject().put("m",107)
        compose.setContent {MaterialTheme {Row {
            Column(Modifier.weight(1f).testTag("reference"),verticalArrangement=Arrangement.spacedBy(10.dp)) {Text("date",style=MaterialTheme.typography.titleSmall);Text(loginHistoryLabel(107,false)+" · details",style=MaterialTheme.typography.bodySmall);HorizontalDivider()}
            Box(Modifier.weight(1f)) {PreviousLoginEntry(event,"date","details",false,Modifier.testTag("colored"))}
        }}}
        assertEquals(compose.onNodeWithTag("reference").fetchSemanticsNode().size.height,compose.onNodeWithTag("colored").fetchSemanticsNode().size.height)
    }
    @Test fun loginHistoryColorsMatchAccountInBothThemes() {
        val neutral=Color.Gray;val foreground=Color.White
        assertEquals(Color(0xFFE2F4E7),previousLoginPalette(107,false,neutral,foreground).first)
        assertEquals(Color(0xFF173B25),previousLoginPalette(107,true,neutral,foreground).first)
        for(code in listOf(108,109,110)) {assertEquals(Color(0xFFFCE5E7),previousLoginPalette(code,false,neutral,foreground).first);assertEquals(Color(0xFF4A2025),previousLoginPalette(code,true,neutral,foreground).first)}
        assertEquals(neutral to foreground,previousLoginPalette(999,false,neutral,foreground));assertEquals(neutral to foreground,previousLoginPalette(null,true,neutral,foreground))
    }
    @Test fun ownHistoryDialogUsesTheReferenceTitleIdentityAndRows() {
        compose.setContent {MaterialTheme {OwnPreviousLoginsDialog("Alice","user//alice",JSONArray().put(JSONObject().put("m",107).put("t",1700000000L)),false,false,false,{},{})}}
        compose.onNodeWithTag("own-previous-logins-dialog").assertIsDisplayed();compose.onNodeWithText("Previous logins").assertExists();compose.onNodeWithText("Alice").assertExists();compose.onNodeWithText("Back").assertExists();compose.onNodeWithText("Close").assertExists()
        compose.onNodeWithText("Successful sign-in",substring=true).assertExists()
    }
    @Test fun statisticsRefreshTracksOnlyItsTabAndCurrentPeriod() {
        val tab=mutableIntStateOf(0);val period=mutableIntStateOf(3);val busy=mutableStateOf(false);val ready=mutableStateOf(true);val requests=mutableListOf<Int>()
        compose.setContent {MaterialTheme {ServerStatisticsHeader(tab.intValue,ready.value,busy.value,header={actions->Row {actions()}},onRefresh={requests.add(period.intValue)})}}
        compose.onNodeWithTag("server-statistics-refresh").assertDoesNotExist()
        compose.runOnIdle {tab.intValue=1;period.intValue=24};compose.onNodeWithTag("server-statistics-refresh").performClick();compose.runOnIdle {assertEquals(listOf(24),requests);busy.value=true}
        compose.onNodeWithTag("server-statistics-refresh").assertIsNotEnabled();compose.runOnIdle {busy.value=false;ready.value=false};compose.onNodeWithTag("server-statistics-refresh").assertIsNotEnabled()
        for(index in listOf(0,2,3)) {compose.runOnIdle {tab.intValue=index};compose.onNodeWithTag("server-statistics-refresh").assertDoesNotExist()}
    }
}
