package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class TerminalProtocolTest {
    @Test fun resizeUsesSingleControlChannelPacket() {
        assertEquals("{\"ctrlChannel\":\"102938\",\"type\":\"termsize\",\"cols\":90,\"rows\":30}", TerminalProtocol.resize(90, 30))
    }
    @Test fun optionsCarryInitialDimensions() {
        assertEquals("{\"ctrlChannel\":\"102938\",\"type\":\"options\",\"cols\":80,\"rows\":25}", TerminalProtocol.options(80, 25))
    }
    @Test fun handshakeIsAcceptedOnlyBeforeConnection() {
        assertTrue(TerminalProtocol.isHandshake("c", false))
        assertTrue(TerminalProtocol.isHandshake("cr", false))
        assertFalse(TerminalProtocol.isHandshake("1", false))
        assertFalse(TerminalProtocol.isHandshake("c", true))
        assertFalse(TerminalProtocol.isHandshake("cr", true))
        assertFalse(TerminalProtocol.isHandshake("1", true))
    }
    @Test fun utf8SurvivesEveryByteBoundary() {
        val text = "á€😀中文\r\n\u001b[31m"
        val bytes = text.toByteArray(Charsets.UTF_8)
        for (split in 0..bytes.size) {
            val decoder = TerminalUtf8Decoder()
            assertEquals(text, decoder.decode(bytes.copyOfRange(0, split)) + decoder.decode(bytes.copyOfRange(split, bytes.size)))
        }
    }
    @Test fun utf8CanArriveOneByteAtATime() {
        val decoder = TerminalUtf8Decoder()
        val text = "ñ€😀"
        assertEquals(text, text.toByteArray(Charsets.UTF_8).joinToString("") { decoder.decode(byteArrayOf(it)) })
    }
    @Test fun partialCharacterDoesNotCrossSocketGenerations() {
        val decoder = TerminalUtf8Decoder()
        assertEquals("", decoder.decode(byteArrayOf(0xE2.toByte())))
        decoder.reset()
        assertEquals("x", decoder.decode(byteArrayOf(0x78)))
    }
    @Test fun malformedUtf8DoesNotPoisonFollowingOutput() {
        val decoder = TerminalUtf8Decoder()
        assertEquals("\uFFFDx", decoder.decode(byteArrayOf(0xFF.toByte(), 0x78)))
    }
    @Test fun duplicateResizeIsNotQueuedTwice() {
        val state = TerminalResizeState()
        var count = 0
        assertTrue(state.send(80, 25) { count++; true })
        assertFalse(state.send(80, 25) { count++; true })
        assertEquals(1, count)
    }
    @Test fun rejectedResizeCanBeRetried() {
        val state = TerminalResizeState()
        assertFalse(state.send(80, 25) { false })
        assertTrue(state.send(80, 25) { true })
    }
    @Test fun newSocketRequiresSameGridToBeSentAgain() {
        val state = TerminalResizeState()
        assertTrue(state.send(80, 25) { true })
        state.reset()
        assertTrue(state.send(80, 25) { true })
    }
    @Test fun invalidGeometryNeverReachesSocket() {
        val state = TerminalResizeState()
        assertFalse(state.send(4, 25) { fail("Invalid grid sent"); true })
        assertFalse(state.send(80, 1) { fail("Invalid grid sent"); true })
    }
    @Test fun forceAllowsSameGridToBeSentAgain() {
        val state = TerminalResizeState()
        assertTrue(state.send(80, 25) { true })
        assertTrue(state.send(80, 25, true) { true })
    }
}
