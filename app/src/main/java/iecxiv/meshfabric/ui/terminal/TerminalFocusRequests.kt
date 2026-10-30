package iecxiv.meshfabric.ui.terminal

internal class TerminalFocusRequests {
    data class Ticket(val generation: Long, val showKeyboard: Boolean)
    private var generation = 0L
    private var pending: Ticket? = null
    fun request(showKeyboard: Boolean) { pending = Ticket(++generation, showKeyboard) }
    fun cancel() { generation++; pending = null }
    fun take(): Ticket? = pending.also { pending = null }
    fun isCurrent(ticket: Ticket): Boolean = ticket.generation == generation
}
