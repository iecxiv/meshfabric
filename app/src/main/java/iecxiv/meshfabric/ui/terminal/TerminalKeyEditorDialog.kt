package iecxiv.meshfabric.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import iecxiv.meshfabric.ui.components.AppAlertDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TerminalKeyEditorDialog(
    initial: List<String>, es: Boolean,
    onSave: (List<String>) -> Unit, onDismiss: () -> Unit
) {
    fun label(spanish: String, english: String) = if (es) spanish else english
    val keys = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeight = 52.dp
    val density = LocalDensity.current
    val rowPx = with(density) { rowHeight.toPx() }
    val edgePx = with(density) { 72.dp.toPx() }
    val maxStepPx = rowPx * 0.35f
    val scrollState = rememberScrollState()
    var viewportPx by remember { mutableIntStateOf(0) }
    var listTopPx by remember { mutableFloatStateOf(0f) }
    fun reorder(id: String) {
        val from = keys.indexOf(id)
        if (from < 0) return
        val target = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, keys.lastIndex)
        if (target != from) {
            keys.add(target, keys.removeAt(from))
            dragOffset -= (target - from) * rowPx
        }
    }
    LaunchedEffect(dragging) {
        val id = dragging ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val from = keys.indexOf(id)
            if (from < 0) break
            val top = listTopPx + from * rowPx + dragOffset - scrollState.value
            val bottom = top + rowPx
            val lower = viewportPx - edgePx
            val step = when {
                top < edgePx -> -((edgePx - top) / edgePx).coerceIn(0f, 1f) * maxStepPx
                bottom > lower -> ((bottom - lower) / edgePx).coerceIn(0f, 1f) * maxStepPx
                else -> 0f
            }
            if (step != 0f) {
                val consumed = scrollState.scrollBy(step)
                if (consumed != 0f) {
                    dragOffset += consumed
                    reorder(id)
                }
            }
        }
    }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label("Teclas de la barra", "Bar keys")) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).onSizeChanged { viewportPx = it.height }.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    label("Arrastra el icono de asa para ordenar y pulsa la cruz para quitar una tecla.",
                        "Drag the handle icon to reorder and tap the cross to remove a key."),
                    style = MaterialTheme.typography.bodySmall
                )
                Column(Modifier.fillMaxWidth().onGloballyPositioned { listTopPx = it.positionInParent().y }) {
                    keys.forEach { id ->
                        key(id) {
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
                                    modifier = Modifier.size(40.dp).testTag("terminal-key-handle-$id")
                                        .pointerInput(id, rowPx) {
                                            detectDragGestures(
                                                onDragStart = { dragging = id; dragOffset = 0f },
                                                onDragEnd = { dragging = null; dragOffset = 0f },
                                                onDragCancel = { dragging = null; dragOffset = 0f },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffset += amount.y
                                                    reorder(id)
                                                }
                                            )
                                        }
                                        .padding(8.dp)
                                )
                                Text(terminalKeyDescription(id), Modifier.weight(1f))
                                IconButton(
                                    onClick = { keys.remove(id) },
                                    modifier = Modifier.testTag("terminal-key-remove-$id")
                                ) { Icon(Icons.Default.Close, contentDescription = label("Quitar", "Remove")) }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text(label("Añadir teclas", "Add keys"), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    terminalCatalogKeyIds.filter { it !in keys }.forEach { id ->
                        FilterChip(
                            selected = false,
                            onClick = { keys.add(id) },
                            label = { Text(terminalKeyDescription(id), fontSize = 11.sp) },
                            modifier = Modifier.testTag("terminal-key-add-$id")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(keys.toList()) }, modifier = Modifier.testTag("terminal-key-editor-save")) {
                Text(label("Guardar", "Save"))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { keys.clear(); keys.addAll(terminalDefaultKeyIds) }) { Text(label("Restablecer", "Reset")) }
                TextButton(onClick = onDismiss) { Text(label("Cancelar", "Cancel")) }
            }
        }
    )
}
