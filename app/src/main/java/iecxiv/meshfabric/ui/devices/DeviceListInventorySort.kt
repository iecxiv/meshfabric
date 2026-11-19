package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode

internal val deviceListAvailableSorts = setOf(DeviceListSort.GROUP, DeviceListSort.DEVICE, DeviceListSort.TAGS)

internal fun deviceListInventorySortRow(group: MeshGroup, node: MeshNode) =
    DeviceListSortRow(node.id, node.name, group.name, tags = node.tags.orEmpty())
