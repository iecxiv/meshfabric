package iecxiv.meshfabric.ui.management
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test
class ActivityRecognitionTest {
    private fun row(id:String="node//n",name:String="")=ActivityEntry("e",id,name,"Evento","",ActivityStatus.UNKNOWN,"Sin confirmar",requestId="request",kind=ActivityKind.SERVER,action="runcommands")
    private val inventory=serverEventInventory(listOf(MeshGroup(id="mesh//g",name="Grupo") to listOf(MeshNode(id="node//n",name="Equipo",meshid="mesh//g"))))
    @Test fun resolvesCanonicalDeviceAndGroup() { assertEquals("Equipo (Grupo)",resolveActivityEntry(row(),inventory).target) }
    @Test fun resolvesBareNodeUsingSharedPolicy() { assertEquals("Equipo (Grupo)",resolveActivityEntry(row("n"),inventory).target) }
    @Test fun neverCrossesDomains() { assertEquals("Equipo no identificado",resolveActivityEntry(row("node/other/n"),inventory).target) }
    @Test fun preservesExplicitEventName() { assertEquals("Nombre recibido",resolveActivityEntry(row(name="Nombre recibido"),inventory).deviceName) }
    @Test fun preservesUnknownOutcomeAndRequest() {
        val value=resolveActivityEntry(row(),inventory)
        assertEquals(ActivityStatus.UNKNOWN,value.status);assertEquals("request",value.requestId);assertEquals("Sin confirmar",value.summary)
    }
    @Test fun knownAndUnknownTitlesStayDistinct() {
        assertEquals("Comando remoto",activityEventTitle("runcommands"));assertEquals("Evento del servidor",activityEventTitle("future-action"))
    }
    @Test fun actionAndMessageIconsStayDistinct() {
        assertEquals("runcommands",activityIconKey(row()));assertEquals("message",activityIconKey(row().copy(action="msg")))
    }
    @Test fun noIdCannotAcquireDeviceByNameOrTime() {
        val value=resolveActivityEntry(row(id=""),inventory)
        assertEquals("",value.deviceName);assertEquals("Servidor",value.target)
    }
}
