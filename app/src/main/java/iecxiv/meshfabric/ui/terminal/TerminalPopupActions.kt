package iecxiv.meshfabric.ui.terminal

import iecxiv.meshfabric.data.remote.TerminalConnectionStatus

internal data class TerminalPopupActions(
    val visible: Boolean,
    val reconnectEnabled: Boolean,
    val returnEnabled: Boolean,
    val cancelVisible: Boolean
)

internal fun terminalPopupActions(
    status: TerminalConnectionStatus, showingHistory: Boolean, reconnecting: Boolean
): TerminalPopupActions {
    val visible = showingHistory || status == TerminalConnectionStatus.DISCONNECTED || reconnecting
    return TerminalPopupActions(
        visible = visible,
        reconnectEnabled = visible && !reconnecting && status != TerminalConnectionStatus.CONNECTING,
        returnEnabled = visible && showingHistory,
        cancelVisible = visible && reconnecting
    )
}
