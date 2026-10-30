package iecxiv.meshfabric.ui.files

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.remote.WindowsDriveEntry

/** Navigation-only drive row: no file mutation context menu. */
@Composable internal fun WindowsDriveRow(drive: WindowsDriveEntry, onOpen: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onOpen), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(when (drive.type) { "REMOVABLE" -> Icons.Default.Usb; "CDROM" -> Icons.Default.Album; else -> Icons.Default.Storage }, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(drive.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val total = drive.capacity
                if (total != null) {
                    val capacity = Formatter.formatFileSize(context, total)
                    val free = drive.free
                    Text(if (free != null) stringResource(R.string.files_drive_free_total, Formatter.formatFileSize(context, free), capacity) else stringResource(R.string.files_drive_capacity, capacity), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
