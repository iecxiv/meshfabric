package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import iecxiv.meshfabric.R
import iecxiv.meshfabric.data.model.MeshNode

@Composable
internal fun DeviceOtherOptionsToggle(expanded: Boolean, onToggle: () -> Unit) {
    IconButton(onClick=onToggle,modifier=Modifier.testTag("device_other_options_toggle")) {
        Icon(if(expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            stringResource(if(expanded) R.string.device_other_options_collapse else R.string.device_other_options_expand))
    }
}

@Composable
internal fun DeviceOtherOptionsRow(node: MeshNode) {
    var details by rememberSaveable(node.id) { mutableStateOf(false) }
    Column {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().testTag("device_other_options_row"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick={},enabled=false,modifier=Modifier.weight(1f).testTag("device_other_events"),contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)) {
                Icon(Icons.Default.Event,null,Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.device_other_options_events),fontSize=11.sp)
            }
            OutlinedButton(onClick={ details=true },modifier=Modifier.weight(1f).testTag("device_other_details"),contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)) {
                Icon(Icons.Default.Info,null,Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.device_other_options_details),fontSize=11.sp)
            }
            OutlinedButton(onClick={},enabled=false,modifier=Modifier.weight(1f).testTag("device_other_console"),contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)) {
                Icon(Icons.Default.Code,null,Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.device_other_options_console),fontSize=11.sp)
            }
        }
        Text(stringResource(R.string.device_other_options_pending),style=MaterialTheme.typography.labelSmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
    }
    if(details) DeviceDetailsDialog(node) { details=false }
}
