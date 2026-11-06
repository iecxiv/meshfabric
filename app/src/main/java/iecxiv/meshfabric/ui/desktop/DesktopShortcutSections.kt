package iecxiv.meshfabric.ui.desktop

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopShortcutSections(nodeId: String, viewModel: RemoteDesktopViewModel, connected: Boolean) {
    val held by viewModel.heldDesktopModifiers(nodeId).collectAsState()
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { context.getSharedPreferences("desktop_shortcuts_v1", Context.MODE_PRIVATE) }
    var entries by remember(preferences) { mutableStateOf(DesktopShortcuts.decode(preferences.getString("items", null))) }
    var modifiers by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selectedKey by remember { mutableStateOf<Int?>(null) }
    var showKeys by remember { mutableStateOf(false) }
    fun save(items: List<DesktopShortcut>) {
        entries = items
        preferences.edit().putString("items", DesktopShortcuts.encode(items)).apply()
    }
    fun move(index: Int, delta: Int) {
        val target = index + delta
        if (target !in entries.indices) return
        val updated = entries.toMutableList()
        val value = updated.removeAt(index)
        updated.add(target, value)
        save(updated)
    }

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Hold · teclas mantenidas", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        desktopHoldKeys.forEach { key ->
            FilterChip(selected = key in held, enabled = connected,
                onClick = { viewModel.toggleDesktopHold(nodeId, key) },
                label = { Text(desktopKeyLabel(key)) })
        }
    }
    OutlinedButton(onClick = { viewModel.releaseDesktopHolds(nodeId) }, enabled = held.isNotEmpty()) {
        Text("Soltar todas")
    }
    Text("Se mantienen al cerrar este diálogo. Al salir se intenta liberarlas; no se reactivan al reconectar.",
        style = MaterialTheme.typography.bodySmall)

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Atajos personalizables", style = MaterialTheme.typography.titleSmall)
    entries.forEachIndexed { index, shortcut ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = true,
                onClick = { viewModel.sendKeyCombo(nodeId, *shortcut.keys.toIntArray()) },
                enabled = connected, modifier = Modifier.weight(1f).heightIn(min = 32.dp),
                label = { Text(shortcut.label, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) })
            IconButton(onClick = { move(index, -1) }, enabled = index > 0,
                modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir ${shortcut.label}", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { move(index, 1) }, enabled = index < entries.lastIndex,
                modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar ${shortcut.label}", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { save(entries.filterIndexed { i, _ -> i != index }) },
                modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Quitar ${shortcut.label}", modifier = Modifier.size(20.dp))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val modifierWidth = (212f * fontScale).dp
        val inlinePicker = maxWidth >= modifierWidth + (112f * fontScale).dp + 4.dp
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ShortcutModifierChecks(modifiers, { key, checked ->
                    modifiers = if (checked) modifiers + key else modifiers - key
                }, if (inlinePicker) Modifier.width(modifierWidth) else Modifier.fillMaxWidth())
                if (inlinePicker) ShortcutKeyPicker(selectedKey, showKeys,
                    { showKeys = it }, { selectedKey = it; showKeys = false }, Modifier.weight(1f))
            }
            if (!inlinePicker) {
                Spacer(Modifier.height(4.dp))
                ShortcutKeyPicker(selectedKey, showKeys, { showKeys = it },
                    { selectedKey = it; showKeys = false }, Modifier.fillMaxWidth())
            }
        }
    }
    val candidate = desktopHoldKeys.filter { it in modifiers } + listOfNotNull(selectedKey)
    Button(enabled = candidate.isNotEmpty() && entries.size < 64 && entries.none { it.keys == candidate },
        onClick = { save(entries + DesktopShortcut(candidate)) }) { Text("Añadir") }
    TextButton(onClick = { save(DesktopShortcuts.defaults) }) { Text("Restaurar atajos predeterminados") }
}


@Composable
private fun ShortcutModifierChecks(
    selected: Set<Int>,
    onCheckedChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        desktopHoldKeys.forEach { key ->
            val checked = key in selected
            Row(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox,
                    onValueChange = { onCheckedChange(key, it) })
                .padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(2.dp))
                Text(desktopKeyLabel(key), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShortcutKeyPicker(
    selectedKey: Int?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        OutlinedButton(onClick = { onExpandedChange(true) }, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text(selectedKey?.let(::desktopKeyLabel) ?: "Ninguna tecla", fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = 260.dp)) {
            DropdownMenuItem(text = { Text("Ninguna tecla") }, onClick = { onSelected(null) })
            DesktopShortcuts.selectableKeys.forEach { key ->
                DropdownMenuItem(text = { Text(desktopKeyLabel(key)) }, onClick = { onSelected(key) })
            }
        }
    }
}
