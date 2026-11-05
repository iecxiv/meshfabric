package iecxiv.meshfabric.ui.desktop

/** Input timestamps, not a debounce timer, identify duplicate representations of one stroke. */
internal class DesktopRightClickPolicy {
    private data class Stroke(val start: Long, var end: Long? = null)
    private val native = mutableMapOf<Int, Stroke>()
    private val legacy = mutableMapOf<Int, Stroke>()

    fun nativeActive(device: Int): Boolean = native[device]?.end == null && native.containsKey(device)
    fun ownsNative(device: Int, downTime: Long): Boolean = native[device]?.let {
        downTime >= it.start && (it.end == null || downTime <= it.end!!)
    } ?: false

    fun nativeDown(device: Int, downTime: Long): Boolean {
        if (nativeActive(device)) return false
        val duplicate = legacy[device]?.let {
            downTime >= it.start && (it.end == null || downTime <= it.end!!)
        } ?: false
        native[device] = Stroke(downTime)
        return !duplicate
    }

    fun nativeUp(device: Int, eventTime: Long) {
        native[device]?.let { it.end = eventTime.coerceAtLeast(it.start) }
    }

    fun backDown(device: Int, downTime: Long): Boolean {
        if (legacy[device]?.start == downTime) return false
        val duplicate = ownsNative(device, downTime)
        legacy[device] = Stroke(downTime)
        return !duplicate
    }

    fun backUp(device: Int, downTime: Long, eventTime: Long): Boolean {
        val stroke = legacy[device] ?: return false
        if (stroke.start != downTime) return false
        stroke.end = eventTime.coerceAtLeast(stroke.start)
        return true
    }

    fun reset() { native.clear(); legacy.clear() }

    companion object {
        fun acceptsLegacyBack(mouseSource: Boolean, scanCode: Int): Boolean =
            mouseSource && scanCode !in 275..279
    }
}
