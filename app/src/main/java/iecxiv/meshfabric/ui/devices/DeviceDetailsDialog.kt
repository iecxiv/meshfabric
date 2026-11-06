package iecxiv.meshfabric.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.remote.DeviceInventoryState
import iecxiv.meshfabric.data.remote.MeshCentralClient
import iecxiv.meshfabric.data.remote.deviceInventory
import kotlinx.coroutines.delay
import java.time.ZoneId

internal val LocalDeviceDetailsClient = staticCompositionLocalOf<MeshCentralClient?> { null }

@Composable
internal fun DeviceDetailsButton(node: MeshNode) {
    var visible by rememberSaveable(node.id) { mutableStateOf(false) }
    IconButton(onClick = { visible = true }) {
        Icon(Icons.Default.Info, stringResource(R.string.device_details_open, node.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (visible) DeviceDetailsDialog(node) { visible = false }
}

@Composable
internal fun DeviceDetailsDialog(node: MeshNode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val es = locale.language == "es"
    val zone = ZoneId.systemDefault()
    val source = LocalDeviceDetailsClient.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val connected = source?.connectionState?.collectAsState()?.value ?: false
    var retry by remember(node.id) { mutableIntStateOf(0) }
    var technical by rememberSaveable(node.id) { mutableStateOf(false) }
    val inventory by produceState(DeviceInventoryState(), source, node.id, retry, connected, lifecycle) {
        value = DeviceInventoryState()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (source == null || !connected || source.isUserLogout)
                value = DeviceInventoryState(pending = emptySet(), errors = mapOf("connection" to "disconnected"))
            else deviceInventory(source, node.id).collect { value = it }
        }
    }
    val sections = remember(inventory) { inventorySections(inventory) }
    val identity = listOf(
        InventoryValue(stringResource(R.string.device_details_name), node.name),
        InventoryValue(stringResource(R.string.device_details_os), node.osdesc.orEmpty()),
        InventoryValue(stringResource(R.string.device_details_node_id), node.id),
        InventoryValue(stringResource(R.string.device_details_group_id), node.meshid)
    )
    var feedback by remember(node.id) { mutableStateOf("") }
    var feedbackVersion by remember(node.id) { mutableIntStateOf(0) }
    val copied = stringResource(R.string.device_details_copied)
    val failed = stringResource(R.string.device_details_copy_failed)
    val copyValue: (String, String) -> Unit = { label, value ->
        val success = runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: error("Clipboard unavailable")
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        }.isSuccess
        feedback = if (success) "$copied: $label" else failed
        feedbackVersion++
    }
    LaunchedEffect(feedbackVersion) { if (feedbackVersion > 0) { delay(2500); feedback = "" } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 600.dp).fillMaxWidth().heightIn(max = (configuration.screenHeightDp * 0.88f).dp)) {
            Column {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Computer, null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(node.name.ifBlank { stringResource(R.string.unknown_device) }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.device_details_title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.device_details_close)) }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = if (node.isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(stringResource(if (node.isOnline) R.string.device_details_online else R.string.device_details_offline),
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(if (es) "Toca para copiar · Mantén pulsado para copiar el original" else "Tap to copy · Hold to copy the original",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    item {
                        Text(if (es) "Últimos datos guardados por MeshCentral. No es una lectura en tiempo real."
                            else "Latest inventory stored by MeshCentral. Not a live reading.", Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (es) "Mostrar nombres técnicos" else "Show technical names", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = technical, onCheckedChange = { technical = it })
                        }
                    }
                    item(key = "identity_heading") { InventoryHeading(if (es) "Identificación" else "Identification", Icons.Default.Badge) }
                    itemsIndexed(identity) { index, row ->
                        InventoryRow(ReadableInventoryValue(row.path, "", row.value), row, technical = false,
                            first = index == 0, last = index == identity.lastIndex, es = es, onCopy = copyValue)
                    }
                    if (inventory.pending.isNotEmpty()) item {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(if (es) "Cargando detalles…" else "Loading details…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (inventory.errors.isNotEmpty()) item {
                        Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                inventory.errors.forEach { (action, error) ->
                                    val title = when (action) {
                                        "getsysinfo" -> if (es) "Hardware y sistema" else "Hardware and system"
                                        "getnetworkinfo" -> if (es) "Redes" else "Networks"
                                        else -> if (es) "Conexión" else "Connection"
                                    }
                                    val reason = when (error) {
                                        "timeout" -> if (es) "Sin respuesta en 25 s. Comprueba conexión y permisos." else "No response within 25 s. Check connection and permissions."
                                        "denied" -> if (es) "El servidor ha rechazado la consulta." else "The server rejected the request."
                                        "oversize" -> if (es) "La respuesta supera el límite del visor." else "The response exceeds the viewer limit."
                                        else -> if (es) "No se ha podido completar la consulta." else "Could not complete the request."
                                    }
                                    Text("$title · $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                    sections.forEach { section ->
                        item(key = "heading_${section.key}") { InventoryHeading(sectionTitle(section.key, es), sectionIcon(section.key)) }
                        itemsIndexed(section.rows) { index, row ->
                            val readable = readableInventory(row, section.key, locale, zone)
                            val previous = section.rows.getOrNull(index - 1)?.let { readableInventory(it, section.key, locale, zone).context }
                            if (readable.context.isNotBlank() && readable.context != previous)
                                Text(readable.context, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 6.dp),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            InventoryRow(readable, row, technical, index == 0, index == section.rows.lastIndex, es, copyValue)
                        }
                    }
                    if (inventory.pending.isEmpty() && sections.isEmpty()) item {
                        Text(if (es) "No hay inventario ampliado disponible." else "No extended inventory available.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (feedback.isNotEmpty()) Text(feedback, Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { retry++ }, enabled = connected && inventory.pending.isEmpty()) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (es) "Actualizar" else "Reload")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.device_details_close)) }
                }
            }
        }
    }
}

private fun sectionTitle(key: String, es: Boolean) = when (key) {
    "cpu" -> if (es) "Procesador" else "Processor"
    "gpu" -> if (es) "Gráficos" else "Graphics"
    "metadata" -> if (es) "Actualización del inventario" else "Inventory update"
    else -> inventoryTitle(key, es)
}
private fun sectionIcon(key: String): ImageVector = when (key) {
    "os" -> Icons.Default.Computer
    "agent" -> Icons.Default.Settings
    "connection", "metadata" -> Icons.Default.Schedule
    "network" -> Icons.Default.Wifi
    "bios", "board", "cpu" -> Icons.Default.Memory
    "gpu" -> Icons.Default.DesktopWindows
    "tpm" -> Icons.Default.Security
    "memory" -> Icons.Default.Memory
    "storage" -> Icons.Default.Storage
    "system" -> Icons.Default.Devices
    else -> Icons.Default.Info
}

@Composable
private fun InventoryHeading(title: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 22.dp, bottom = 10.dp).semantics { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InventoryRow(readable: ReadableInventoryValue, raw: InventoryValue, technical: Boolean, first: Boolean, last: Boolean, es: Boolean, onCopy: (String, String) -> Unit) {
    val available = raw.value.isNotBlank()
    val shape = RoundedCornerShape(topStart = if (first) 14.dp else 0.dp, topEnd = if (first) 14.dp else 0.dp,
        bottomStart = if (last) 14.dp else 0.dp, bottomEnd = if (last) 14.dp else 0.dp)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Column {
            Row(Modifier.fillMaxWidth().combinedClickable(enabled = available,
                onClickLabel = if (es) "Copiar ${readable.label}" else "Copy ${readable.label}",
                onLongClickLabel = if (es) "Copiar valor original" else "Copy original value",
                onLongClick = { onCopy(readable.label, raw.value) }, onClick = { onCopy(readable.label, readable.value) })
                .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(readable.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(readable.value.ifBlank { stringResource(R.string.device_details_unavailable) }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (technical) Text(raw.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (available) Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
            }
            if (!last) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
    }
}
