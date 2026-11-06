package iecxiv.meshfabric.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.DeviceInventoryState
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.deviceInventory
import kotlinx.coroutines.delay

internal val LocalDeviceDetailsClient = staticCompositionLocalOf<MeshCentralClient?> { null }

@Composable
internal fun DeviceDetailsButton(node: MeshNode) {
    var visible by rememberSaveable(node.id) { mutableStateOf(false) }
    IconButton(onClick = { visible = true }) {
        Icon(Icons.Default.Info, stringResource(R.string.device_details_open, node.name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (visible) DeviceDetailsDialog(node) { visible = false }
}

@Composable
internal fun DeviceDetailsDialog(node: MeshNode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val source = LocalDeviceDetailsClient.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val spanish = LocalConfiguration.current.locales[0].language == "es"
    val connected = source?.connectionState?.collectAsState()?.value ?: false
    var retry by remember(node.id) { mutableIntStateOf(0) }
    val inventory by produceState(DeviceInventoryState(), source, node.id, retry, connected, lifecycle) {
        value = DeviceInventoryState()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (source == null || !connected || source.isUserLogout) {
                value = DeviceInventoryState(pending = emptySet(), errors = mapOf("connection" to "disconnected"))
            } else deviceInventory(source, node.id).collect { value = it }
        }
    }
    val sections = remember(inventory) { inventorySections(inventory) }
    var feedback by remember(node.id) { mutableStateOf("") }
    var feedbackVersion by remember(node.id) { mutableIntStateOf(0) }
    val copied = stringResource(R.string.device_details_copied)
    val failed = stringResource(R.string.device_details_copy_failed)
    val online = stringResource(R.string.device_details_online)
    val offline = stringResource(R.string.device_details_offline)
    val copyValue: (String, String) -> Unit = { label, value ->
        val success = runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: error("Clipboard unavailable")
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        }.isSuccess
        feedback = if (success) "$copied: $label" else failed
        feedbackVersion++
    }
    LaunchedEffect(feedbackVersion) { if (feedbackVersion > 0) { delay(2500); feedback = "" } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.device_details_title), style = MaterialTheme.typography.titleLarge)
                Text(node.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(if (spanish) "Inventario almacenado por MeshCentral; no es una lectura en tiempo real. Los campos y fechas conservan el formato del servidor."
                            else "Inventory stored by MeshCentral; not a live reading. Fields and timestamps retain the server format.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        InventoryHeading(if (spanish) "Identificación" else "Identification")
                        InventoryRow(stringResource(R.string.device_details_name), node.name, copyValue)
                        InventoryRow(stringResource(R.string.device_details_status), if (node.isOnline) online else offline, copyValue)
                        InventoryRow(stringResource(R.string.device_details_os), node.osdesc.orEmpty(), copyValue)
                        InventoryRow(stringResource(R.string.device_details_node_id), node.id, copyValue)
                        InventoryRow(stringResource(R.string.device_details_group_id), node.meshid, copyValue)
                    }
                    if (inventory.pending.isNotEmpty()) item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(if (spanish) "Consultando inventario…" else "Loading inventory…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (inventory.errors.isNotEmpty()) item {
                        val message = inventory.errors.entries.joinToString("\n") { (action, error) ->
                            val reason = when (error) {
                                "timeout" -> if (spanish) "Sin respuesta en 25 s; comprueba conexión y permisos." else "No response within 25 s; check connection and permissions."
                                "denied" -> if (spanish) "Solicitud rechazada por el servidor." else "Request rejected by server."
                                "oversize" -> if (spanish) "Respuesta demasiado grande para este visor." else "Response too large for this viewer."
                                else -> if (spanish) "Sin conexión o no se pudo completar la consulta." else "Disconnected or request could not be completed."
                            }
                            "$action: $reason"
                        }
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    sections.forEach { section ->
                        item(key = "heading_${section.key}") { InventoryHeading(inventoryTitle(section.key, spanish)) }
                        items(section.rows) { row -> InventoryRow(row.path, row.value, copyValue) }
                    }
                    if (inventory.pending.isEmpty() && sections.isEmpty()) item {
                        Text(if (spanish) "No se ha recibido inventario ampliado. No se han estimado ni inventado datos."
                            else "No extended inventory received. No data has been estimated or invented.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (feedback.isNotEmpty()) Text(feedback,
                    Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { retry++ }, enabled = connected && inventory.pending.isEmpty()) {
                        Text(if (spanish) "Volver a consultar" else "Reload")
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.device_details_close)) }
                }
            }
        }
    }
}

@Composable
private fun InventoryHeading(title: String) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun InventoryRow(label: String, value: String, onCopy: (String, String) -> Unit) {
    val copyLabel = stringResource(R.string.device_details_copy_value, label)
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().clickable(enabled = value.isNotBlank(), onClickLabel = copyLabel) { onCopy(label, value) }
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value.ifBlank { stringResource(R.string.device_details_unavailable) }, style = MaterialTheme.typography.bodyMedium)
            }
            if (value.isNotBlank()) Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
