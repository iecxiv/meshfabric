package iecxiv.meshfabric.ui.management
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.theme.AppThemeMode
import iecxiv.meshfabric.ui.theme.MeshCentralTheme
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class ActivityRecognitionUiTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    @After fun closeScope() { scope.cancel() }
    private fun show(journal:ActivityJournal) {
        val feedback=RemoteActionFeedback(scope,{false},{_,_->null})
        compose.setContent { MeshCentralTheme(AppThemeMode.DARK) { ActivityTimeline(journal,feedback,Modifier.fillMaxSize()) } }
    }
    @Test fun connectionGetsSpecificTitleAndIcon() {
        val journal=ActivityJournal({""},{""})
        journal.event(JSONObject().put("action","nodeconnect").put("nodename","Equipo"))
        show(journal);compose.onNodeWithText("Equipo conectado").assertExists();compose.onNodeWithTag("activity-icon-connection").assertExists()
    }
    @Test fun numericMessageRemainsVisible() {
        val journal=ActivityJournal({""},{""})
        journal.event(JSONObject().put("action","agentlog").put("msg",1234))
        show(journal);compose.onNodeWithText("1234").assertExists()
    }
    @Test fun mismatchedNestedDeviceIsNotAdopted() {
        val journal=ActivityJournal({""},{""})
        journal.event(JSONObject().put("action","changenode").put("nodeid","node//a").put("node",JSONObject().put("_id","node//b").put("name","Incorrecto")))
        assertEquals("",journal.entries.value.single().deviceName)
        show(journal);compose.onNodeWithText("Equipo no identificado").assertExists();compose.onNodeWithText("Incorrecto").assertDoesNotExist()
    }
    @Test fun inventoryRefreshResolvesEarlierUnknownRow() {
        var inventory=ServerEventInventory()
        val journal=ActivityJournal({""},{""},{inventory})
        journal.event(JSONObject().put("action","changenode").put("nodeid","node//a"))
        show(journal);compose.onNodeWithText("Equipo no identificado").assertExists()
        compose.runOnIdle {
            inventory=serverEventInventory(listOf(MeshGroup(id="mesh//g",name="Grupo") to listOf(MeshNode(id="node//a",name="Equipo",meshid="mesh//g"))))
            journal.rememberDevices(mapOf("node//a" to "Equipo"))
        }
        compose.onNodeWithText("Equipo (Grupo)").assertExists()
        assertEquals(ActivityStatus.INFO,journal.entries.value.single().status)
    }
    @Test fun commandIconDoesNotConfirmExecution() {
        val journal=ActivityJournal({"Equipo"},{"Operador"})
        journal.sent("Ejecutar comando",JSONObject().put("action","runcommands").put("nodeid","node//a").put("responseid","r"),true)
        show(journal);compose.onNodeWithTag("activity-icon-runcommands").assertExists();compose.onNodeWithText("Solicitud enviada").assertExists()
        assertEquals(ActivityStatus.SENT,journal.entries.value.single().status)
    }
    @Test fun unknownActionKeepsGenericPresentation() {
        val journal=ActivityJournal({""},{""})
        journal.event(JSONObject().put("action","future-action").put("msg",JSONObject().put("x",1)))
        show(journal);compose.onNodeWithText("Evento del servidor").assertExists();compose.onNodeWithTag("activity-icon-unknown").assertExists()
        compose.onNodeWithText("El servidor no incluyó una descripción adicional.").assertExists()
    }
}
