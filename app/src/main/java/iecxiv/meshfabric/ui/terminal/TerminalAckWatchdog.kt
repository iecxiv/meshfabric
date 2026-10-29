package iecxiv.meshfabric.ui.terminal

/** Monotonic-clock policy. Only a visible, resumed view spends its ACK deadline. */
internal class TerminalAckWatchdog(private val timeoutMs: Long = 20_000L) {
    private var active = false
    private var token: String? = null
    private var deadline: Long? = null
    private var expired = false
    init { require(timeoutMs > 0) }

    fun setActive(value: Boolean, now: Long) {
        if (active == value) return
        active = value
        deadline = if (value && token != null && !expired) now + timeoutMs else null
    }
    fun sent(value: String, now: Long) {
        token = value
        expired = false
        deadline = if (active) now + timeoutMs else null
    }
    fun acknowledge(value: String): Boolean {
        if (expired || token != value) return false
        token = null
        deadline = null
        return true
    }
    fun remaining(now: Long): Long? = if (active && !expired) deadline?.let { (it - now).coerceAtLeast(0L) } else null
    fun poll(now: Long): Boolean {
        val due = deadline ?: return false
        if (!active || expired || token == null || now < due) return false
        expired = true
        deadline = null
        return true
    }
    fun reset() {
        active = false
        token = null
        deadline = null
        expired = false
    }
}
