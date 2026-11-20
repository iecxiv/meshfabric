package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class TransientPresentationTest {
    @Test fun localInboxDescriptionMatchesRequestedText() {
        assertEquals("Canal local para avisos recibidos desde servidor.",noticeLocalChannelLabel(true))
    }
    @Test fun normalLoadingDoesNotProduceDiagnostics() {
        assertNull(agentContextDiagnostic(true,true,false,false,false,false,false,false))
        assertNull(agentContextDiagnostic(true,true,true,true,false,false,true,false))
        assertNull(agentContextDiagnostic(true,true,true,false,true,false,true,false))
        assertNull(agentContextDiagnostic(true,true,true,false,false,false,true,true))
        assertNull(agentContextDiagnostic(true,false,false,false,false,false,false,false))
    }
    @Test fun failuresRemainDiagnosable() {
        assertEquals("INVITE_CONTEXT_DISCONNECTED",agentContextDiagnostic(false,true,false,false,false,false,false,false))
        assertEquals("INVITE_CONTEXT_READ_FAILED",agentContextDiagnostic(true,true,false,false,false,true,true,false))
        assertEquals("INVITE_CONTEXT_ACTOR_MISMATCH",agentContextDiagnostic(true,true,true,false,false,false,false,true))
        assertEquals("INVITE_CONTEXT_GROUP_OR_RIGHTS",agentContextDiagnostic(true,true,true,false,false,false,true,false))
    }
    @Test fun onlyTrailingTechnicalCodesAreSeparated() {
        assertEquals("Download failed." to "SAVE_IO",agentNoticeParts("Download failed. [SAVE_IO]"))
        assertEquals("Enlace generado." to "",agentNoticeParts("Enlace generado."))
        assertEquals("Text [not a code]" to "",agentNoticeParts("Text [not a code]"))
    }
}
