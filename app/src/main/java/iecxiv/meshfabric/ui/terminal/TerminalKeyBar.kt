package iecxiv.meshfabric.ui.terminal

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.ui.theme.appCardBackground
import iecxiv.meshfabric.ui.theme.appCardTonalElevation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val KEY_BAR_PREFS = "terminal_key_bar"
private const val KEY_BAR_SCROLL = "scroll_x"

private data class TerminalBarKey(val id: String, val label: Int, val description: Int = label)
private val barKeys = listOf(
    TerminalBarKey("esc", R.string.terminal_key_esc), TerminalBarKey("tab", R.string.terminal_key_tab),
    TerminalBarKey("ctrl-c", R.string.terminal_key_ctrl_c),
    TerminalBarKey("enter", R.string.terminal_key_enter_glyph, R.string.terminal_key_enter),
    TerminalBarKey("backspace", R.string.terminal_key_backspace_glyph, R.string.terminal_key_backspace),
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
    largeLabel: Boolean = false, arrowRotation: Float? = null, onClick: () -> Unit
) {
    val labelStyle = if (largeLabel) MaterialTheme.typography.labelLarge.copy(
        fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold
    ) else MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp)
    FilterChip(
        selected = true,
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics { contentDescription = description }
            .minimumInteractiveComponentSize().widthIn(min = 48.dp),
        label = {
            if (arrowRotation != null) Icon(
                Icons.Default.ArrowUpward, contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(arrowRotation).testTag("terminal-arrow-${arrowRotation.toInt()}")
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
    val context = LocalContext.current
    val prefs = remember(context) { context.applicationContext.getSharedPreferences(KEY_BAR_PREFS, Context.MODE_PRIVATE) }
    val scroll = androidx.compose.foundation.rememberScrollState(remember(prefs) { prefs.getInt(KEY_BAR_SCROLL, 0).coerceAtLeast(0) })
    var ctrlMenu by remember { mutableStateOf(false) }
    LaunchedEffect(scroll, prefs) {
        snapshotFlow { scroll.value }.collectLatest { value ->
            delay(250L)
            prefs.edit().putInt(KEY_BAR_SCROLL, value).apply()
        }
    }
    DisposableEffect(scroll, prefs) { onDispose { prefs.edit().putInt(KEY_BAR_SCROLL, scroll.value).apply() } }
    LaunchedEffect(ctrlMenu) { onPopup(ctrlMenu) }
    LaunchedEffect(enabled) { if (!enabled) ctrlMenu = false }
    DisposableEffect(Unit) { onDispose { onPopup(false) } }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("terminal-key-bar"),
        shape = RoundedCornerShape(24.dp),
        color = appCardBackground(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        shadowElevation = 8.dp,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = appCardTonalElevation(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("terminal-key-bar-scroll"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            barKeys.forEach { KeyButton(it, enabled, onKey) }
            Box {
                BarButton(stringResource(R.string.terminal_ctrl_more_label), stringResource(R.string.terminal_ctrl_more), enabled) { ctrlMenu = true }
                DropdownMenu(expanded = ctrlMenu, onDismissRequest = { ctrlMenu = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                    ('A'..'Z').forEach { letter ->
                        DropdownMenuItem(text = { Text(stringResource(R.string.terminal_ctrl_shortcut, letter.toString())) },
                            onClick = { ctrlMenu = false; onPopup(false); onKey("ctrl-" + letter.lowercaseChar()) })
                    }
                }
            }
            listOf('Z', 'X', 'O', 'W').forEach { letter ->
                val label = stringResource(R.string.terminal_ctrl_shortcut, letter.toString())
                BarButton(label, label, enabled) { onKey("ctrl-" + letter.lowercaseChar()) }
            }
            BarButton(stringResource(R.string.terminal_latest_glyph), stringResource(R.string.terminal_latest), historyEnabled, largeLabel = true, onClick = onLatest)
        }
    }
}
