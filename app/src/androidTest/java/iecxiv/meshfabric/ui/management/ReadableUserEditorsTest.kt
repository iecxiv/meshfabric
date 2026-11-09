package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
@RunWith(AndroidJUnit4::class) class ReadableUserEditorsTest {
    @get:Rule val compose=createComposeRule()
    private val uid="user//person"
    private fun field(target:Any,name:String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private inner class Fixture(val mode:String,missingName:Boolean=false):AutoCloseable {
        val client=MeshCentralClient();val session=ProfileSession(client)
        val account=JSONObject().put("_id",uid).put("name",if(missingName) "" else "Alicia").put("email","alice@example.invalid").put("phone","+34911111111").put("quota",4096L).put("siteadmin",0)
        val baseline=ProfileState(ready=true,user=JSONObject().put("_id","user//admin").put("siteadmin",0xffffffffL),users=listOf(account),server=JSONObject().put("emailcheck",mode!="verification").put("features",0x02000000L))
        val state=mutableStateOf(baseline)
        init {
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    if(mode=="advanced") UserAdvancedEditor(session,state.value,account,onDismiss={},onSaved={})
                    else UserContactEditor(session,state.value,account,onDismiss={},onSaved={},initialField=if(mode=="verification") "emailVerified" else "phone")
                }}
            }
        }
        fun ambiguous():ProfileState=baseline.copy(users=listOf(account,JSONObject(account.toString()).put("_id","user//other")))
        fun assertNoId() {compose.onAllNodesWithText(uid,substring=true).assertCountEquals(0)}
        override fun close() {
            assertNull(field(client,"webSocket").get(client));assertNull(field(session,"socket").get(session))
            session.close();(field(client,"scope").get(client) as CoroutineScope).cancel()
        }
    }
    @Test fun contactUsesReadableIdentityAndRequiresFreshConsentAfterAmbiguity() {Fixture("contact").use {f->
        f.assertNoId()
        compose.onNodeWithText("Alicia · alice@example.invalid").assertExists()
        compose.onNodeWithTag("user-contact-value").performScrollTo().performTextReplacement("+34612345678")
        compose.onNodeWithTag("user-contact-send").assertIsNotEnabled()
        compose.onNodeWithTag("user-contact-consent").performScrollTo().performClick()
        compose.onNodeWithTag("user-contact-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.ambiguous()}
        compose.onNodeWithTag("user-contact-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag("user-contact-consent").assertIsOff()
        compose.onNodeWithTag("user-contact-send").assertIsNotEnabled()
        compose.onNodeWithTag("user-contact-consent").performScrollTo().performClick()
        compose.onNodeWithTag("user-contact-send").assertIsEnabled()
    }}
    @Test fun missingContactNameBlocksInputsWithoutIdFallback() {Fixture("contact",true).use {f->
        f.assertNoId()
        compose.onNodeWithText("nombre no disponible").assertExists()
        compose.onNodeWithTag("user-contact-value").assertIsNotEnabled()
        compose.onNodeWithTag("user-contact-send").assertIsNotEnabled()
    }}
    @Test fun advancedConsentChangeKeepsIdentityAndRequiresFreshApproval() {Fixture("advanced").use {f->
        f.assertNoId()
        compose.onNodeWithText("Alicia · alice@example.invalid").assertExists()
        compose.onNodeWithText("Consentimiento del usuario").performScrollTo().performClick()
        compose.onNodeWithTag("user-advanced-check-Avisar: escritorio").performScrollTo().performClick()
        val consent="user-advanced-check-Confirmo aplicar los cambios a este usuario."
        compose.onNodeWithTag(consent).performScrollTo().performClick()
        compose.onNodeWithTag("user-advanced-send").assertIsEnabled()
        compose.runOnIdle {f.state.value=f.ambiguous()}
        compose.onNodeWithTag("user-advanced-send").assertIsNotEnabled()
        compose.runOnIdle {f.state.value=f.baseline}
        compose.onNodeWithTag(consent).assertIsOff()
        compose.onNodeWithTag("user-advanced-send").assertIsNotEnabled()
        compose.onNodeWithTag(consent).performScrollTo().performClick()
        compose.onNodeWithTag("user-advanced-send").assertIsEnabled()
        f.assertNoId()
    }}
    @Test fun verificationRemainsUnavailableWithoutServerCapability() {Fixture("verification").use {f->
        f.assertNoId()
        compose.onNodeWithTag("user-contact-send").assertIsNotEnabled()
        compose.onNodeWithText("Esta opción ya no está disponible",substring=true).assertExists()
    }}
}
