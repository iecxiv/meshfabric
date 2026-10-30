package iecxiv.meshfabric.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.TerminalConnectionStatus
import iecxiv.meshfabric.data.remote.TerminalSession
import java.text.DateFormat
import java.util.Date

/** The anchor remains visible; actions and explanatory text live in the popup. */
@Composable
internal fun TerminalSessionPopup(
    status: TerminalConnectionStatus,
    attempt: ManualReconnectState,
    history: List<TerminalSession>,
    showingHistory: Boolean,
    truncated: Boolean,
    expanded: Boolean,
    deletionIds: Set<String>,
    onExpanded: (Boolean) -> Unit,
    onReconnect: () -> Unit,
    onCancel: () -> Unit,
    onCurrent: () -> Unit,
    onSelect: (String) -> Unit,
    onRequestDelete: (Set<String>) -> Unit,
    onDismissDelete: () -> Unit,
    onDelete: (Set<String>) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale) }
    val connecting = attempt == ManualReconnectState.CONNECTING
    val summary = stringResource(when {
        showingHistory -> R.string.terminal_previous_history
        connecting -> R.string.status_connecting
        status == TerminalConnectionStatus.CONNECTED -> R.string.status_connected
        status == TerminalConnectionStatus.CONNECTING -> R.string.status_connecting
        else -> R.string.status_disconnected
    })
    val details = when {
        showingHistory -> R.string.terminal_history_readonly
        connecting -> R.string.terminal_reconnect_pending
        status == TerminalConnectionStatus.DISCONNECTED -> R.string.terminal_disconnected_retained
        status == TerminalConnectionStatus.CONNECTED && history.isNotEmpty() -> R.string.terminal_new_session_connected
        else -> null
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            TextButton(onClick = { onExpanded(!expanded) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                Text(stringResource(R.string.terminal_session_popup_summary, summary, history.size), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                if (truncated) Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.terminal_retained_truncated), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Icon(Icons.Default.ExpandMore, contentDescription = stringResource(R.string.terminal_session_popup_title))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }, modifier = Modifier.width(320.dp).heightIn(max = 420.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.terminal_session_popup_title), style = MaterialTheme.typography.titleSmall)
                if (details != null) Text(stringResource(details), style = MaterialTheme.typography.bodySmall)
                if (!showingHistory && status == TerminalConnectionStatus.DISCONNECTED && (attempt == ManualReconnectState.FAILED || attempt == ManualReconnectState.TIMED_OUT))
                    Text(stringResource(R.string.terminal_reconnect_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (truncated) Text(stringResource(R.string.terminal_retained_truncated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (connecting) DropdownMenuItem(text = { Text(stringResource(R.string.terminal_recovery_cancel)) }, onClick = { onExpanded(false); onCancel() })
            else if (!showingHistory && status == TerminalConnectionStatus.DISCONNECTED)
                DropdownMenuItem(text = { Text(stringResource(R.string.terminal_reconnect_manual)) }, onClick = { onExpanded(false); onReconnect() })
            if (showingHistory) DropdownMenuItem(text = { Text(stringResource(R.string.terminal_return_current)) }, onClick = { onExpanded(false); onCurrent() })
            HorizontalDivider()
            Text(stringResource(R.string.terminal_previous_history), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            if (history.isEmpty()) Text(stringResource(R.string.terminal_history_empty), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
            history.asReversed().forEach { archived ->
                val label = dates.format(Date(archived.openedAt))
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onExpanded(false); onSelect(archived.localSessionId) }, modifier = Modifier.weight(1f)) {
                        Text(label, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onExpanded(false); onRequestDelete(setOf(archived.localSessionId)) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.terminal_history_delete_description, label), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (history.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.terminal_history_delete_all), color = MaterialTheme.colorScheme.error) }, onClick = { onExpanded(false); onRequestDelete(history.map { it.localSessionId }.toSet()) })
            }
        }
    }
    if (deletionIds.isNotEmpty()) {
        val single = history.firstOrNull { it.localSessionId in deletionIds }
        val label = single?.let { dates.format(Date(it.openedAt)) }.orEmpty()
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.terminal_history_delete_title)) },
            text = { Text(if (deletionIds.size == 1) stringResource(R.string.terminal_history_delete_one_warning, label) else stringResource(R.string.terminal_history_delete_many_warning, deletionIds.size)) },
            confirmButton = { TextButton(onClick = { val snapshot = deletionIds.toSet(); onDismissDelete(); onDelete(snapshot) }) { Text(stringResource(R.string.terminal_history_delete_action), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text(stringResource(R.string.terminal_recovery_cancel)) } }
        )
    }
}
