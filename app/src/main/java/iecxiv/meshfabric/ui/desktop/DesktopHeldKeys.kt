package iecxiv.meshfabric.ui.desktop

internal val desktopHoldKeys = listOf(17, 16, 18, 91)

/** Each sender owns its keys. Releasing an action must not release another owner. */
internal class DesktopHeldKeys(private val emit: (Int, Boolean) -> Unit) {
    private val owners = linkedMapOf<Int, MutableSet<Any>>()
    private val holdOwner = Any()
    private val physicalOwner = Any()
    val held: Set<Int> get() = owners.filterValues { holdOwner in it }.keys.toSet()

    fun press(key: Int, owner: Any) {
        require(key in 1..255)
        val references = owners.getOrPut(key) { linkedSetOf() }
        val wasEmpty = references.isEmpty()
        if (references.add(owner) && wasEmpty) emit(key, true)
    }
    fun release(key: Int, owner: Any) {
        val references = owners[key] ?: return
        if (!references.remove(owner)) return
        if (references.isEmpty()) { owners.remove(key); emit(key, false) }
    }
    fun physical(key: Int, down: Boolean) {
        if (down) {
            if (owners[key]?.contains(physicalOwner) == true && key !in desktopHoldKeys) emit(key, true)
            else press(key, physicalOwner)
        } else release(key, physicalOwner)
    }
    fun toggle(key: Int) {
        require(key in desktopHoldKeys)
        if (key in held) release(key, holdOwner) else press(key, holdOwner)
    }
    fun releaseHolds() { desktopHoldKeys.asReversed().forEach { release(it, holdOwner) } }
    fun releaseAll() {
        val keys = owners.keys.toList().asReversed()
        owners.clear()
        keys.forEach { emit(it, false) }
    }
}
