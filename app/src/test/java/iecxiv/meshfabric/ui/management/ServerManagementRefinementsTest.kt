package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class ServerManagementRefinementsTest {
    @Test fun pausedTraceNeverAppendsAndResumeNeedsAuthority() {
        assertTrue(serverTraceAcceptsLive(true,false))
        assertFalse(serverTraceAcceptsLive(true,true))
        assertFalse(serverTraceAcceptsLive(false,false))
        assertFalse(serverTraceAcceptsLive(false,true))
    }
    @Test fun groupActionSurvivesPresentationRefreshButNotDisconnect() {
        assertTrue(userGroupActionCanStayOpen(true,"user//admin"))
        assertFalse(userGroupActionCanStayOpen(false,"user//admin"))
        assertFalse(userGroupActionCanStayOpen(true,""))
    }
    @Test fun textExportIsUtf8WithoutCsvFormatting() {
        val text="línea uno\ncomando,valor\n"
        assertArrayEquals(text.toByteArray(Charsets.UTF_8),serverConsoleExportBytes(text))
        assertEquals(0,serverConsoleExportBytes("").size)
    }
}
