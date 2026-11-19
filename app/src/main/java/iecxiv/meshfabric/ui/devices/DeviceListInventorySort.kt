package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode

internal val deviceListAvailableSorts = setOf(
    DeviceListSort.GROUP, DeviceListSort.POWER, DeviceListSort.DEVICE, DeviceListSort.TAGS, DeviceListSort.LAST_BOOT
)

internal fun deviceListPoweredOn(state: Int?): Boolean? = when (state) {
    1 -> true
    2, 3, 4, 5, 6, 8 -> false
    else -> null
}

internal fun deviceListInventorySortRow(group: MeshGroup, node: MeshNode) =
    DeviceListSortRow(node.id, node.name, group.name, tags = node.tags.orEmpty(),
        poweredOn = deviceListPoweredOn(node.pwr), lastBoot = node.lastBootTime)
