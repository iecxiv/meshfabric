package iecxiv.meshfabric.ui.devices

import org.junit.Assert.*
import org.junit.Test

class AgentConsoleReasonTextTest {
    @Test fun everyKnownReasonHasBothLanguages() {
        val codes=listOf("identity","disconnected","timeout","unavailable","connection","invalid","denied","unverified","changed","local","closed")
        for(code in codes) {
            val spanish=agentConsoleReasonText(code,true)
            val english=agentConsoleReasonText(code,false)
            assertTrue(spanish.isNotBlank())
            assertTrue(english.isNotBlank())
            assertNotEquals(code,spanish)
            assertNotEquals(code,english)
            assertNotEquals(spanish,english)
            assertNotEquals(agentConsoleReasonText("unknown",true),spanish)
            assertNotEquals(agentConsoleReasonText("unknown",false),english)
        }
    }
    @Test fun timeoutHasAnExplicitExplanation() {
        assertEquals("Se agotó el tiempo de verificación.",agentConsoleReasonText("timeout",true))
        assertEquals("Verification timed out.",agentConsoleReasonText("timeout",false))
    }
    @Test fun unknownReasonDoesNotExposeTheCodeAsTheExplanation() {
        assertEquals("Consola desconectada por un motivo no identificado.",agentConsoleReasonText("future_code",true))
        assertEquals("Console disconnected for an unidentified reason.",agentConsoleReasonText("future_code",false))
    }
}
