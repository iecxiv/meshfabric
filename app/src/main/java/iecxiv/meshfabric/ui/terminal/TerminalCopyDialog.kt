package iecxiv.meshfabric.ui.terminal

import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager as AndroidClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import iecxiv.meshfabric.R

internal fun terminalCopyClip(text: String): ClipData = ClipData.newPlainText("Terminal", text).apply {
    // Terminal output may contain secrets; hide the OS clipboard preview.
    description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
}

@Composable
internal fun TerminalCopyDialog(snapshot: TerminalCopySnapshot?, loading: Boolean, onDismiss: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val fallback = LocalClipboardManager.current
    val clipboard = remember(appContext, fallback) {
        object : ClipboardManager {
            override fun getText(): AnnotatedString? = fallback.getText()
            override fun setText(annotatedString: AnnotatedString) {
                val native = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboardManager
                native.setPrimaryClip(terminalCopyClip(annotatedString.text))
                if (Build.VERSION.SDK_INT < 33) Toast.makeText(appContext, R.string.terminal_copy_done, Toast.LENGTH_SHORT).show()
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.terminal_copy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.terminal_copy_help), style = MaterialTheme.typography.bodySmall)
                if (snapshot?.truncated == true) Text(
                    stringResource(R.string.terminal_copy_limited),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
                Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        snapshot == null -> Text(stringResource(R.string.terminal_copy_failed))
                        snapshot.text.isEmpty() -> Text(stringResource(R.string.terminal_copy_empty))
                        else -> CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                            SelectionContainer {
                                Text(snapshot.text, modifier = Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        enabled = snapshot != null && snapshot.text.isNotEmpty(),
                        onClick = { snapshot?.let { clipboard.setText(AnnotatedString(it.text)) } }
                    ) { Text(stringResource(R.string.terminal_copy_all)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
        }
    }
}
