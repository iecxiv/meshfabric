package iecxiv.meshfabric.ui.management

import android.content.res.Configuration
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import com.google.gson.Gson
import iecxiv.meshfabric.data.model.UserInfo
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class ProfileGroupSearchIntegrationTest {
    @get:Rule val compose=createComposeRule()
    private val client=MeshCentralClient().apply {isUserLogout=true}
    private val session=ProfileSession(client)
    private val factory:(MeshCentralClient)->ProfileSession={assertSame(client,it);session}
    private val mounted=mutableStateOf(true)
    private lateinit var back:OnBackPressedDispatcher
    private val actor="user//fixture-reader"
    private fun field(name:String)=MeshCentralClient::class.java.getDeclaredField(name).apply {isAccessible=true}
    private fun group(id:String,name:String)=JSONObject().put("_id",id).put("name",name).put("mtype",2).put("links",JSONObject())
    private fun node(id:String,mesh:String,name:String)=JSONObject().put("_id",id).put("meshid",mesh).put("name",name)
    private fun mount() {
        val user=JSONObject().put("_id",actor).put("name","fixture-reader").put("siteadmin",0)
        @Suppress("UNCHECKED_CAST")
        val actorFlow=field("_userInfo").get(client) as MutableStateFlow<UserInfo?>
        actorFlow.value=Gson().fromJson(user.toString(),UserInfo::class.java)
        assertEquals(actor,client.userInfo.value?.id)
        compose.setContent {
            val configuration=Configuration(LocalConfiguration.current).apply {setLocale(Locale.forLanguageTag("es"))}
            val density=Density(LocalDensity.current.density,1f)
            back=requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CompositionLocalProvider(LocalConfiguration provides configuration,LocalDensity provides density) {MaterialTheme {
                if(mounted.value) ProfileScreen(client,Modifier.fillMaxSize(),sessionFactory=factory,header={actions->Row {actions()}})
            }}
        }
        compose.waitForIdle()
        compose.runOnIdle {
            session.state.value=ProfileState(ready=true,user=user,notice="ready",groups=listOf(group("mesh//one","Directory One"),group("mesh//two","Directory Two")),nodes=listOf(
                node("node//one-alpha","mesh//one","Alpha in group one"),node("node//one-beta","mesh//one","Beta in group one"),
                node("node//two-alpha","mesh//two","Alpha in group two"),node("node//two-beta","mesh//two","Beta in group two")))
        }
        tab("Grupos de dispositivos")
        toggle().performClick()
    }
    private fun tab(label:String) {compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()}
    private fun toggle()=compose.onNodeWithTag("profile-directory-search-toggle")
    private fun directory()=compose.onNodeWithTag("profile-directory-search-input")
    private fun devices()=compose.onNodeWithTag("device-group-search-input")
    private fun query(input:SemanticsNodeInteraction,expected:String) {assertEquals(expected,input.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)}
    private fun open(name:String) {
        directory().performTextReplacement(name)
        query(directory(),name)
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("Abrir").performScrollTo().performClick()
        directory().assertDoesNotExist()
        toggle().assertDoesNotExist()
        tab("Dispositivos")
    }
    private fun leave() {compose.onNodeWithTag("user-action-back").performClick()}
    @After fun dispose() {
        try {
            compose.runOnIdle {mounted.value=false}
            compose.waitForIdle()
            assertFalse(client.connectionState.value)
            assertTrue(client.authCookie.isEmpty())
            assertNull(field("webSocket").get(client))
        } finally {
            session.close()
            (field("scope").get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun scopedResultsAndTwoBackPressesPreserveDirectoryFilter() {
        mount();open("Directory One")
        devices().assertDoesNotExist()
        toggle().performClick();devices().performTextInput("Alpha")
        compose.onNodeWithText("Alpha in group one").assertExists()
        for(name in listOf("Beta in group one","Alpha in group two","Beta in group two")) compose.onNodeWithText(name).assertDoesNotExist()
        compose.runOnIdle {back.onBackPressed()}
        devices().assertDoesNotExist();directory().assertDoesNotExist()
        compose.onNodeWithText("Alpha in group one").assertExists()
        compose.onNodeWithText("Beta in group one").assertExists()
        compose.onNodeWithText("Alpha in group two").assertDoesNotExist()
        compose.runOnIdle {back.onBackPressed()}
        query(directory(),"Directory One")
        compose.onNodeWithText("Directory Two").assertDoesNotExist()
        compose.onNodeWithTag("user-action-back").assertDoesNotExist()
    }
    @Test fun eachGroupRetainsItsOwnQueryAcrossDirectoryRoundTrips() {
        mount();open("Directory One")
        toggle().performClick();devices().performTextInput("Alpha");leave()
        query(directory(),"Directory One")
        open("Directory Two");devices().assertDoesNotExist()
        toggle().performClick();devices().performTextInput("Beta")
        compose.onNodeWithText("Beta in group two").assertExists()
        compose.onNodeWithText("Alpha in group two").assertDoesNotExist()
        compose.onNodeWithText("Beta in group one").assertDoesNotExist()
        leave();query(directory(),"Directory Two")
        open("Directory One");query(devices(),"Alpha")
        compose.onNodeWithText("Alpha in group one").assertExists()
        compose.onNodeWithText("Beta in group one").assertDoesNotExist()
        leave();open("Directory Two");query(devices(),"Beta")
        compose.onNodeWithText("Beta in group two").assertExists()
        toggle().performClick();devices().assertDoesNotExist()
        leave();query(directory(),"Directory Two")
    }
    @Test fun groupTabsHideSearchOutsideDevicesWithoutLosingItsQuery() {
        mount();open("Directory One")
        toggle().performClick();devices().performTextInput("Alpha")
        tab("Accesos");toggle().assertDoesNotExist();devices().assertDoesNotExist();directory().assertDoesNotExist()
        tab("Resumen");toggle().assertDoesNotExist();devices().assertDoesNotExist()
        tab("Dispositivos");query(devices(),"Alpha")
        compose.onNodeWithText("Alpha in group one").assertExists()
        compose.onNodeWithText("Beta in group one").assertDoesNotExist()
        leave();query(directory(),"Directory One")
    }
}
