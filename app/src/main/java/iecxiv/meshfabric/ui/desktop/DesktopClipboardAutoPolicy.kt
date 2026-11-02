package iecxiv.meshfabric.ui.desktop

/** Main-thread, per-visible-node state. Clipboard text is never logged or persisted. */
internal class DesktopClipboardAutoPolicy {
    private var localKnown = false
    private var local = ""
    private var remoteKnown = false
    private var remote = ""
    private var pending: String? = null
    private var awaiting: String? = null

    fun seedLocalIfUnknown(value: String) {
        if (!localKnown) { local = value; localKnown = true }
    }

    fun observeLocal(value: String) {
        if (!localKnown) { seedLocalIfUnknown(value); return }
        if (local == value) return
        local = value
        pending = value
    }

    fun takeLocalToSend(): String? {
        val value = pending ?: return null
        pending = null
        awaiting = value
        return value
    }

    fun discardPendingLocal() { pending = null }
    fun writeFailed() { awaiting = null; remoteKnown = false }

    fun observeRemote(value: String): String? {
        val first = !remoteKnown
        val changed = first || remote != value
        remote = value
        remoteKnown = true
        if (pending != null) return null
        // The first snapshot after a local write is an echo/conflict baseline, not a local overwrite.
        if (awaiting != null) { awaiting = null; return null }
        if (first || !changed || (localKnown && local == value)) return null
        local = value
        localKnown = true
        return value
    }

    fun reset() {
        localKnown = false
        remoteKnown = false
        local = ""
        remote = ""
        pending = null
        awaiting = null
    }
}
