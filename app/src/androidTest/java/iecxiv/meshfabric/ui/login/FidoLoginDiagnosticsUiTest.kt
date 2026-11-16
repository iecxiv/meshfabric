package iecxiv.meshfabric.ui.login

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.FidoLoginFailure
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FidoLoginDiagnosticsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun failureShowsFixedDiagnosticCode() {
        compose.setContent { MaterialTheme {
            FidoLoginControls(FidoLoginFlow.State(phase=FidoLoginFlow.Phase.FAILED,failure=FidoLoginFailure.Code.ASSERTION),false,false,{},{},{_,_->},{_ ->})
        } }
        compose.onNodeWithText("FIDO-201",substring=true).assertExists()
    }
    @Test fun verificationShowsPendingMessageNotAuthenticatorHost() {
        compose.setContent { MaterialTheme {
            FidoLoginControls(FidoLoginFlow.State(phase=FidoLoginFlow.Phase.VERIFYING,id=1,server="https://server.example"),false,false,{},{},{_,_->},{_ ->})
        } }
        compose.onNodeWithTag("fido-pending").assertExists()
        compose.onNodeWithText("FIDO-201",substring=true).assertDoesNotExist()
        compose.onNodeWithTag("fido-cancel").assertExists()
    }
}
