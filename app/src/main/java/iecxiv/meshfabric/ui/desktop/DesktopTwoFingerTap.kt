package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

internal data class DesktopPointerSample(val id: Long, val x: Float, val y: Float)

/** Recognizes a short touch with exactly two fingers that do not move beyond the touch slop. */
internal class DesktopTwoFingerTap(private val slop: Float, private val timeoutMillis: Long) {
    private val origins = HashMap<Long, DesktopPointerSample>()
    private var startTime = 0L
    private var maxPointers = 0
    private var invalid = false

    fun reset() {
        origins.clear()
        startTime = 0L
        maxPointers = 0
        invalid = false
    }

    fun update(time: Long, pressed: List<DesktopPointerSample>) {
        if (pressed.isEmpty()) return
        if (origins.isEmpty()) startTime = time
        for (sample in pressed) {
            val origin = origins.getOrPut(sample.id) { sample }
            if (hypot(sample.x - origin.x, sample.y - origin.y) > slop) invalid = true
        }
        maxPointers = maxOf(maxPointers, pressed.size)
        if (maxPointers > 2 || time - startTime > timeoutMillis) invalid = true
    }

    fun isTap(endTime: Long): Boolean = !invalid && maxPointers == 2 && endTime - startTime <= timeoutMillis
}

private fun PointerInputChange.toSample() = DesktopPointerSample(id.value, position.x, position.y)

/** Observes touches without consuming them, so it works over the desktop image and over the black area alike. */
@Composable
internal fun Modifier.desktopTwoFingerTap(enabled: Boolean, onTap: () -> Unit): Modifier {
    val latest = rememberUpdatedState(onTap)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val detector = DesktopTwoFingerTap(viewConfiguration.touchSlop, viewConfiguration.longPressTimeoutMillis)
        awaitEachGesture {
            detector.reset()
            val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (first.type == PointerType.Mouse) return@awaitEachGesture
            var last = first.uptimeMillis
            detector.update(last, listOf(first.toSample()))
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                last = event.changes.maxOf { it.uptimeMillis }
                detector.update(last, event.changes.filter { it.pressed }.map { it.toSample() })
            } while (event.changes.any { it.pressed })
            if (detector.isTap(last)) latest.value()
        }
    }
}
