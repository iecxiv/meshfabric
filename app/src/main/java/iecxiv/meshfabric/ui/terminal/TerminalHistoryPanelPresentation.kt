package iecxiv.meshfabric.ui.terminal

internal data class TerminalHistoryPanelPresentation(val visible: Boolean, val expanded: Boolean)

/** Header: hidden -> expanded, visible -> hidden. It never owns a separate menu. */
internal fun terminalHistoryHeaderClick(current: TerminalHistoryPanelPresentation): TerminalHistoryPanelPresentation =
    TerminalHistoryPanelPresentation(visible = !current.visible, expanded = !current.visible)

/** A new involuntary disconnect always presents only the panel header. */
internal fun terminalHistoryUnexpectedDisconnect(): TerminalHistoryPanelPresentation =
    TerminalHistoryPanelPresentation(visible = true, expanded = false)
