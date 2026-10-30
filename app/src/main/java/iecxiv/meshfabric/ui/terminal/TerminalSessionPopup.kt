package iecxiv.meshfabric.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
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
import iecxiv.meshfabric.ui.management.FabricDialog
import java.text.DateFormat
import java.util.Date

/** One themed, full-width panel; primary actions appear only in its header. */
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
    onDelete: (Set<String>) -> Unit,
    showBar: Boolean = true
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
    if (showBar) Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.terminal_session_popup_summary, summary, history.size), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                if (truncated) Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.terminal_retained_truncated), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                val primaryLabel = when {
                    connecting -> R.string.terminal_recovery_cancel
                    showingHistory -> R.string.terminal_return_current
                    status == TerminalConnectionStatus.DISCONNECTED -> R.string.terminal_reconnect_manual
                    else -> null
                }
                if (primaryLabel != null) TextButton(
                    onClick = {
                        onExpanded(false)
                        when {
                            connecting -> onCancel()
                            showingHistory -> onCurrent()
                            else -> { onCurrent(); onReconnect() }
                        }
                    },
                    modifier = Modifier.widthIn(max = 200.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text(stringResource(primaryLabel), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) }
                IconButton(onClick = { onExpanded(!expanded) }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = stringResource(R.string.terminal_session_popup_title))
                }
            }
            if (expanded) {
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (details != null) Text(stringResource(details), style = MaterialTheme.typography.bodySmall)
                        if (!showingHistory && status == TerminalConnectionStatus.DISCONNECTED && (attempt == ManualReconnectState.FAILED || attempt == ManualReconnectState.TIMED_OUT))
                            Text(stringResource(R.string.terminal_reconnect_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (truncated) Text(stringResource(R.string.terminal_retained_truncated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.terminal_previous_history), style = MaterialTheme.typography.labelLarge)
                        if (history.isEmpty()) Text(stringResource(R.string.terminal_history_empty), style = MaterialTheme.typography.bodySmall)
                    }
                    history.asReversed().forEach { archived ->
                        val label = dates.format(Date(archived.openedAt))
                        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onExpanded(false); onSelect(archived.localSessionId) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                                Text(label, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            }
                            IconButton(onClick = { onExpanded(false); onRequestDelete(setOf(archived.localSessionId)) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.terminal_history_delete_description, label), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        HorizontalDivider()
                        TextButton(onClick = { onExpanded(false); onRequestDelete(history.map { it.localSessionId }.toSet()) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
                            Text(stringResource(R.string.terminal_history_delete_all), modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (deletionIds.isNotEmpty()) {
        val single = history.firstOrNull { it.localSessionId in deletionIds }
        val label = single?.let { dates.format(Date(it.openedAt)) }.orEmpty()
        FabricDialog(
            title = stringResource(R.string.terminal_history_delete_title),
            onDismiss = onDismissDelete,
            confirm = stringResource(R.string.terminal_history_delete_action),
            cancel = stringResource(R.string.terminal_recovery_cancel),
            dangerous = true,
            onConfirm = {
                val snapshot = deletionIds.toSet()
                onDismissDelete()
                onDelete(snapshot)
            }
        ) {
            Text(if (deletionIds.size == 1) stringResource(R.string.terminal_history_delete_one_warning, label) else stringResource(R.string.terminal_history_delete_many_warning, deletionIds.size))
        }
    }
}
