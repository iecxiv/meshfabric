package iecxiv.meshfabric.ui.management
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test
@org.junit.runner.RunWith(org.junit.runners.Parameterized::class)
class ActivityRecognitionTest(private val language:String) {
    companion object {
        @JvmStatic
        @org.junit.runners.Parameterized.Parameters(name="{0}")
        fun languages():Collection<Array<String>> = listOf(arrayOf("es"),arrayOf("en"))
    }
    private lateinit var previousLocale:java.util.Locale
    @org.junit.Before fun selectLanguage() {
        previousLocale=java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(language))
    }
    @org.junit.After fun restoreLanguage() { java.util.Locale.setDefault(previousLocale) }

    private fun row(id:String="node//n",name:String="")=ActivityEntry("e",id,name,"Evento","",ActivityStatus.UNKNOWN,"Sin confirmar",requestId="request",kind=ActivityKind.SERVER,action="runcommands")
    private val inventory=serverEventInventory(listOf(MeshGroup(id="mesh//g",name="Grupo") to listOf(MeshNode(id="node//n",name="Equipo",meshid="mesh//g"))))
    @Test fun resolvesCanonicalDeviceAndGroup() { assertEquals("Equipo (Grupo)",resolveActivityEntry(row(),inventory).target) }
    @Test fun resolvesBareNodeUsingSharedPolicy() { assertEquals("Equipo (Grupo)",resolveActivityEntry(row("n"),inventory).target) }
    @Test fun neverCrossesDomains() { assertEquals((if(language=="es") "Equipo no identificado" else "Unidentified device"),resolveActivityEntry(row("node/other/n"),inventory).target) }
    @Test fun preservesExplicitEventName() { assertEquals("Nombre recibido",resolveActivityEntry(row(name="Nombre recibido"),inventory).deviceName) }
    @Test fun preservesUnknownOutcomeAndRequest() {
        val value=resolveActivityEntry(row(),inventory)
        assertEquals(ActivityStatus.UNKNOWN,value.status);assertEquals("request",value.requestId);assertEquals("Sin confirmar",value.summary)
    }
    @Test fun knownAndUnknownTitlesStayDistinct() {
        assertEquals((if(language=="es") "Comando remoto" else "Remote command"),activityEventTitle("runcommands"));assertEquals((if(language=="es") "Evento del servidor" else "Server event"),activityEventTitle("future-action"))
    }
    @Test fun actionAndMessageIconsStayDistinct() {
        assertEquals("runcommands",activityIconKey(row()));assertEquals("message",activityIconKey(row().copy(action="msg")))
    }
    @Test fun noIdCannotAcquireDeviceByNameOrTime() {
        val value=resolveActivityEntry(row(id=""),inventory)
        assertEquals("",value.deviceName);assertEquals((if(language=="es") "Servidor" else "Server"),value.target)
    }
}
