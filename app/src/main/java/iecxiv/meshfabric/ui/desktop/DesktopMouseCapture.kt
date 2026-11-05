package iecxiv.meshfabric.ui.desktop

import android.content.Context
import android.content.ContextWrapper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import iecxiv.meshfabric.MainActivity

internal class DesktopMouseCapture {
    var viewport: LayoutCoordinates? = null
    var localControls: Rect? = null
    var ready: () -> Boolean = { false }
    var geometry: () -> DesktopWheelGeometry? = { null }
    var rightClick: (DesktopWheelPoint) -> Boolean = { false }
    private val policy = DesktopRightClickPolicy()
    private var lastPosition: Offset? = null
    private var lastDevice = -1
    private var navigationButton = false

    private fun target(position: Offset): DesktopWheelPoint? {
        if (!ready() || localControls?.contains(position) == true) return null
        val coordinates = viewport ?: return null
        if (!coordinates.isAttached) return null
        val local = coordinates.windowToLocal(position)
        return geometry()?.project(local.x, local.y)
    }

    fun motion(event: MotionEvent): Boolean {
        if (event.pointerCount != 1 || (!event.isFromSource(InputDevice.SOURCE_MOUSE) &&
                event.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE)) return false
        val device = event.deviceId
        lastDevice = device
        lastPosition = Offset(event.x, event.y)
        navigationButton = event.buttonState and (MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD) != 0 ||
            ((event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS || event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) &&
                (event.actionButton == MotionEvent.BUTTON_BACK || event.actionButton == MotionEvent.BUTTON_FORWARD))
        if (!ready()) { policy.reset(); return false }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_SCROLL) return false
        val secondary = event.buttonState and MotionEvent.BUTTON_SECONDARY != 0
        val secondaryAction = (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) &&
            event.actionButton == MotionEvent.BUTTON_SECONDARY
        if (action == MotionEvent.ACTION_HOVER_EXIT) {
            lastPosition = null
            if (policy.nativeActive(device)) policy.nativeUp(device, event.eventTime)
            return false
        }
        if ((action == MotionEvent.ACTION_DOWN && secondary) ||
            (action == MotionEvent.ACTION_BUTTON_PRESS && secondaryAction)) {
            if (policy.nativeActive(device)) return true
            val point = target(lastPosition!!) ?: return false
            val start = event.downTime.takeIf { it > 0L && it <= event.eventTime } ?: event.eventTime
            if (policy.nativeDown(device, start)) rightClick(point)
            return true
        }
        if (policy.nativeActive(device)) {
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP ||
                (action == MotionEvent.ACTION_BUTTON_RELEASE && secondaryAction) || !secondary)
                policy.nativeUp(device, event.eventTime)
            return true
        }
        return action == MotionEvent.ACTION_UP && policy.ownsNative(device, event.downTime)
    }

    fun key(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_UP && policy.backUp(event.deviceId, event.downTime, event.eventTime)) return true
        if (event.action != KeyEvent.ACTION_DOWN || !ready()) return false
        val nativeOwned = policy.ownsNative(event.deviceId, event.downTime)
        val recognized = DesktopRightClickPolicy.acceptsLegacyBack(event.isFromSource(InputDevice.SOURCE_MOUSE), event.scanCode) ||
            (event.scanCode == 273 && nativeOwned)
        if (!recognized || (navigationButton && !nativeOwned)) return false
        val point = if (event.deviceId == lastDevice) lastPosition?.let(::target) else null
        if (point == null && !nativeOwned) return false
        if (policy.backDown(event.deviceId, event.downTime) && point != null) rightClick(point)
        return true
    }

    fun reset() {
        policy.reset()
        lastPosition = null
        lastDevice = -1
        navigationButton = false
    }
}

private fun Context.desktopActivity(): MainActivity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is MainActivity) return current
        val next = current.baseContext
        if (next === current) break
        current = next
    }
    return current as? MainActivity
}

@Composable
internal fun Modifier.desktopRightClickCanvas(
    capture: DesktopMouseCapture,
    enabled: Boolean,
    geometry: DesktopWheelGeometry,
    onRightClick: (DesktopWheelPoint) -> Boolean
): Modifier {
    val activity = LocalContext.current.desktopActivity()
    val view = LocalView.current
    val latestEnabled = rememberUpdatedState(enabled)
    val latestGeometry = rememberUpdatedState(geometry)
    val latestClick = rememberUpdatedState(onRightClick)
    DisposableEffect(activity, capture, view) {
        capture.ready = { latestEnabled.value && view.hasWindowFocus() }
        capture.geometry = { latestGeometry.value }
        capture.rightClick = { latestClick.value(it) }
        activity?.desktopMouseCapture?.reset()
        if (activity != null) activity.desktopMouseCapture = capture
        onDispose {
            if (activity != null && activity.desktopMouseCapture === capture) activity.desktopMouseCapture = null
            capture.reset()
            capture.ready = { false }
            capture.viewport = null
        }
    }
    return onGloballyPositioned { capture.viewport = it }
}

internal fun Modifier.desktopMouseLocalControls(capture: DesktopMouseCapture): Modifier =
    onGloballyPositioned { capture.localControls = it.boundsInWindow() }
