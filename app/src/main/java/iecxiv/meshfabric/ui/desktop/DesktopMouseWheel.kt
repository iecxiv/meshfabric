package iecxiv.meshfabric.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

@Composable
internal fun Modifier.desktopVerticalWheel(
    enabled: Boolean,
    geometry: DesktopWheelGeometry,
    reverse: Boolean,
    onWheel: (DesktopWheelPoint, Int) -> Boolean
): Modifier {
    val latestOnWheel = rememberUpdatedState(onWheel)
    val view = LocalView.current
    return pointerInput(enabled, geometry, reverse, view) {
        if (!enabled) return@pointerInput
        val accumulator = DesktopVerticalWheelAccumulator(reverse)
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                if (!view.hasWindowFocus()) { accumulator.reset(); continue }
                val change = event.changes.firstOrNull {
                    it.type == PointerType.Mouse && !it.isConsumed &&
                        it.scrollDelta.y.isFinite() && it.scrollDelta.y != 0f
                } ?: continue
                val point = geometry.project(change.position.x, change.position.y)
                if (point == null) { accumulator.reset(); continue }
                val units = accumulator.push(change.scrollDelta.y)
                if (units == 0 || latestOnWheel.value(point, units)) change.consume()
                else accumulator.reset()
            }
        }
    }
}
