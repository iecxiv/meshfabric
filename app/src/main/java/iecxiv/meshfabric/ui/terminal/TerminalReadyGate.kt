package iecxiv.meshfabric.ui.terminal

/** One readiness event per page; callbacks from retired pages cannot initialize a new view. */
internal class TerminalReadyGate {
    private var generation = 0L
    private var active = false
    private var accepted = false

    @Synchronized fun begin(): Long {
        generation++
        active = true
        accepted = false
        return generation
    }

    @Synchronized fun accept(token: Long): Boolean {
        if (!active || token != generation || accepted) return false
        accepted = true
        return true
    }

    @Synchronized fun invalidate() {
        generation++
        active = false
        accepted = false
    }
}
