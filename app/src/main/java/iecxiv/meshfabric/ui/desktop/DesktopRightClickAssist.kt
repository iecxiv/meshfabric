package iecxiv.meshfabric.ui.desktop

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import kotlin.math.roundToInt

internal class DesktopRightClickAssistState(context: Context, orientation: Int) {
    private val prefs = context.applicationContext.getSharedPreferences("desktop_right_click_assist", Context.MODE_PRIVATE)
    private val key = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private fun read(name: String, fallback: Float) = runCatching { prefs.getFloat(name, fallback) }.getOrDefault(fallback).takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: fallback
    val latch = DesktopRightClickArm(runCatching { prefs.getBoolean("visible", false) }.getOrDefault(false))
    var x by mutableFloatStateOf(read(key + "_x", 1f))
    var y by mutableFloatStateOf(read(key + "_y", 0f))
    var placedX = 0f; var placedY = 0f
    var canvasWidth = 0; var canvasHeight = 0; var buttonSize = 0; var margin = 0
    private var device: Int? = null
    private var tail: Int? = null
    private var start = Offset.Zero
    private var origin: DesktopWheelPoint? = null
    private var dragging = false
    private var release: () -> Unit = {}
    fun toggleVisible() {
        reset()
        latch.show(!latch.visible)
        prefs.edit().putBoolean("visible", latch.visible).apply()
    }
    fun desired(px: Float, py: Float) {
        x = if (canvasWidth > buttonSize + margin * 2) ((px - margin) / (canvasWidth - buttonSize - margin * 2)).coerceIn(0f, 1f) else 0f
        y = if (canvasHeight > buttonSize + margin * 2) ((py - margin) / (canvasHeight - buttonSize - margin * 2)).coerceIn(0f, 1f) else 0f
    }
    fun savePosition() { prefs.edit().putFloat(key + "_x", x).putFloat(key + "_y", y).apply() }
    fun reset() {
        latch.cancel()
        if (dragging) { dragging = false; release() }
        tail = device ?: tail; device = null; origin = null; release = {}
    }
    fun mouse(event: MotionEvent, capture: DesktopMouseCapture): Boolean {
        val action = event.actionMasked
        if (device == null && action == MotionEvent.ACTION_UP && tail == event.deviceId) { tail = null; return true }
        if (action == MotionEvent.ACTION_DOWN) tail = null
        if (device != null) {
            if (device != event.deviceId) { reset(); return false }
            if (!capture.ready() || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_HOVER_EXIT ||
                event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) { reset(); return true }
            if (!dragging && !latch.armed) { reset(); return true }
            val up = action == MotionEvent.ACTION_UP ||
                (action == MotionEvent.ACTION_BUTTON_RELEASE && event.actionButton == MotionEvent.BUTTON_PRIMARY)
            if (action == MotionEvent.ACTION_MOVE || up) {
                val point = capture.target(Offset(event.x, event.y)) ?: run { reset(); return true }
                if (!dragging && (Offset(event.x, event.y) - start).getDistance() > slop) {
                    latch.cancel()
                    dragging = capture.primaryInput(origin, DesktopPrimaryPhase.DOWN)
                    if (!dragging) { reset(); return true }
                }
                if (dragging && !capture.primaryInput(point, DesktopPrimaryPhase.MOVE)) { reset(); return true }
                if (up) {
                    if (!dragging && latch.consume()) capture.rightClick(point)
                    reset()
                }
            }
            return true
        }
        val primaryDown = (action == MotionEvent.ACTION_DOWN && event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) ||
            (action == MotionEvent.ACTION_BUTTON_PRESS && event.actionButton == MotionEvent.BUTTON_PRIMARY)
        if (!latch.armed || !capture.ready() || !primaryDown ||
            event.buttonState and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD) != 0) return false
        val point = capture.target(Offset(event.x, event.y)) ?: return false
        device = event.deviceId; origin = point; start = Offset(event.x, event.y)
        release = { capture.primaryInput(null, DesktopPrimaryPhase.UP); Unit }
        return true
    }
}

@Composable internal fun rememberDesktopRightClickAssist(nodeId: String): DesktopRightClickAssistState {
    val context = LocalContext.current.applicationContext
    val orientation = LocalConfiguration.current.orientation
    return remember(context, nodeId, orientation) { DesktopRightClickAssistState(context, orientation) }
}

@Composable internal fun DesktopRightClickAssistBinding(state: DesktopRightClickAssistState, capture: DesktopMouseCapture, enabled: Boolean) {
    DisposableEffect(state, capture, enabled) {
        capture.assist = if (enabled) state else null
        if (!enabled) state.reset()
        onDispose { state.reset(); if (capture.assist === state) capture.assist = null; capture.assistControls = null }
    }
}

@Composable internal fun DesktopRightClickAssistHeader(state: DesktopRightClickAssistState) {
    val label = stringResource(if (state.latch.visible) R.string.desktop_right_assist_hide else R.string.desktop_right_assist_show)
    IconButton(onClick = state::toggleVisible, modifier = Modifier.size(36.dp).semantics { selected = state.latch.visible }) {
        DesktopRightClickHeaderGlyph(state.latch.visible, label)
    }
}

@Composable internal fun DesktopRightAssistLayout(
    state: DesktopRightClickAssistState, capture: DesktopMouseCapture,
    relativeY: Float, collapsed: Boolean, enabled: Boolean, controls: @Composable () -> Unit
) {
    val label = stringResource(R.string.desktop_right_assist_click)
    val status = stringResource(if (state.latch.armed) R.string.desktop_right_assist_armed else R.string.desktop_right_assist_idle)
    val interaction = remember(state) { MutableInteractionSource() }
    var dragX = 0f; var dragY = 0f; var previousX = 0f; var previousY = 0f
    Layout(modifier = Modifier.fillMaxSize(), content = {
        Box { controls() }
        if (state.latch.visible) Surface(
            modifier = Modifier.clip(CircleShape).onGloballyPositioned { capture.assistControls = it.boundsInWindow() }
                .semantics { contentDescription = label; stateDescription = status; selected = state.latch.armed }
                .pointerInput(state, enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { state.latch.cancel(); dragX = state.placedX; dragY = state.placedY; previousX = state.x; previousY = state.y },
                        onDragCancel = { state.x = previousX; state.y = previousY },
                        onDragEnd = state::savePosition,
                        onDrag = { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y; state.desired(dragX, dragY) })
                }.clickable(interactionSource = interaction, indication = null, enabled = enabled) { state.latch.toggle() },
            shape = CircleShape,
            shadowElevation = 0.dp,
            border = BorderStroke(if (state.latch.armed) 2.dp else 1.dp, MaterialTheme.colorScheme.outline),
            color = if (state.latch.armed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (state.latch.armed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ) { Box(contentAlignment = androidx.compose.ui.Alignment.Center) { DesktopRightClickHeaderGlyph(active = false, label = null, rightButtonBlue = state.latch.armed) } }
    }) { measurables, constraints ->
        val width = constraints.maxWidth; val height = constraints.maxHeight
        val margin = 12.dp.roundToPx(); val gap = 8.dp.roundToPx(); val size = 48.dp.roundToPx()
        val bar = measurables[0].measure(Constraints(0, (width - margin * 2).coerceAtLeast(0), 0, height))
        val bx = if (collapsed) minOf(margin, (width - bar.width).coerceAtLeast(0)) else ((width - bar.width) / 2).coerceAtLeast(0)
        val by = ((height - bar.height).coerceAtLeast(0) * (1f - relativeY.coerceIn(0f, 1f))).roundToInt()
        val bounds = DesktopAssistBounds(bx, by, bx + bar.width, by + bar.height)
        val position = if (measurables.size > 1) desktopAssistPosition(width, height, size, bounds, state.x, state.y, margin, gap) else null
        val button = if (position != null) measurables[1].measure(Constraints.fixed(size, size)) else null
        state.canvasWidth = width; state.canvasHeight = height; state.buttonSize = size; state.margin = margin
        if (position == null) capture.assistControls = null
        layout(width, height) {
            bar.place(bx, by)
            if (button != null && position != null) {
                state.placedX = position.first.toFloat(); state.placedY = position.second.toFloat()
                button.place(position.first, position.second)
            }
        }
    }
}
