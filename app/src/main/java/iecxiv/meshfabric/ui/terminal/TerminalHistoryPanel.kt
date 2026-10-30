package iecxiv.meshfabric.ui.terminal

/** One automatic presentation per established-session disconnect incident. */
internal class TerminalHistoryPanelGate {
    private val handled = mutableMapOf<String, Long>()
    @Synchronized fun consume(recordId: String, incident: Long): Boolean {
        if (incident <= 0L || incident <= (handled[recordId] ?: 0L)) return false
        handled[recordId] = incident
        return true
    }
    @Synchronized fun clear() { handled.clear() }
}

internal fun terminalHistoryBarVisible(unexpectedDisconnect: Boolean, showingHistory: Boolean, reconnecting: Boolean): Boolean =
    unexpectedDisconnect || showingHistory || reconnecting
