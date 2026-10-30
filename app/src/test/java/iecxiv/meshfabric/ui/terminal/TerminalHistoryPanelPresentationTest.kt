package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class TerminalHistoryPanelPresentationTest {
    @Test fun headerOpensHiddenPanelExpanded() {
        assertEquals(TerminalHistoryPanelPresentation(true, true), terminalHistoryHeaderClick(TerminalHistoryPanelPresentation(false, false)))
    }
    @Test fun headerHidesExpandedPanel() {
        assertEquals(TerminalHistoryPanelPresentation(false, false), terminalHistoryHeaderClick(TerminalHistoryPanelPresentation(true, true)))
    }
    @Test fun headerHidesAutomaticallyShownCollapsedPanel() {
        assertEquals(TerminalHistoryPanelPresentation(false, false), terminalHistoryHeaderClick(terminalHistoryUnexpectedDisconnect()))
    }
    @Test fun reopeningAfterHideIsExpanded() {
        val hidden = terminalHistoryHeaderClick(TerminalHistoryPanelPresentation(true, false))
        assertEquals(TerminalHistoryPanelPresentation(true, true), terminalHistoryHeaderClick(hidden))
    }
    @Test fun unexpectedDisconnectShowsCollapsedPanel() {
        assertEquals(TerminalHistoryPanelPresentation(true, false), terminalHistoryUnexpectedDisconnect())
    }
    @Test fun staleExpansionCannotPreventExpandedManualOpening() {
        assertEquals(TerminalHistoryPanelPresentation(true, true), terminalHistoryHeaderClick(TerminalHistoryPanelPresentation(false, true)))
    }
}
