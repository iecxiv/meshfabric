package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import iecxiv.meshfabric.R
import kotlin.math.roundToInt

/** Custom shortcuts as buttons, each with a drag handle on the left and a cross on the right. */
@Composable
internal fun DesktopShortcutList(
    entries: List<DesktopShortcut>,
    connected: Boolean,
    onSend: (DesktopShortcut) -> Unit,
    onChange: (List<DesktopShortcut>) -> Unit,
    scrollState: ScrollState? = null,
    viewportPx: Int = 0
) {
    var working by remember(entries) { mutableStateOf(entries) }
    var dragging by remember { mutableStateOf<List<Int>?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var listTopPx by remember { mutableFloatStateOf(0f) }
    val rowHeight = 44.dp
    val density = LocalDensity.current
    val rowPx = with(density) { rowHeight.toPx() }
    val edgePx = with(density) { 72.dp.toPx() }
    val maxStepPx = rowPx * 0.35f
    val latestViewport = rememberUpdatedState(viewportPx)
    val latestOnChange = rememberUpdatedState(onChange)

    fun reorder(id: List<Int>) {
        val from = working.indexOfFirst { it.keys == id }
        if (from < 0) return
        val target = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, working.lastIndex)
        if (target != from) {
            val updated = working.toMutableList()
            updated.add(target, updated.removeAt(from))
            working = updated
            dragOffset -= (target - from) * rowPx
        }
    }

    fun finishDrag() {
        dragging = null
        dragOffset = 0f
        if (working != entries) latestOnChange.value(working)
    }

    fun move(from: Int, delta: Int) {
        val target = from + delta
        if (target !in working.indices) return
        val updated = working.toMutableList()
        updated.add(target, updated.removeAt(from))
        working = updated
        latestOnChange.value(updated)
    }

    LaunchedEffect(dragging) {
        val id = dragging ?: return@LaunchedEffect
        val scroll = scrollState ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val from = working.indexOfFirst { it.keys == id }
            if (from < 0) break
            val top = listTopPx + from * rowPx + dragOffset - scroll.value
            val bottom = top + rowPx
            val lower = latestViewport.value - edgePx
            val step = when {
                top < edgePx -> -((edgePx - top) / edgePx).coerceIn(0f, 1f) * maxStepPx
                bottom > lower -> ((bottom - lower) / edgePx).coerceIn(0f, 1f) * maxStepPx
                else -> 0f
            }
            if (step != 0f) {
                val consumed = scroll.scrollBy(step)
                if (consumed != 0f) {
                    dragOffset += consumed
                    reorder(id)
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().onGloballyPositioned { listTopPx = it.positionInParent().y }) {
        working.forEachIndexed { index, shortcut ->
            val id = shortcut.keys
            key(id) {
                val shortcutLabel = desktopShortcutDisplayLabel(shortcut)
                val moveUpLabel = stringResource(R.string.desktop_shortcuts_move_up, shortcutLabel)
                val moveDownLabel = stringResource(R.string.desktop_shortcuts_move_down, shortcutLabel)
                val isDragging = dragging == id
                Row(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                        .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DragHandle, contentDescription = null,
                        modifier = Modifier.size(40.dp).testTag("desktop-shortcut-handle-$index")
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(moveUpLabel) { move(index, -1); true },
                                    CustomAccessibilityAction(moveDownLabel) { move(index, 1); true }
                                )
                            }
                            .pointerInput(id, rowPx) {
                                detectDragGestures(
                                    onDragStart = { dragging = id; dragOffset = 0f },
                                    onDragEnd = { finishDrag() },
                                    onDragCancel = { finishDrag() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        reorder(id)
                                    }
                                )
                            }
                            .padding(8.dp)
                    )
                    FilterChip(
                        selected = true,
                        onClick = { onSend(shortcut) },
                        enabled = connected,
                        modifier = Modifier.weight(1f).heightIn(min = 32.dp).testTag("desktop-shortcut-button-$index"),
                        label = {
                            Text(
                                shortcutLabel, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    IconButton(
                        onClick = {
                            val updated = working.filter { it.keys != id }
                            working = updated
                            latestOnChange.value(updated)
                        },
                        modifier = Modifier.size(40.dp).testTag("desktop-shortcut-remove-$index")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.desktop_shortcuts_remove, shortcutLabel),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
