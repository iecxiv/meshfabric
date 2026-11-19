package iecxiv.meshfabric.ui.devices

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import iecxiv.meshfabric.R

@Composable
internal fun DeviceListBrowseControls(
    sort: DeviceListSort, reverse: Boolean, grouped: Boolean,
    selecting: Boolean, selectedCount: Int, hasVisible: Boolean,
    onSort: (DeviceListSort) -> Unit, onReverse: () -> Unit, onGroup: () -> Unit,
    onSelection: () -> Unit, onSelectAll: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.device_list_sort_by)) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DeviceListSort.entries.forEach { option ->
                    val supported = option in deviceListAvailableSorts
                    DropdownMenuItem(enabled = supported, text = {
                        val label = stringResource(when (option) {
                            DeviceListSort.GROUP -> R.string.device_list_sort_group
                            DeviceListSort.POWER -> R.string.device_list_sort_power
                            DeviceListSort.DEVICE -> R.string.device_list_sort_device
                            DeviceListSort.TAGS -> R.string.device_list_sort_tags
                            DeviceListSort.GROUP_TAGS -> R.string.device_list_sort_group_tags
                            DeviceListSort.LAST_SEEN -> R.string.device_list_sort_seen
                            DeviceListSort.LAST_BOOT -> R.string.device_list_sort_boot
                        })
                        Text(if (supported) label else stringResource(R.string.device_list_sort_pending, label))
                    }, trailingIcon = { RadioButton(selected = sort == option, onClick = null, enabled = supported) },
                        onClick = { onSort(option); open = false })
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.device_list_reverse)) },
                    trailingIcon = { Checkbox(checked = reverse, onCheckedChange = null) }, onClick = onReverse)
                DropdownMenuItem(text = { Text(stringResource(R.string.device_list_grouped)) },
                    trailingIcon = { Checkbox(checked = grouped, onCheckedChange = null) }, onClick = onGroup)
            }
        }
        TextButton(onClick = onSelection) { Text(stringResource(if (selecting) R.string.device_list_cancel_selection else R.string.device_list_select)) }
        if (selecting) {
            Text(stringResource(R.string.device_list_selected_count, selectedCount))
            TextButton(onClick = onSelectAll, enabled = hasVisible) { Text(stringResource(R.string.device_list_toggle_visible)) }
        }
    }
}
