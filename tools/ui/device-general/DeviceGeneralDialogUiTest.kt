package iecxiv.meshfabric.ui.devices
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Rule
import org.junit.Test
class DeviceGeneralDialogUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun disconnectedCannotSave() {
        compose.setContent { MaterialTheme { DeviceGeneralDialog(MeshNode(id="node//x",name="Equipo")) {} } }
        compose.onNodeWithTag("general_save").assertIsNotEnabled()
        compose.onNodeWithTag("general_name").assertDoesNotExist()
        compose.onNodeWithText("Equipo").assertIsDisplayed()
    }
}
