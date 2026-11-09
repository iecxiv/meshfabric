package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
@RunWith(AndroidJUnit4::class) class ManagementNavigationLabelTest {
    @get:Rule val compose=createComposeRule()
    private fun label(language:String,label:String,old:String) {
        var selected=-1
        compose.setContent {
            val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale(language))}
            CompositionLocalProvider(LocalConfiguration provides config) {
                MaterialTheme {ProfileNavigationBar(3) {selected=it}}
            }
        }
        compose.onNodeWithText(old).assertDoesNotExist()
        compose.onNodeWithText(label).assertIsDisplayed().assertIsSelected().performClick()
        compose.runOnIdle {assertEquals(3,selected);assertEquals(listOf(3,2,0,1,4),bottomNavigationPageOrder)}
    }
    @Test fun spanishManagementKeepsDestination()=label("es","Gestión","Perfil")
    @Test fun englishManagementKeepsDestination()=label("en","Management","Profile")
}
