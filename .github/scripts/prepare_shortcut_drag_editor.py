#!/usr/bin/env python3
"""Custom desktop shortcuts: drag handle + button + cross, like the terminal bar key editor."""
import re
import subprocess
from pathlib import Path

MAIN = Path('app/src/main/java/iecxiv/meshfabric/ui/desktop')
ANDROID_TEST = Path('app/src/androidTest/java/iecxiv/meshfabric/ui/desktop')
RES = Path('app/src/main/res')


def fail(message):
    raise SystemExit(message)


def rep(text, old, new):
    found = text.count(old)
    if found != 1:
        fail('anchor count %d (expected 1): %r' % (found, old[:90]))
    return text.replace(old, new)


def sub(text, pattern, new, label):
    out, n = re.subn(pattern, lambda m: new, text, count=1, flags=re.S)
    if n != 1:
        fail('pattern not found: ' + label)
    return out


sections_path = MAIN / 'DesktopShortcutSections.kt'
digest = subprocess.check_output(['git', 'hash-object', str(sections_path)], text=True).strip()
if digest != '5233781278da1e029b8d2b9c82492dfa5c8c9842':
    fail('DesktopShortcutSections.kt changed; review required')

s = sections_path.read_text(encoding='utf-8')
s = rep(s, 'internal fun DesktopShortcutSections(nodeId: String, viewModel: RemoteDesktopViewModel, connected: Boolean) {',
        'internal fun DesktopShortcutSections(\n    nodeId: String, viewModel: RemoteDesktopViewModel, connected: Boolean,\n    scrollState: androidx.compose.foundation.ScrollState? = null, viewportPx: Int = 0\n) {')
s = sub(s, r'    fun move\(index: Int, delta: Int\) \{\n.*?\n    \}\n\n', '', 'move function')
s = rep(s, '    Text(stringResource(R.string.desktop_shortcuts_custom_title), style = MaterialTheme.typography.titleSmall)\n',
        '    Text(stringResource(R.string.desktop_shortcuts_custom_title), style = MaterialTheme.typography.titleSmall)\n'
        '    Text(stringResource(R.string.desktop_shortcuts_edit_hint), style = MaterialTheme.typography.bodySmall)\n')
s = sub(s, r'    entries\.forEachIndexed \{ index, shortcut ->\n.*?\n    \}\n(?=    DesktopShortcutEditorControls\(modifiers, selectedKey, showKeys,)',
        '    DesktopShortcutList(entries, connected,\n'
        '        onSend = { shortcut -> viewModel.sendKeyCombo(nodeId, *shortcut.keys.toIntArray()) },\n'
        '        onChange = { save(it) }, scrollState = scrollState, viewportPx = viewportPx)\n', 'shortcut rows')
for unused in ('KeyboardArrowUp', 'KeyboardArrowDown', 'Delete'):
    s = rep(s, 'import androidx.compose.material.icons.filled.%s\n' % unused, '')
sections_path.write_text(s, encoding='utf-8')

screen_path = MAIN / 'RemoteDesktopScreen.kt'
screen = screen_path.read_text(encoding='utf-8')
screen = sub(screen,
    r'Column\(modifier = Modifier\.weight\(1f, fill = false\)\.fillMaxWidth\(\)\.verticalScroll\(rememberScrollState\(\)\)\) \{(\n\s*Text\(stringResource\(R\.string\.send_direct_text\))',
    'val shortcutsScroll = rememberScrollState()\n'
    '                        var shortcutsViewportPx by remember { mutableIntStateOf(0) }\n'
    '                        Column(modifier = Modifier.weight(1f, fill = false).fillMaxWidth().onSizeChanged { shortcutsViewportPx = it.height }.verticalScroll(shortcutsScroll)) {\n'
    '                            Text(stringResource(R.string.send_direct_text)', 'keyboard dialog scroll column')
screen = rep(screen, 'DesktopShortcutSections(nodeId, viewModel, isConnected)', 'DesktopShortcutSections(nodeId, viewModel, isConnected, shortcutsScroll, shortcutsViewportPx)')
screen_path.write_text(screen, encoding='utf-8')

hints = {
    'values': 'Drag the handle to reorder and tap the cross to remove a shortcut.',
    'values-en': 'Drag the handle to reorder and tap the cross to remove a shortcut.',
    'values-es': 'Arrastra el asa para ordenar y pulsa la cruz para quitar un atajo.',
}
for folder, text in hints.items():
    (RES / folder / 'desktop_shortcuts_edit_hint.xml').write_text(
        '<resources>\n    <string name="desktop_shortcuts_edit_hint">%s</string>\n</resources>\n' % text, encoding='utf-8')

(MAIN / 'DesktopShortcutList.kt').write_text(r'''package iecxiv.meshfabric.ui.desktop

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
''', encoding='utf-8')

(ANDROID_TEST / 'DesktopShortcutListTest.kt').write_text('''package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesktopShortcutListTest {
    @get:Rule val compose = createComposeRule()
    private val first = DesktopShortcut(listOf(17, 65))
    private val second = DesktopShortcut(listOf(17, 67))
    private val third = DesktopShortcut(listOf(17, 86))
    private var current by mutableStateOf(listOf(first, second, third))
    private val sent = mutableListOf<DesktopShortcut>()

    private fun show() = compose.setContent {
        MaterialTheme { Column { DesktopShortcutList(current, true, { sent.add(it) }, { current = it }) } }
    }

    @Test fun crossRemovesOnlyThatShortcut() {
        show()
        compose.onNodeWithTag("desktop-shortcut-remove-1").performClick()
        compose.runOnIdle { assertEquals(listOf(first, third), current) }
    }

    @Test fun buttonStillSendsItsKeys() {
        show()
        compose.onNodeWithTag("desktop-shortcut-button-2").performClick()
        compose.runOnIdle { assertEquals(listOf(third), sent) }
    }

    @Test fun dragHandleMovesTheShortcutAndSavesOnRelease() {
        show()
        val rowPx = 44f * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        compose.onNodeWithTag("desktop-shortcut-handle-0").performTouchInput {
            down(center)
            moveBy(Offset(0f, rowPx * 0.8f))
            moveBy(Offset(0f, rowPx * 0.8f))
            moveBy(Offset(0f, rowPx * 0.8f))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(second, third, first), current) }
    }
}
''', encoding='utf-8')

forbidden = ['desktop_shortcuts_move_up\\b.*Icons', 'KeyboardArrowUp']
hits = subprocess.run(['grep', '-rIl', 'KeyboardArrowUp', str(MAIN / 'DesktopShortcutSections.kt')], capture_output=True, text=True).stdout.split()
if hits:
    fail('arrow icons still referenced in the sections file')
print('prepared OK')
