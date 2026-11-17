package iecxiv.meshfabric.ui.devices

import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class DeviceManualEventPolicyTest {
    @Test fun packetTargetsExactlyOneDevice() {
        val p=manualEventPacket("node//a","Prueba",4)
        assertEquals("setDeviceEvent",p.get("action").asString)
        assertEquals("node//a",p.get("nodeid").asString)
        assertEquals(setOf("action","nodeid","msg"),p.entrySet().map {it.key}.toSet())
    }
    @Test fun encodingUsesPercent20RatherThanPlus() {assertEquals("a%20b%2Bc",manualEventEncoded("a b+c"))}
    @Test fun unicodeAndNewlinesRoundTripOnce() {
        val original="Revisión ✓\nEquipo listo"
        assertEquals(original,URLDecoder.decode(manualEventEncoded(original),"UTF-8"))
    }
    @Test fun matchesEncodeURIComponentSafePunctuation() {assertEquals("!*'()~._-",manualEventEncoded("!*'()~._-"))}
    @Test fun percentIsEncodedNotTreatedAsAnEscape() {assertEquals("100%25",manualEventEncoded("100%"))}
    @Test fun rejectsBlankOrExcessiveText() {
        assertTrue(runCatching {manualEventEncoded(" ")}.isFailure)
        assertTrue(runCatching {manualEventEncoded("a".repeat(1025))}.isFailure)
        assertTrue(runCatching {manualEventEncoded("漢".repeat(1024))}.isFailure)
    }
    @Test fun rejectsControlCharactersAndUnpairedSurrogates() {
        assertTrue(runCatching {manualEventEncoded("bad\u0000text")}.isFailure)
        val malformed=String(charArrayOf(0xD800.toChar()))
        assertTrue(runCatching {manualEventEncoded(malformed)}.isFailure)
    }
    @Test fun requiresManagementPermission() {assertTrue(runCatching {manualEventPacket("node//a","Texto",8)}.isFailure)}
    @Test fun rejectsMalformedNodeId() {assertTrue(runCatching {manualEventPacket("node//","Texto",4)}.isFailure)}
    @Test fun onlyVerifiedReadyStateCanSubmit() {
        assertTrue(ManualEventState("ready",rights=4).canSubmit)
        assertFalse(ManualEventState("ready",rights=8).canSubmit)
        for(phase in listOf("loading","checking","sent","error")) assertFalse(ManualEventState(phase,rights=4).canSubmit)
    }
}
