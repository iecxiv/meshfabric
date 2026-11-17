package iecxiv.meshfabric.ui.devices

import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralChatPolicyTest {
    @Test fun sessionBindsExactDeviceAndActor() {assertEquals("meshmessenger/node//a/user//admin",generalChatId("node//a","user//admin"))}
    @Test fun differentDomainsAreRejected() {assertTrue(runCatching {generalChatId("node/site/a","user/other/admin")}.isFailure)}
    @Test fun malformedAndControlBearingIdentitiesAreRejected() {
        for(id in listOf("node//","node//a/extra","node//a\n")) assertTrue(runCatching {generalChatId(id,"user//admin")}.isFailure)
    }
    @Test fun relayParametersCannotInjectAnotherTarget() {
        val path=generalChatRelayPath("node//a","user//admin","token+&id=other")
        val params=path.substringAfter('?').split('&')
        assertEquals(2,params.size)
        assertEquals("meshmessenger/node//a/user//admin",URLDecoder.decode(params[0].substringAfter('='),"UTF-8"))
        assertEquals("token+&id=other",URLDecoder.decode(params[1].substringAfter('='),"UTF-8"))
    }
    @Test fun socketOpeningAloneDoesNotMeanPaired() {assertNull(generalChatMarker("open"));assertNull(generalChatMarker("connected"))}
    @Test fun onlyExactRelayMarkersPairAndIdentifyRecording() {assertEquals(false,generalChatMarker("c"));assertEquals(true,generalChatMarker("cr"));assertNull(generalChatMarker("c "))}
    @Test fun noSendingWhileWaitingCheckingOrClosed() {
        for(phase in listOf("loading","ready","checking","connecting","waiting","checking_send","closed")) assertFalse(generalChatCanSend(phase,"Hola"))
        assertTrue(generalChatCanSend("connected","Hola"))
    }
    @Test fun invalidTextIsNeverSendable() {
        for(text in listOf(" ","x".repeat(4097),"bad\u0000text")) assertFalse(generalChatCanSend("connected",text))
        assertTrue(generalChatTextValid("Hola\nTexto + 100% ✓"))
    }
    @Test fun chatPacketPreservesLiteralTextAndDoesNotClaimReceipt() {
        val text="<b>literal</b> + 100%\n✓"
        val p=generalChatPacket(text)
        assertEquals("chat",p.get("action").asString);assertEquals(text,p.get("msg").asString)
        assertEquals(2,p.entrySet().size)
    }
    @Test fun historyHasCountAndMemoryBounds() {
        val history=generalChatHistory((1..120).map {GeneralChatEntry(it.toString(),false,"x".repeat(4096))})
        assertTrue(history.size<=100);assertTrue(history.sumOf {it.text.length}<=64000)
        assertEquals("120",history.last().id)
    }
    @Test fun localQueueIsNotDeliveryOrReading() {assertEquals("queued",GeneralChatEntry("a",true,"Hola","queued").status)}
    @Test fun blankTokenIsRejected() {assertTrue(runCatching {generalChatRelayPath("node//a","user//admin","")}.isFailure)}
}
