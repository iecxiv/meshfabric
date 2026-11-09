package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
@RunWith(AndroidJUnit4::class) class ProfileUserListIdentityTest {
    @get:Rule val compose=createComposeRule()
    private val uid="user//alice"
    private fun person(id:String=uid)=JSONObject().put("_id",id).put("name","Alicia").put("realname","Alicia García").put("email","alice@example.invalid")
    private fun show(accounts:List<JSONObject>,language:String="es") {
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale(language))}
            CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {Column {for(account in accounts) ProfileUserListIdentity(account,accounts)}}}
        }
    }
    @Test fun readableIdentityAndIconHideIdentifierByDefault() {
        show(listOf(person()))
        compose.onNodeWithText("Alicia · Alicia García · alice@example.invalid").assertExists()
        compose.onAllNodesWithTag("profile-user-list-icon",useUnmergedTree=true).assertCountEquals(1)
        compose.onAllNodesWithText(uid,substring=true).assertCountEquals(0)
        compose.onNodeWithText("Identificador").performClick()
        compose.onNodeWithText(uid).assertExists()
    }
    @Test fun missingNameNeverUsesIdentifierAsTitle() {
        show(listOf(person().put("name","")))
        compose.onNodeWithText("nombre no disponible").assertExists()
        compose.onAllNodesWithText(uid,substring=true).assertCountEquals(0)
    }
    @Test fun internalIdentifierCannotMasqueradeAsReadableName() {
        show(listOf(person().put("name",uid)))
        compose.onNodeWithText("nombre no disponible").assertExists()
        compose.onAllNodesWithText(uid,substring=true).assertCountEquals(0)
    }
    @Test fun duplicateNamesKeepDistinctReadableContextWithoutExposingIds() {
        val other=person("user//other").put("realname","Alicia López").put("email","other@example.invalid")
        show(listOf(person(),other))
        compose.onNodeWithText("Alicia · Alicia García · alice@example.invalid").assertExists()
        compose.onNodeWithText("Alicia · Alicia López · other@example.invalid").assertExists()
        for(id in listOf(uid,"user//other")) compose.onAllNodesWithText(id,substring=true).assertCountEquals(0)
    }
    @Test fun englishFallbackAndExplicitIdentifierRemainLocalized() {
        show(listOf(person().put("name","")),"en")
        compose.onNodeWithText("name unavailable").assertExists()
        compose.onAllNodesWithText(uid,substring=true).assertCountEquals(0)
        compose.onNodeWithText("Identifier").performClick()
        compose.onNodeWithText(uid).assertExists()
    }
}
