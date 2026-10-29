package iecxiv.meshfabric.ui.terminal

/** Main-thread LRU: attached views are never evicted. */
internal class RetainedTerminalStore<K, V : Any>(
    private val limit: Int,
    private val attached: (V) -> Boolean,
    private val dispose: (V) -> Unit
) {
    private val entries = LinkedHashMap<K, V>()
    init { require(limit > 0) }
    val size: Int get() = entries.size

    fun acquire(key: K, matches: (V) -> Boolean, create: () -> V): V {
        val previous = entries.remove(key)
        if (previous != null && matches(previous)) {
            entries[key] = previous
            return previous
        }
        if (previous != null) dispose(previous)
        val value = create()
        entries[key] = value
        trim(value)
        return value
    }
    fun trim(protectedValue: V? = null) {
        while (entries.size > limit) {
            val victim = entries.entries.firstOrNull { it.value !== protectedValue && !attached(it.value) } ?: return
            entries.remove(victim.key)
            dispose(victim.value)
        }
    }
    fun remove(key: K) { entries.remove(key)?.let(dispose) }
    fun removeIf(key: K, expected: V): Boolean {
        val current = entries[key] ?: return false
        if (current !== expected) return false
        entries.remove(key)
        dispose(current)
        return true
    }
    fun clear() {
        val old = entries.values.toList()
        entries.clear()
        old.forEach(dispose)
    }
}

/** An old Compose disposal must not detach a WebView already attached to a new host. */
internal class TerminalAttachmentLease {
    private var serial = 0L
    var current: Long? = null
        private set
    fun attach(): Long = (++serial).also { current = it }
    fun release(token: Long): Boolean {
        if (current != token) return false
        current = null
        return true
    }
    fun invalidate() { serial++; current = null }
}
