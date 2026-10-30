package iecxiv.meshfabric.ui.terminal

import iecxiv.meshfabric.data.remote.TerminalConnectionStatus
import org.junit.Assert.*
import org.junit.Test

class TerminalPopupActionsTest {
    @Test fun disconnectedCurrentKeepsBothActionsVisible() {
        val actions = terminalPopupActions(TerminalConnectionStatus.DISCONNECTED, false, false)
        assertTrue(actions.visible)
        assertTrue(actions.reconnectEnabled)
        assertFalse(actions.returnEnabled)
        assertFalse(actions.cancelVisible)
    }
    @Test fun previousHistoryOffersReconnectAndReturn() {
        val actions = terminalPopupActions(TerminalConnectionStatus.DISCONNECTED, true, false)
        assertTrue(actions.visible)
        assertTrue(actions.reconnectEnabled)
        assertTrue(actions.returnEnabled)
    }
    @Test fun reconnectingKeepsActionsVisibleAndAddsCancel() {
        val actions = terminalPopupActions(TerminalConnectionStatus.DISCONNECTED, true, true)
        assertTrue(actions.visible)
        assertFalse(actions.reconnectEnabled)
        assertTrue(actions.returnEnabled)
        assertTrue(actions.cancelVisible)
    }
    @Test fun reconnectingCurrentStillShowsDisabledReturn() {
        val actions = terminalPopupActions(TerminalConnectionStatus.DISCONNECTED, false, true)
        assertTrue(actions.visible)
        assertFalse(actions.returnEnabled)
        assertFalse(actions.reconnectEnabled)
        assertTrue(actions.cancelVisible)
    }
    @Test fun connectedCurrentDoesNotNeedDisconnectedActions() {
        val actions = terminalPopupActions(TerminalConnectionStatus.CONNECTED, false, false)
        assertFalse(actions.visible)
        assertFalse(actions.reconnectEnabled)
        assertFalse(actions.returnEnabled)
        assertFalse(actions.cancelVisible)
    }
    @Test fun initialConnectionDoesNotOfferAnotherAttempt() {
        val actions = terminalPopupActions(TerminalConnectionStatus.CONNECTING, false, false)
        assertFalse(actions.visible)
        assertFalse(actions.reconnectEnabled)
    }
}
