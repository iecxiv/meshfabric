package iecxiv.meshfabric
internal class OwnedSessionCallback(
    private val read: () -> (() -> Unit)?,
    private val write: ((() -> Unit)?) -> Unit,
    action: () -> Unit
) {
    private var released = false
    val callback: () -> Unit = { if (ownsSlot) action() }
    val ownsSlot: Boolean get() = !released && read() === callback
    init { write(callback) }
    fun release() {
        if (released) return
        val owned = read() === callback
        released = true
        if (owned) write(null)
    }
}
