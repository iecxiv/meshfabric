package iecxiv.meshfabric.ui.management
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import iecxiv.meshfabric.data.remote.MeshCentralClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
@RunWith(AndroidJUnit4::class) class UserGroupsActionsCardTest {
    @get:Rule val compose=createComposeRule()
    @Test fun serverActionsAreGroupedAndOldHeadingIsAbsent() {
        val client=MeshCentralClient();val session=ProfileSession(client)
        try {
            compose.setContent {
                val config=Configuration(LocalConfiguration.current).apply {setLocale(Locale("es"))}
                CompositionLocalProvider(LocalConfiguration provides config) {MaterialTheme {
                    UserGroupsDirectoryPane(session,ProfileState(),onOpenUser={},onOpenDeviceGroup={})
                }}
            }
            compose.onNodeWithText("Grupos de usuarios").assertDoesNotExist()
            compose.onNodeWithText("Grupos del dominio actual; son distintos de los grupos de dispositivos.").assertDoesNotExist()
            compose.onNodeWithTag("user-groups-server-actions").assertExists()
            val heading=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(iecxiv.meshfabric.R.string.my_server_actions)
            compose.onNodeWithText(heading).assertExists()
            compose.onNode(hasText(heading) and hasAnyAncestor(hasTestTag("user-groups-server-actions"))).assertDoesNotExist()
            val headingBounds=compose.onNodeWithText(heading).fetchSemanticsNode().boundsInRoot
            val actionsBounds=compose.onNodeWithTag("user-groups-server-actions").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("Heading must precede action controls",headingBounds.bottom<=actionsBounds.top)
            compose.onNode(hasText("Reconectar") and hasAnyAncestor(hasTestTag("user-groups-server-actions"))).assertExists()
            compose.onNode(hasText("Crear grupo de usuarios") and hasAnyAncestor(hasTestTag("user-groups-server-actions"))).assertIsNotEnabled()
            compose.onNodeWithText("Buscar grupo").assertExists()
        } finally {
            session.close()
            val field=client.javaClass.getDeclaredField("scope").apply {isAccessible=true}
            (field.get(client) as CoroutineScope).cancel()
        }
    }
}
