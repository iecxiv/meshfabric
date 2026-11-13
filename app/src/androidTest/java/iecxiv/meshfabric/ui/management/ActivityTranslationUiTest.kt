package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
class ActivityTranslationUiTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    @After fun close() { scope.cancel() }
    private fun check(language:String,dark:Boolean) {
        val journal=ActivityJournal({""},{""})
        val feedback=RemoteActionFeedback(scope,{false},{_,_->null})
        compose.setContent {
            val configuration=Configuration(LocalConfiguration.current).apply { setLocale(java.util.Locale.forLanguageTag(language)) }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme(colorScheme=if(dark) darkColorScheme() else lightColorScheme()) {
                    ActivityTimeline(journal,feedback,Modifier.fillMaxSize())
                }
            }
        }
        val es=language=="es"
        compose.onNodeWithText(if(es) "Mis acciones" else "My actions").assertIsDisplayed()
        compose.onNodeWithText(if(es) "Todos los dispositivos" else "All devices").assertIsDisplayed()
        compose.onNodeWithText(if(es) "No hay actividad que coincida con los filtros." else "No activity matches the filters.").assertIsDisplayed()
        compose.onNodeWithText(if(es) "My actions" else "Mis acciones").assertDoesNotExist()
    }
    @Test fun englishLight()=check("en",false)
    @Test fun englishDark()=check("en",true)
    @Test fun spanishLight()=check("es",false)
    @Test fun spanishDark()=check("es",true)
}
