package iecxiv.meshfabric.ui.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode
import kotlinx.coroutines.delay

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
    var feedback by remember(node.id) { mutableStateOf("") }
    var feedbackVersion by remember(node.id) { mutableIntStateOf(0) }
    val copied = stringResource(R.string.device_details_copied)
    val failed = stringResource(R.string.device_details_copy_failed)
    val online = stringResource(R.string.device_details_online)
    val offline = stringResource(R.string.device_details_offline)
    val unknown = stringResource(R.string.device_details_unavailable)
    LaunchedEffect(feedbackVersion) {
        if (feedbackVersion > 0) {
            delay(2500)
            feedback = ""
        }
    }
    val copyValue: (String, String) -> Unit = { label, value ->
        val result = runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: error("Clipboard unavailable")
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        }
        feedback = if (result.isSuccess) "$copied: $label" else failed
        feedbackVersion++
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(Icons.Default.Info, contentDescription = null) },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.device_details_title), style = MaterialTheme.typography.titleLarge)
                Text(node.name.ifBlank { unknown }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column {
                Text(stringResource(R.string.device_details_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailsSection(stringResource(R.string.device_details_identity)) {
                        DetailValue(stringResource(R.string.device_details_name), node.name, copyValue)
                        DetailValue(stringResource(R.string.device_details_status), if (node.isOnline) online else offline, copyValue)
                        DetailValue(stringResource(R.string.device_details_os), node.osdesc, copyValue)
                        DetailValue(stringResource(R.string.device_details_node_id), node.id, copyValue)
                        DetailValue(stringResource(R.string.device_details_group_id), node.meshid, copyValue)
                    }
                    DetailsSection(stringResource(R.string.device_details_network)) {
                        DetailValue(stringResource(R.string.device_details_ip), node.ip, copyValue)
                        DetailValue(stringResource(R.string.device_details_gateway_ip), node.gip, copyValue)
                        DetailValue(stringResource(R.string.device_details_local_ip), node.localip, copyValue)
                        node.networkIpv4.orEmpty().distinct().forEach { network ->
                            DetailValue(stringResource(R.string.device_details_interface,
                                network.interfaceName.ifBlank { unknown }), network.address, copyValue)
                        }
                        if (node.networkIpv4.orEmpty().isEmpty()) {
                            Text(stringResource(R.string.device_details_no_interfaces),
                                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DetailsSection(stringResource(R.string.device_details_agent)) {
                        DetailValue(stringResource(R.string.device_details_agent_id), node.agent?.id?.toString(), copyValue)
                        DetailValue(stringResource(R.string.device_details_agent_version), node.agent?.ver?.toString(), copyValue)
                        DetailValue(stringResource(R.string.device_details_agent_caps), node.agent?.caps?.toString(), copyValue)
                        DetailValue(stringResource(R.string.device_details_node_caps), node.caps.toString(), copyValue)
                        DetailValue(stringResource(R.string.device_details_connection_flags), node.conn.toString(), copyValue)
                        DetailValue(stringResource(R.string.device_details_policy_flags), node.p.toString(), copyValue)
                    }
                }
                if (feedback.isNotEmpty()) {
                    Text(feedback, Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.device_details_close)) } }
    )
}

@Composable
private fun DetailsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String?, onCopy: (String, String) -> Unit) {
    val available = !value.isNullOrBlank()
    val copyLabel = stringResource(R.string.device_details_copy_value, label)
    Row(Modifier.fillMaxWidth()
        .clickable(enabled = available, onClickLabel = copyLabel) { value?.let { onCopy(label, it) } }
        .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (available) value.orEmpty() else stringResource(R.string.device_details_unavailable),
                style = MaterialTheme.typography.bodyMedium)
        }
        if (available) Icon(Icons.Default.ContentCopy, contentDescription = null,
            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
