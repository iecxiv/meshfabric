package iecxiv.meshfabric.ui.login

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FidoLoginControlsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun tlsBypassDisablesHardwareAction() {
        compose.setContent { MaterialTheme { FidoLoginControls(FidoLoginFlow.State(),false,true,{},{},{_,_->},{}) } }
        compose.onNodeWithTag("fido-start").assertIsNotEnabled(); compose.onNodeWithTag("fido-tls").assertExists()
    }
    @Test fun pendingIsExplicitAndCanBeCancelled() {
        var cancelled=0
        compose.setContent { MaterialTheme { FidoLoginControls(FidoLoginFlow.State(FidoLoginFlow.Phase.PENDING,1,"https://server.example"),false,false,{},{cancelled++},{_,_->},{}) } }
        compose.onNodeWithTag("fido-pending").assertExists(); compose.onNodeWithTag("fido-cancel").performClick()
        compose.runOnIdle { assertEquals(1,cancelled) }
    }
}
