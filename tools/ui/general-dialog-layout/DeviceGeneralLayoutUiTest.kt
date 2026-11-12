package iecxiv.meshfabric.ui.devices

import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.theme.LocalAppDarkTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceGeneralLayoutUiTest {
    @get:Rule val compose=createComposeRule()
    private val node=MeshNode(id="node//layout",name="Equipo de prueba",osdesc="Linux")
    private fun textLayout(target:SemanticsNodeInteraction):TextLayoutResult {
        val results=mutableListOf<TextLayoutResult>()
        target.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            assertTrue("Text layout must be available",action(results))
        }
        assertEquals("Exactly one text layout expected",1,results.size)
        return results.single()
    }
    private fun compare(dark:Boolean) {
        val details=mutableStateOf(true)
        compose.setContent { MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
            CompositionLocalProvider(LocalAppDarkTheme provides dark) {
                if(details.value) DeviceDetailsDialog(node) {} else DeviceGeneralDialog(node) {}
            }
        } }
        val reference=compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot.width
        val titleHeight=compose.onNodeWithText(node.name).fetchSemanticsNode().boundsInRoot.height
        val subtitle=InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.device_details_title)
        val referenceLayout=textLayout(compose.onNodeWithText(subtitle,useUnmergedTree=true))
        compose.runOnIdle { details.value=false }
        assertEquals(reference,compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot.width,1f)
        assertEquals(titleHeight,compose.onNodeWithText(node.name).fetchSemanticsNode().boundsInRoot.height,1f)
        val general=compose.onNodeWithTag("device_general_subtitle",useUnmergedTree=true)
        val generalLayout=textLayout(general)
        assertEquals("Subtitle text style must match Details",referenceLayout.layoutInput.style,generalLayout.layoutInput.style)
        assertEquals("Subtitle density must match Details",referenceLayout.layoutInput.density,generalLayout.layoutInput.density)
        assertFalse("Details subtitle must not overflow vertically",referenceLayout.didOverflowHeight)
        assertFalse("General subtitle must not overflow vertically",generalLayout.didOverflowHeight)
        general.assertTextEquals("General").assertIsDisplayed()
        compose.onNodeWithTag("general_save").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("device_general_close").assertIsDisplayed()
    }
    @Test fun lightMatchesDetails()=compare(false)
    @Test fun darkMatchesDetails()=compare(true)
    @Test fun compactWindowKeepsHeightBoundAndFooter() {
        var density=1f
        compose.setContent {
            val compact=Configuration(LocalConfiguration.current).apply { screenHeightDp=480;screenWidthDp=320 }
            density=LocalDensity.current.density
            CompositionLocalProvider(LocalConfiguration provides compact) { MaterialTheme { DeviceGeneralDialog(node) {} } }
        }
        val bounds=compose.onNodeWithTag("device_general_surface").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height<=480f*0.88f*density+2f)
        compose.onNodeWithTag("general_save").assertIsDisplayed()
        compose.onNodeWithTag("device_general_close").assertIsDisplayed()
    }
    @Test fun headerCloseUsesDismissCallback() {
        var closed=0
        compose.setContent { MaterialTheme { DeviceGeneralDialog(node) { closed++ } } }
        compose.onNodeWithTag("device_general_close").performClick()
        compose.runOnIdle { assertEquals(1,closed) }
    }
}
