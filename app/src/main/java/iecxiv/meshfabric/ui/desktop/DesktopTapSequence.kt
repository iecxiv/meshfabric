package iecxiv.meshfabric.ui.desktop

internal data class DesktopTapTarget(val x: Int, val y: Int, val swapped: Boolean)

internal class DesktopTapSequence(
    private val binding: () -> Any?,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val emit: (Any, DesktopTapTarget, Int) -> Boolean
) {
    private var target: DesktopTapTarget? = null
    private var origin: Any? = null
    private var cancelTimer: (() -> Unit)? = null
    private var generation = 0L
    private var reserved = false

    fun reserveSecond(): Boolean {
        if (target == null) return false
        if (binding() !== origin) { cancel(); return false }
        generation++
        cancelTimer?.invoke()
        cancelTimer = null
        reserved = true
        return true
    }

    fun currentBinding(): Any? = binding()

    fun tap(next: DesktopTapTarget, timeoutMillis: Long, expectedOrigin: Any?): Boolean {
        if (expectedOrigin == null || binding() !== expectedOrigin) { cancel(); return false }
        if (reserved && target?.swapped == next.swapped) return deliver(2)
        flush()
        val socket = expectedOrigin
        target = next
        origin = socket
        reserved = false
        val ticket = ++generation
        cancelTimer = schedule(timeoutMillis.coerceAtLeast(1L)) {
            if (generation == ticket) flush()
        }
        return true
    }

    fun flush(): Boolean = deliver(1)

    private fun deliver(count: Int): Boolean {
        val point = target ?: return true
        val socket = origin
        cancel()
        return socket != null && binding() === socket && emit(socket, point, count)
    }

    fun cancel() {
        generation++
        val timer = cancelTimer
        cancelTimer = null
        target = null
        origin = null
        reserved = false
        timer?.invoke()
    }
}

internal fun desktopTapPackets(target: DesktopTapTarget, count: Int): List<ByteArray> {
    require(count in 1..2)
    val actions = mutableListOf(0x00)
    repeat(count) {
        actions += if (target.swapped) 0x08 else 0x02
        actions += if (target.swapped) 0x10 else 0x04
    }
    val x = target.x.coerceIn(0, 65535)
    val y = target.y.coerceIn(0, 65535)
    return actions.map { action ->
        byteArrayOf(0x00, 0x02, 0x00, 0x0A, 0x00, action.toByte(),
            (x shr 8).toByte(), x.toByte(), (y shr 8).toByte(), y.toByte())
    }
}
