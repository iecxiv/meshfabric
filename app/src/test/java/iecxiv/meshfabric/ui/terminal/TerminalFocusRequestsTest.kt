package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalFocusRequestsTest {
    @Test fun noAutomaticInitialRequest() { assertNull(TerminalFocusRequests().take()) }
    @Test fun restoreDoesNotRequestKeyboard() {
        val requests = TerminalFocusRequests(); requests.request(false)
        assertFalse(requests.take()!!.showKeyboard)
    }
    @Test fun explicitRequestCanShowKeyboard() {
        val requests = TerminalFocusRequests(); requests.request(true)
        assertTrue(requests.take()!!.showKeyboard)
    }
    @Test fun consumedOnce() {
        val requests = TerminalFocusRequests(); requests.request(true)
        assertNotNull(requests.take()); assertNull(requests.take())
    }
    @Test fun cancelDropsPending() {
        val requests = TerminalFocusRequests(); requests.request(true); requests.cancel()
        assertNull(requests.take())
    }
    @Test fun cancelInvalidatesCallback() {
        val requests = TerminalFocusRequests(); requests.request(true); val ticket = requests.take()!!
        requests.cancel(); assertFalse(requests.isCurrent(ticket))
    }
    @Test fun newerRequestInvalidatesCallback() {
        val requests = TerminalFocusRequests(); requests.request(true); val old = requests.take()!!
        requests.request(false); val current = requests.take()!!
        assertFalse(requests.isCurrent(old)); assertTrue(requests.isCurrent(current)); assertFalse(current.showKeyboard)
    }
}
