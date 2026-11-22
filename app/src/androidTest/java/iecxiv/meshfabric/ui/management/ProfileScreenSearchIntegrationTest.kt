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
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class ProfileScreenSearchIntegrationTest {
    @get:Rule val compose=createComposeRule()
    private val client=MeshCentralClient()
    private val mounted=mutableStateOf(true)
    private lateinit var back:OnBackPressedDispatcher

    private fun mount(fontScale:Float=1f) {
        client.isUserLogout=true
        compose.setContent {
            val configuration=Configuration(LocalConfiguration.current).apply {setLocale(Locale.forLanguageTag("es"))}
            val density=Density(LocalDensity.current.density,fontScale)
            back=requireNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            CompositionLocalProvider(LocalConfiguration provides configuration,LocalDensity provides density) {
                MaterialTheme {
                    if(mounted.value) ProfileScreen(client,Modifier.fillMaxSize(),header={actions->Row {actions()}})
                }
            }
        }
        compose.waitForIdle()
    }
    private fun section(label:String) {
        compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
        compose.waitForIdle()
    }
    private fun toggle()=compose.onNodeWithTag("profile-directory-search-toggle")
    private fun input()=compose.onNodeWithTag("profile-directory-search-input")
    private fun query(expected:String) {
        assertEquals(expected,input().fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
    @After fun dispose() {
        try {
            compose.runOnIdle {mounted.value=false}
            compose.waitForIdle()
            val socket=MeshCentralClient::class.java.getDeclaredField("webSocket").apply {isAccessible=true}
            assertNull(socket.get(client))
        } finally {
            val scope=MeshCentralClient::class.java.getDeclaredField("scope").apply {isAccessible=true}
            (scope.get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun headerToggleOpensClosesAndClearsDirectorySearch() {
        mount()
        toggle().assertDoesNotExist()
        section("Grupos de dispositivos")
        input().assertDoesNotExist()
        toggle().assertIsDisplayed().performClick()
        input().assertIsDisplayed().performTextInput("outer-filter")
        query("outer-filter")
        toggle().performClick()
        input().assertDoesNotExist()
        toggle().performClick()
        input().assertIsDisplayed()
        query("")
    }
    @Test fun switchingEveryDirectorySectionClearsAndClosesSearch() {
        mount()
        for(label in listOf("Grupos de dispositivos","Usuarios","Grupos","Grupos de dispositivos")) {
            section(label)
            input().assertDoesNotExist()
            toggle().performClick()
            query("")
            input().performTextInput("filter-$label")
            query("filter-$label")
        }
        section("Mi cuenta")
        toggle().assertDoesNotExist()
        input().assertDoesNotExist()
        section("Usuarios")
        input().assertDoesNotExist()
        toggle().performClick()
        query("")
    }
    @Test fun backClosesAndClearsSearchWithoutLeavingSelectedSection() {
        mount()
        section("Usuarios")
        toggle().performClick()
        input().performTextInput("back-filter")
        query("back-filter")
        compose.runOnIdle {back.onBackPressed()}
        input().assertDoesNotExist()
        compose.onNode(hasText("Usuarios") and hasClickAction()).assertIsSelected()
        toggle().assertIsDisplayed().performClick()
        query("")
    }
    @Test fun largeFontSearchLimitsInputAndStillClosesFromHeader() {
        mount(1.8f)
        section("Grupos de dispositivos")
        toggle().assertIsDisplayed().performClick()
        val longQuery="a".repeat(300)
        input().assertIsDisplayed().performTextInput(longQuery)
        query(longQuery.take(256))
        toggle().assertIsDisplayed().performClick()
        input().assertDoesNotExist()
        toggle().performClick()
        query("")
    }
}
