package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ActionFeedbackTest {
    private fun command()=CommandFeedback("request-a","node/tenant/a","Equipo","id")
    @Test fun acknowledgementIsNotOutput() {
        val s=command().reply("runcommands","","request-a","","OK",true)
        assertEquals(CommandPhase.ACCEPTED,s.phase); assertTrue(s.busy); assertEquals("",s.output)
    }
    @Test fun outputUsesRequestAndDevice() {
        val s=command().reply("msg","runcommands","request-a","node/tenant/a","uid=0",true)
        assertEquals(CommandPhase.OUTPUT,s.phase); assertEquals("uid=0",s.output)
    }
    @Test fun unrelatedResponseCannotFillWindow() {
        val s=command()
        assertEquals(s,s.reply("msg","runcommands","request-b","node/tenant/a","Otro",true))
        assertEquals(s,s.reply("msg","runcommands","request-a","node/tenant/b","Otro",true))
    }
    @Test fun emptyOutputIsStillAReply() {
        val s=command().reply("msg","runcommands","request-a","node/tenant/a","",true)
        assertEquals(CommandPhase.OUTPUT,s.phase); assertFalse(s.busy)
    }
    @Test fun literalOkOutputIsNotAnAcknowledgement() {
        assertEquals("OK",command().reply("msg","runcommands","request-a","node/tenant/a","OK",true).output)
    }
    @Test fun clipboardAcknowledgementDoesNotVerifyContent() {
        val s=ClipboardFeedback("a","node//a","Texto","Agente").acknowledged(true)
        assertEquals(ClipboardPhase.VERIFYING,s.phase); assertTrue(s.acceptedByAgent)
    }
    @Test fun clipboardMismatchAndTimeoutNeverClaimSuccess() {
        val s=ClipboardFeedback("a","node//a","Texto","Agente").readback(false).timeout()
        assertEquals(ClipboardPhase.UNKNOWN,s.phase); assertTrue(s.mismatch)
    }
    @Test fun clipboardReadbackCanVerify() {
        assertEquals(ClipboardPhase.VERIFIED,ClipboardFeedback("a","node//a","Texto","Agente").readback(true).phase)
    }
    @Test fun packetUsesUtf8LengthAndClipboardCommand() {
        val text="á🙂"
        val p=clipboardPacket(text)!!
        assertEquals(text.toByteArray(Charsets.UTF_8).size+4,p.size)
        assertEquals(9,p[1].toInt()); assertEquals(p.size,((p[2].toInt() and 255) shl 8) or (p[3].toInt() and 255))
        assertEquals(text,String(p,4,p.size-4,Charsets.UTF_8))
    }
    @Test fun packetRejectsOverflowAndNullButNotWhitespace() {
        assertNull(clipboardPacket("")); assertNull(clipboardPacket("x".repeat(65532))); assertNull(clipboardPacket("a\u0000b"))
        assertNotNull(clipboardPacket("  \n")); assertEquals(" a \nb ",comparableClipboard(" a \r\nb "))
    }
}
