package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class TerminalHandshakePolicyTest {
    @Test fun initialCStartsHandshake() { assertTrue(TerminalHandshakePolicy.shouldHandle("c", false)) }
    @Test fun initialCrStartsHandshake() { assertTrue(TerminalHandshakePolicy.shouldHandle("cr", false)) }
    @Test fun initialOneKeepsExistingCompatibility() { assertTrue(TerminalHandshakePolicy.shouldHandle("1", false)) }
    @Test fun connectedCIsOutput() { assertFalse(TerminalHandshakePolicy.shouldHandle("c", true)) }
    @Test fun connectedCrIsOutput() { assertFalse(TerminalHandshakePolicy.shouldHandle("cr", true)) }
    @Test fun connectedOneIsOutput() { assertFalse(TerminalHandshakePolicy.shouldHandle("1", true)) }
    @Test fun normalTextIsNotHandshake() {
        for (text in listOf("", "cat", "C", "crx", "11")) {
            assertFalse(TerminalHandshakePolicy.shouldHandle(text, false))
            assertFalse(TerminalHandshakePolicy.shouldHandle(text, true))
        }
    }
    @Test fun markersAreOnlyRecognizedBeforeCompletion() {
        for (text in listOf("c", "cr", "1")) {
            assertTrue(TerminalHandshakePolicy.shouldHandle(text, false))
            assertFalse(TerminalHandshakePolicy.shouldHandle(text, true))
        }
    }
}
