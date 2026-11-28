package iecxiv.meshfabric.ui.desktop

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DesktopShortcutSections(
    nodeId: String, viewModel: RemoteDesktopViewModel, connected: Boolean,
    scrollState: androidx.compose.foundation.ScrollState? = null, viewportPx: Int = 0
) {
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
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(stringResource(R.string.desktop_shortcuts_hold_title), style = MaterialTheme.typography.titleSmall)
    FlowRow(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        desktopHoldKeys.forEach { key ->
            FilterChip(selected = key in held, enabled = connected,
                onClick = { viewModel.toggleDesktopHold(nodeId, key) },
                label = { Text(desktopKeyDisplayLabel(key)) })
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        OutlinedButton(onClick = { viewModel.releaseDesktopHolds(nodeId) }, enabled = held.isNotEmpty()) {
            Text(stringResource(R.string.desktop_shortcuts_release_all))
        }
    }
    Text(stringResource(R.string.desktop_shortcuts_hold_hint),
        style = MaterialTheme.typography.bodySmall)

    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(stringResource(R.string.desktop_shortcuts_custom_title), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.desktop_shortcuts_edit_hint), style = MaterialTheme.typography.bodySmall)
    DesktopShortcutList(entries, connected,
        onSend = { shortcut -> viewModel.sendKeyCombo(nodeId, *shortcut.keys.toIntArray()) },
        onChange = { save(it) }, scrollState = scrollState, viewportPx = viewportPx)
    DesktopShortcutEditorControls(modifiers, selectedKey, showKeys,
        onModifierChange = { key, checked ->
            modifiers = if (checked) modifiers + key else modifiers - key
        },
        onExpandedChange = { showKeys = it },
        onSelected = { selectedKey = it; showKeys = false })
    val candidate = desktopHoldKeys.filter { it in modifiers } + listOfNotNull(selectedKey)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Button(enabled = candidate.isNotEmpty() && entries.size < 64 && entries.none { it.keys == candidate },
            onClick = { save(entries + DesktopShortcut(candidate)) }) { Text(stringResource(R.string.desktop_shortcuts_add)) }
    }
    TextButton(onClick = { save(DesktopShortcuts.defaults) }) { Text(stringResource(R.string.desktop_shortcuts_restore_defaults)) }
}


@Composable
private fun ShortcutModifierChecks(
    selected: Set<Int>,
    onCheckedChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier,
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
                Text(desktopKeyDisplayLabel(key), fontSize = 11.sp, maxLines = 1)
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
    Box(modifier.wrapContentSize(Alignment.TopStart)) {
        OutlinedButton(onClick = { onExpandedChange(true) }, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            Text(if (selectedKey == null) stringResource(R.string.desktop_shortcuts_no_key) else desktopKeyDisplayLabel(selectedKey), fontSize = 11.sp,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) },
            offset = DpOffset.Zero, modifier = Modifier.heightIn(max = 260.dp)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.desktop_shortcuts_no_key)) }, onClick = { onSelected(null) })
            DesktopShortcuts.selectableKeys.forEach { key ->
                DropdownMenuItem(text = { Text(desktopKeyDisplayLabel(key)) }, onClick = { onSelected(key) })
            }
        }
    }
}


@Composable
internal fun DesktopShortcutEditorControls(
    modifiers: Set<Int>,
    selectedKey: Int?,
    expanded: Boolean,
    onModifierChange: (Int, Boolean) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val modifierScroll = rememberScrollState()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    LaunchedEffect(modifierScroll.value) { onExpandedChange(false) }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val pickerWidth = (124f * fontScale).dp.coerceAtMost(maxWidth * 0.5f)
        val editorWidth = (336f * fontScale + 4f).dp.coerceAtMost(maxWidth)
        Row(Modifier.width(editorWidth).testTag("shortcut-editor-row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ShortcutModifierChecks(modifiers, onModifierChange,
                Modifier.weight(1f).testTag("shortcut-modifier-viewport").horizontalScroll(modifierScroll))
            ShortcutKeyPicker(selectedKey, expanded, onExpandedChange, onSelected,
                Modifier.width(pickerWidth).testTag("shortcut-key-picker"))
        }
    }
}
