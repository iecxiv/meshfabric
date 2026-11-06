package iecxiv.meshfabric.ui.desktop

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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
            TextButton(onClick = { viewModel.sendKeyCombo(nodeId, *shortcut.keys.toIntArray()) },
                enabled = connected, modifier = Modifier.weight(1f)) {
                Text(shortcut.label)
            }
            TextButton(onClick = { move(index, -1) }, enabled = index > 0,
                contentPadding = PaddingValues(4.dp)) { Text("Subir") }
            TextButton(onClick = { move(index, 1) }, enabled = index < entries.lastIndex,
                contentPadding = PaddingValues(4.dp)) { Text("Bajar") }
            TextButton(onClick = { save(entries.filterIndexed { i, _ -> i != index }) },
                contentPadding = PaddingValues(4.dp)) { Text("Quitar") }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        desktopHoldKeys.forEach { key ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = key in modifiers, onCheckedChange = { checked ->
                    modifiers = if (checked) modifiers + key else modifiers - key
                })
                Text(desktopKeyLabel(key))
            }
        }
    }
    Box {
        OutlinedButton(onClick = { showKeys = true }) {
            Text(selectedKey?.let(::desktopKeyLabel) ?: "Ninguna tecla")
        }
        DropdownMenu(expanded = showKeys, onDismissRequest = { showKeys = false },
            modifier = Modifier.heightIn(max = 260.dp)) {
            DropdownMenuItem(text = { Text("Ninguna tecla") }, onClick = { selectedKey = null; showKeys = false })
            DesktopShortcuts.selectableKeys.forEach { key ->
                DropdownMenuItem(text = { Text(desktopKeyLabel(key)) },
                    onClick = { selectedKey = key; showKeys = false })
            }
        }
    }
    val candidate = desktopHoldKeys.filter { it in modifiers } + listOfNotNull(selectedKey)
    Button(enabled = candidate.isNotEmpty() && entries.size < 64 && entries.none { it.keys == candidate },
        onClick = { save(entries + DesktopShortcut(candidate)) }) { Text("Añadir") }
    TextButton(onClick = { save(DesktopShortcuts.defaults) }) { Text("Restaurar atajos predeterminados") }
}
