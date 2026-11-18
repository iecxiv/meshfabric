package iecxiv.meshfabric.ui.desktop

internal class DesktopTouchDragGesture(
    private val initial: DesktopWheelPoint,
    private val touchSlop: Float,
    private val press: (DesktopWheelPoint) -> Boolean,
    private val move: (DesktopWheelPoint) -> Boolean,
    private val release: () -> Unit
) {
    private var held = false
    private var moved = false
    private var ended = false
    var isCancelled = false
        private set

    fun move(point: DesktopWheelPoint?, distance: Float) {
        if (ended) return
        if (point == null || !distance.isFinite() || distance < 0f) { cancel(); return }
        if (distance > touchSlop) moved = true
        if (!moved) return
        if (!held) {
            if (!press(initial)) { cancel(); return }
            held = true
        }
        if (!move.invoke(point)) cancel()
    }

    fun finish(): Boolean {
        if (ended) return false
        ended = true
        releaseIfHeld()
        return !isCancelled && !moved
    }

    fun cancel() {
        if (ended) return
        isCancelled = true
        ended = true
        releaseIfHeld()
    }

    private fun releaseIfHeld() {
        if (!held) return
        held = false
        release()
    }
}
