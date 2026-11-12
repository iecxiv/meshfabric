package iecxiv.meshfabric.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    IconButton(onClick = { visible = true }, modifier = Modifier.offset(x = (-6).dp)) {
        Icon(Icons.Default.Info, stringResource(R.string.device_details_open, node.name), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var expanded by rememberSaveable(node.id) { mutableStateOf(listOf("os")) }
    var expandIncoming by rememberSaveable(node.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val inventory by produceState(DeviceInventoryState(), source, node.id, retry, connected, lifecycle) {
        value = DeviceInventoryState()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (source == null || !connected || source.isUserLogout)
                value = DeviceInventoryState(pending = emptySet(), errors = mapOf("connection" to "disconnected"))
            else deviceInventory(source, node.id).collect { value = it }
        }
    }
    val identityTitle = if (es) "Identificación" else "Identification"
    val identity = listOf(
        InventoryValue(stringResource(R.string.device_details_name), node.name),
        InventoryValue(stringResource(R.string.device_details_node_id), node.id),
        InventoryValue(stringResource(R.string.device_details_group_id), node.meshid)
    )
    val order = listOf("os", "cpu", "gpu", "memory", "storage", "network", "system", "board", "bios", "tpm", "agent", "connection", "metadata", "extra")
    val sections = remember(inventory) {
        inventorySections(inventory).sortedBy { order.indexOf(it.key).takeIf { it >= 0 } ?: Int.MAX_VALUE }
    }
    val keys = listOf("identity") + sections.map { it.key }
    LaunchedEffect(keys, expandIncoming) {
        if (expandIncoming) expanded = (expanded + keys).distinct()
    }
    val toggle: (String) -> Unit = { key ->
        expandIncoming = false
        expanded = if (key in expanded) expanded - key else expanded + key
    }
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
        iecxiv.meshfabric.ui.components.AppDialogSurface(shape = iecxiv.meshfabric.ui.components.AppDialogAppearance.shape, color = iecxiv.meshfabric.ui.components.AppDialogAppearance.containerColor(),
            tonalElevation = iecxiv.meshfabric.ui.components.AppDialogAppearance.tonalElevation, modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 600.dp).fillMaxWidth().heightIn(max = (configuration.screenHeightDp * 0.88f).dp)) {
            Column {
                Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Computer, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(node.name.ifBlank { stringResource(R.string.unknown_device) }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.device_details_title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, if (es) "Opciones de visualización" else "Display options")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(if (es) "Expandir todo" else "Expand all") },
                                    leadingIcon = { Icon(Icons.Default.UnfoldMore, null) },
                                    onClick = { expanded = keys; expandIncoming = true; menu = false })
                                DropdownMenuItem(text = { Text(if (es) "Contraer todo" else "Collapse all") },
                                    leadingIcon = { Icon(Icons.Default.UnfoldLess, null) },
                                    onClick = { expanded = emptyList(); expandIncoming = false; menu = false })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(if (es) "Mostrar nombres técnicos" else "Show technical names") },
                                    trailingIcon = { Checkbox(checked = technical, onCheckedChange = null) },
                                    onClick = { technical = !technical; menu = false })
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.device_details_close)) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = if (node.isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(stringResource(if (node.isOnline) R.string.device_details_online else R.string.device_details_offline),
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                        }
                        if (!node.osdesc.isNullOrBlank()) Text(node.osdesc.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (es) "Toca para copiar · Mantén pulsado para copiar el original" else "Tap to copy · Hold to copy the original",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    item(key = "inventory_notice") {
                        Text(if (es) "Inventario guardado por MeshCentral; no es una lectura en tiempo real."
                            else "Inventory stored by MeshCentral; not a live reading.", Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (inventory.pending.isNotEmpty()) item(key = "loading") {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(if (es) "Cargando detalles…" else "Loading details…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (inventory.errors.isNotEmpty()) item(key = "errors") {
                        Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(10.dp), color = iecxiv.meshfabric.ui.theme.appCardBackground(
MaterialTheme.colorScheme.errorContainer
),
    contentColor = androidx.compose.material3.contentColorFor(
MaterialTheme.colorScheme.errorContainer
)
,
    tonalElevation = iecxiv.meshfabric.ui.theme.appCardTonalElevation(
androidx.compose.ui.unit.Dp(0f)
)
) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    item(key = "identity_heading") {
                        InventoryHeading(identityTitle, Icons.Default.Badge, "identity" in expanded, es) { toggle("identity") }
                    }
                    if ("identity" in expanded) itemsIndexed(identity, key = { index, _ -> "identity_row_$index" }) { index, row ->
                        InventoryRow(ReadableInventoryValue(row.path, "", row.value), row, false,
                            index == 0, index == identity.lastIndex, es, copyValue)
                    }
                    sections.forEach { section ->
                        item(key = "heading_${section.key}") {
                            InventoryHeading(sectionTitle(section.key, es), sectionIcon(section.key),
                                section.key in expanded, es) { toggle(section.key) }
                        }
                        if (section.key in expanded) itemsIndexed(section.rows, key = { index, _ -> "${section.key}_row_$index" }) { index, row ->
                            val readable = readableInventory(row, section.key, locale, zone)
                            val previous = section.rows.getOrNull(index - 1)?.let { readableInventory(it, section.key, locale, zone).context }
                            val next = section.rows.getOrNull(index + 1)?.let { readableInventory(it, section.key, locale, zone).context }
                            val start = index == 0 || readable.context != previous
                            val end = index == section.rows.lastIndex || readable.context != next
                            if (readable.context.isNotBlank() && start)
                                Text(readable.context, Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            InventoryRow(readable, row, technical, start, end, es, copyValue)
                        }
                    }
                    if (inventory.pending.isEmpty() && sections.isEmpty()) item(key = "empty") {
                        Text(if (es) "No hay inventario ampliado disponible." else "No extended inventory available.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (feedback.isNotEmpty()) Text(feedback, Modifier.padding(horizontal = 20.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { retry++ }, enabled = connected && inventory.pending.isEmpty()) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (es) "Actualizar" else "Reload")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.device_details_close)) }
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
    "bios", "board", "cpu", "memory" -> Icons.Default.Memory
    "gpu" -> Icons.Default.DesktopWindows
    "tpm" -> Icons.Default.Security
    "storage" -> Icons.Default.Storage
    "system" -> Icons.Default.Devices
    else -> Icons.Default.Info
}

@Composable
private fun InventoryHeading(title: String, icon: ImageVector, expanded: Boolean, es: Boolean, onToggle: () -> Unit) {
    val state = if (expanded) { if (es) "Expandido" else "Expanded" } else { if (es) "Contraído" else "Collapsed" }
    val action = if (expanded) { if (es) "Contraer" else "Collapse" } else { if (es) "Expandir" else "Expand" }
    Surface(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(12.dp),
        color = iecxiv.meshfabric.ui.theme.appCardBackground(
if (expanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
),
    contentColor = androidx.compose.material3.contentColorFor(
if (expanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
)
,
    tonalElevation = iecxiv.meshfabric.ui.theme.appCardTonalElevation(
androidx.compose.ui.unit.Dp(0f)
)
) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "$action $title", onClick = onToggle)
            .semantics { heading(); stateDescription = state }.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InventoryRow(readable: ReadableInventoryValue, raw: InventoryValue, technical: Boolean, first: Boolean, last: Boolean, es: Boolean, onCopy: (String, String) -> Unit) {
    val available = raw.value.isNotBlank()
    val shape = RoundedCornerShape(topStart = if (first) 12.dp else 0.dp, topEnd = if (first) 12.dp else 0.dp,
        bottomStart = if (last) 12.dp else 0.dp, bottomEnd = if (last) 12.dp else 0.dp)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)) {
        Column {
            Column(Modifier.fillMaxWidth().combinedClickable(enabled = available,
                onClickLabel = if (es) "Copiar ${readable.label}" else "Copy ${readable.label}",
                onLongClickLabel = if (es) "Copiar valor original" else "Copy original value",
                onLongClick = { onCopy(readable.label, raw.value) }, onClick = { onCopy(readable.label, readable.value) })
                .padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(readable.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(readable.value.ifBlank { stringResource(R.string.device_details_unavailable) }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (technical) Text(raw.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!last) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}
