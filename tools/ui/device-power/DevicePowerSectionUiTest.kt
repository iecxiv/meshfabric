package iecxiv.meshfabric.ui.devices
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
class DevicePowerSectionUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun noSourceCannotExport() {
        compose.setContent { MaterialTheme { DevicePowerSection("node//x") } }
        compose.onNodeWithTag("power_heading").assertIsDisplayed()
        compose.onNodeWithTag("power_csv").assertIsNotEnabled()
    }
}
