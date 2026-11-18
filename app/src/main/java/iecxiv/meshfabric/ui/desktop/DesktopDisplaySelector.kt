package iecxiv.meshfabric.ui.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.DesktopDisplayProtocol
import iecxiv.meshfabric.data.remote.DesktopDisplayState

@Composable
internal fun DesktopDisplaySelector(
    state: DesktopDisplayState,
    connected: Boolean,
    onOpen: () -> Unit,
    onSelect: (Int, Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuGeneration by remember { mutableStateOf(0L) }
    LaunchedEffect(connected, state.connectionGeneration) {
        if (!connected) expanded = false
        else if (expanded) {
            if (menuGeneration == 0L) menuGeneration = state.connectionGeneration
            else if (menuGeneration != state.connectionGeneration) expanded = false
        }
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val insetHeight = with(density) { (insets.getTop(this) + insets.getBottom(this)).toDp() }
    val maxHeight = (configuration.screenHeightDp.dp - insetHeight - 48.dp).coerceAtLeast(0.dp)
    val activeBlue = Color(0xFF2196F3)
    val ordinary = MaterialTheme.colorScheme.onSurface
    val ids = state.ids.filter { it != DesktopDisplayProtocol.ALL }.sorted() +
        state.ids.filter { it == DesktopDisplayProtocol.ALL }
    Box {
        IconButton(
            onClick = { menuGeneration = state.connectionGeneration; expanded = true; onOpen() },
            enabled = connected,
            modifier = Modifier.size(38.dp).testTag("desktop_display_selector")
        ) {
            Icon(
                Icons.Default.DesktopWindows,
                stringResource(when {
                    state.selectionUnconfirmed -> R.string.desktop_display_selector_unconfirmed
                    state.pendingId != null -> R.string.desktop_display_selector_applying
                    else -> R.string.desktop_display_selector_title
                }),
                Modifier.size(20.dp),
                tint = if (state.selectionUnconfirmed) MaterialTheme.colorScheme.error else LocalContentColor.current
            )
        }
        DropdownMenu(
            expanded = expanded && connected,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = maxHeight).testTag("desktop_display_menu"),
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (ids.isEmpty()) DropdownMenuItem(
                text = { Text(stringResource(R.string.desktop_display_selector_unavailable)) },
                enabled = false,
                onClick = {}
            )
            if (state.pendingId != null) DropdownMenuItem(
                text = { Text(stringResource(R.string.desktop_display_selector_applying)) },
                enabled = false,
                onClick = {}
            )
            if (state.selectionUnconfirmed) DropdownMenuItem(
                text = { Text(stringResource(R.string.desktop_display_selector_unconfirmed), color = MaterialTheme.colorScheme.error) },
                enabled = false,
                onClick = {}
            )
            ids.forEach { id ->
                val selected = state.selectedId == id
                val color = if (selected) activeBlue else ordinary
                DropdownMenuItem(
                    text = {
                        Text(
                            if (id == DesktopDisplayProtocol.ALL) stringResource(R.string.desktop_display_selector_all)
                            else stringResource(R.string.desktop_display_selector_number, id),
                            color = color
                        )
                    },
                    leadingIcon = {
                        Icon(if (selected) Icons.Default.Check else Icons.Default.DesktopWindows, null,
                            modifier = Modifier.size(20.dp), tint = color)
                    },
                    enabled = connected && state.pendingId == null,
                    modifier = Modifier.testTag("desktop_display_$id").semantics { this.selected = selected },
                    onClick = {
                        expanded = false
                        onSelect(id, if (menuGeneration == 0L) state.connectionGeneration else menuGeneration)
                    }
                )
            }
        }
    }
}
