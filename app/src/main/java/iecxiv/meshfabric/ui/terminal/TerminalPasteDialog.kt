package iecxiv.meshfabric.ui.terminal

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R

internal data class TerminalPasteDraft(val text: String?, val issue: TerminalPasteIssue?)
internal fun readTerminalPaste(context: Context): TerminalPasteDraft = try {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip
    val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text else null
    val issue = TerminalPastePolicy.issue(value)
    TerminalPasteDraft(if (issue == null) value.toString() else null, issue)
} catch (_: RuntimeException) { TerminalPasteDraft(null, TerminalPasteIssue.CLIPBOARD) }

@Composable
internal fun TerminalPasteDialog(
    text: String?, issue: TerminalPasteIssue?, enabled: Boolean, attempted: Boolean,
    busy: Boolean, result: TerminalPasteResult?, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    var acknowledged by remember(text) { mutableStateOf(false) }
    val multiline = text != null && TerminalPastePolicy.multiline(text)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.terminal_paste_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.terminal_paste_warning))
                if (text != null) {
                    Text(stringResource(R.string.terminal_paste_count, text.length))
                    Text(text, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()))
                }
                if (issue != null) Text(stringResource(when (issue) {
                    TerminalPasteIssue.EMPTY -> R.string.terminal_paste_empty
                    TerminalPasteIssue.TOO_LARGE -> R.string.terminal_paste_large
                    TerminalPasteIssue.CLIPBOARD -> R.string.terminal_paste_clipboard
                    else -> R.string.terminal_paste_controls
                }), color = MaterialTheme.colorScheme.error)
                if (multiline) Row {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it }, enabled = !attempted)
                    Text(stringResource(R.string.terminal_paste_multiline))
                }
                if (busy) CircularProgressIndicator()
                if (result != null && result != TerminalPasteResult.DISPATCHED) Text(
                    stringResource(if (result == TerminalPasteResult.UNKNOWN) R.string.terminal_paste_unknown else R.string.terminal_paste_rejected),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled && !attempted && issue == null && text != null && (!multiline || acknowledged)) {
                Text(stringResource(R.string.terminal_paste_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.close)) } }
    )
}
