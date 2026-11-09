package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ProfileCompactActionLayoutTest {
    @get:Rule val compose=createComposeRule()
    private fun alignment(scale:Float) {
        val checked=mutableStateOf(false)
        compose.setContent {val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {MaterialTheme {
                Column(Modifier.width(240.dp)) {ProfileAlignedCheckboxRow {
                    Checkbox(checked.value,{checked.value=it},modifier=Modifier.testTag("check"))
                    Text("Permission and restriction description with several lines",Modifier.weight(1f).testTag("label"))
                }}
            }}
        }
        val control=compose.onNodeWithTag("check").fetchSemanticsNode().boundsInRoot
        val text=compose.onNodeWithTag("label").fetchSemanticsNode().boundsInRoot
        assertEquals(text.center.y,control.center.y,1f)
        compose.onNodeWithTag("check").assertTouchHeightIsEqualTo(48.dp).assertTouchWidthIsEqualTo(48.dp).performTouchInput {click()}.assertIsOn()
    }
    @Test fun normalTextCheckboxAlignment()=alignment(1f)
    @Test fun enlargedMultilineCheckboxAlignment()=alignment(1.8f)
    @Test fun actionsHaveNoExtraGapsAndKeepTouchTargets() {
        var clicks=0
        compose.setContent {MaterialTheme {Column(Modifier.width(300.dp)) {ProfileActionCard {
            Text("Device group",Modifier.testTag("identity"))
            ProfileActionGroup {
                ProfileGroupActionButton("Review direct access",Icons.Default.Security,true,onClick={clicks++})
                ProfileGroupActionButton("Edit direct rights",Icons.Default.Security,false,onClick={error("Disabled action")})
            }
        }}}}
        val actions=compose.onAllNodesWithTag("profile-group-action")
        actions[0].assertHeightIsAtLeast(48.dp)
        actions[1].assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
        val identity=compose.onNodeWithTag("identity").fetchSemanticsNode().boundsInRoot
        val first=actions[0].fetchSemanticsNode().boundsInRoot
        val second=actions[1].fetchSemanticsNode().boundsInRoot
        assertEquals(identity.bottom,first.top,1f)
        assertEquals(first.bottom,second.top,1f)
        actions[0].performClick()
        compose.runOnIdle {assertEquals(1,clicks)}
    }
}
