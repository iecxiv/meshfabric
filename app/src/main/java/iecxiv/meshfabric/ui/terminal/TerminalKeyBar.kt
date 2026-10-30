package iecxiv.meshfabric.ui.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R

private data class TerminalBarKey(val id: String, val label: Int, val description: Int = label)
private val primaryKeys = listOf(
    TerminalBarKey("esc", R.string.terminal_key_esc), TerminalBarKey("tab", R.string.terminal_key_tab),
    TerminalBarKey("ctrl-c", R.string.terminal_key_ctrl_c),
    TerminalBarKey("enter", R.string.terminal_key_enter_glyph, R.string.terminal_key_enter),
    TerminalBarKey("backspace", R.string.terminal_key_backspace_glyph, R.string.terminal_key_backspace)
)
private val navigationKeys = listOf(
    TerminalBarKey("left", R.string.terminal_key_left_glyph, R.string.terminal_key_left),
    TerminalBarKey("down", R.string.terminal_key_down_glyph, R.string.terminal_key_down),
    TerminalBarKey("up", R.string.terminal_key_up_glyph, R.string.terminal_key_up),
    TerminalBarKey("right", R.string.terminal_key_right_glyph, R.string.terminal_key_right),
    TerminalBarKey("home", R.string.terminal_key_home), TerminalBarKey("end", R.string.terminal_key_end),
    TerminalBarKey("insert", R.string.terminal_key_insert), TerminalBarKey("delete", R.string.terminal_key_delete),
    TerminalBarKey("pageup", R.string.terminal_key_pageup), TerminalBarKey("pagedown", R.string.terminal_key_pagedown)
)

@Composable
private fun BarButton(
    label: String, description: String, enabled: Boolean,
    largeLabel: Boolean = false, arrowRotation: Float? = null,
    chevronRotation: Float? = null, onClick: () -> Unit
) {
    val keyHeight = maxOf(36.dp, with(LocalDensity.current) { 24.sp.toDp() } + 8.dp)
    val labelStyle = if (largeLabel) MaterialTheme.typography.labelLarge.copy(
        fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold
    ) else MaterialTheme.typography.labelLarge
    FilterChip(
        selected = true,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics { contentDescription = description }
            .minimumInteractiveComponentSize().height(keyHeight).widthIn(min = 48.dp),
        label = {
            if (chevronRotation != null) Icon(
                Icons.Default.KeyboardArrowDown, contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(chevronRotation).testTag("terminal-chevron-${chevronRotation.toInt()}")
            ) else if (arrowRotation != null) Icon(
                Icons.Default.ArrowUpward, contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(arrowRotation).testTag("terminal-arrow-${arrowRotation.toInt()}")
            ) else Text(label, maxLines = 1, style = labelStyle)
        }
    )
}
@Composable
private fun KeyButton(key: TerminalBarKey, enabled: Boolean, onKey: (String) -> Unit) {
    val arrow = when (key.id) {
        "up" -> 0f
        "right" -> 90f
        "down" -> 180f
        "left" -> 270f
        else -> null
    }
    BarButton(
        stringResource(key.label), stringResource(key.description), enabled,
        largeLabel = key.id == "enter", arrowRotation = arrow,
        onClick = { onKey(key.id) }
    )
}

@Composable
internal fun TerminalKeyBar(
    enabled: Boolean, historyEnabled: Boolean, onKey: (String) -> Unit,
    onLatest: () -> Unit, onPopup: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var ctrlMenu by remember { mutableStateOf(false) }
    LaunchedEffect(ctrlMenu) { onPopup(ctrlMenu) }
    LaunchedEffect(enabled) { if (!enabled) ctrlMenu = false }
    DisposableEffect(Unit) { onDispose { onPopup(false) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                primaryKeys.forEach { KeyButton(it, enabled, onKey) }
            }
            BarButton(
                stringResource(if (expanded) R.string.terminal_keys_less_glyph else R.string.terminal_keys_more_glyph),
                stringResource(if (expanded) R.string.terminal_keys_less else R.string.terminal_keys_more), true,
                chevronRotation = if (expanded) 180f else 0f
            ) { expanded = !expanded; ctrlMenu = false }
        }
        if (expanded) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                navigationKeys.forEach { KeyButton(it, enabled, onKey) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    BarButton(stringResource(R.string.terminal_ctrl_more_label), stringResource(R.string.terminal_ctrl_more), enabled) { ctrlMenu = true }
                    DropdownMenu(expanded = ctrlMenu, onDismissRequest = { ctrlMenu = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                        ('A'..'Z').forEach { letter ->
                            DropdownMenuItem(text = { Text(stringResource(R.string.terminal_ctrl_shortcut, letter.toString())) },
                                onClick = { ctrlMenu = false; onPopup(false); onKey("ctrl-" + letter.lowercaseChar()) })
                        }
                    }
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf('Z', 'X', 'O', 'W').forEach { letter ->
                        val label = stringResource(R.string.terminal_ctrl_shortcut, letter.toString())
                        BarButton(label, label, enabled) { onKey("ctrl-" + letter.lowercaseChar()) }
                    }
                }
                BarButton(stringResource(R.string.terminal_latest_glyph), stringResource(R.string.terminal_latest), historyEnabled, largeLabel = true, onClick = onLatest)
            }
        }
    }
}
