package iecxiv.meshfabric.ui.devices

import java.util.Locale

internal enum class DeviceListSort(val newestFirst: Boolean = false) {
    GROUP, POWER(true), DEVICE, TAGS, GROUP_TAGS, LAST_SEEN(true), LAST_BOOT(true)
}

// Enriched by the inventory layer; missing telemetry must remain unknown.
internal data class DeviceListSortRow(
    val id: String,
    val name: String,
    val groupName: String,
    val tags: List<String> = emptyList(),
    val poweredOn: Boolean? = null,
    val lastSeen: Long? = null,
    val lastBoot: Long? = null
)

private fun deviceListText(value: String): String? =
    value.trim().lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }

private fun deviceListTags(values: List<String>): String? =
    values.mapNotNull(::deviceListText).distinct().sorted().joinToString("\u0000").takeIf { it.isNotEmpty() }

private fun <T : Comparable<T>> deviceListCompare(a: T?, b: T?, descending: Boolean): Int {
    if (a == null) return if (b == null) 0 else 1
    if (b == null) return -1
    return if (descending) b.compareTo(a) else a.compareTo(b)
}

internal fun deviceListComparator(sort: DeviceListSort, reverse: Boolean = false): Comparator<DeviceListSortRow> {
    val descending = sort.newestFirst != reverse
    return Comparator { a, b ->
        val primary = when (sort) {
            DeviceListSort.GROUP -> deviceListCompare(deviceListText(a.groupName), deviceListText(b.groupName), descending)
            DeviceListSort.POWER -> deviceListCompare(a.poweredOn, b.poweredOn, descending)
            DeviceListSort.DEVICE -> deviceListCompare(deviceListText(a.name), deviceListText(b.name), descending)
            DeviceListSort.TAGS -> deviceListCompare(deviceListTags(a.tags), deviceListTags(b.tags), descending)
            DeviceListSort.GROUP_TAGS -> {
                val group = deviceListCompare(deviceListText(a.groupName), deviceListText(b.groupName), descending)
                if (group != 0) group else deviceListCompare(deviceListTags(a.tags), deviceListTags(b.tags), descending)
            }
            DeviceListSort.LAST_SEEN -> deviceListCompare(a.lastSeen?.takeIf { it > 0L }, b.lastSeen?.takeIf { it > 0L }, descending)
            DeviceListSort.LAST_BOOT -> deviceListCompare(a.lastBoot?.takeIf { it > 0L }, b.lastBoot?.takeIf { it > 0L }, descending)
        }
        if (primary != 0) primary else {
            val name = deviceListCompare(deviceListText(a.name), deviceListText(b.name), false)
            if (name != 0) name else a.id.compareTo(b.id)
        }
    }
}

// Scope must identify both server and authenticated account, never just a hostname.
internal data class DeviceListSelection(val scope: String, val ids: Set<String> = emptySet()) {
    fun reconcile(currentScope: String, visibleIds: Set<String>): DeviceListSelection =
        DeviceListSelection(currentScope, if (scope == currentScope) ids.intersect(visibleIds).filter { it.isNotBlank() }.toSet() else emptySet())

    fun toggle(currentScope: String, id: String, visibleIds: Set<String>): DeviceListSelection {
        val current = reconcile(currentScope, visibleIds)
        if (id.isBlank() || id !in visibleIds) return current
        return current.copy(ids = if (id in current.ids) current.ids - id else current.ids + id)
    }

    fun selectVisible(currentScope: String, visibleIds: Set<String>): DeviceListSelection =
        DeviceListSelection(currentScope, visibleIds.filter { it.isNotBlank() }.toSet())

    fun toggleGroup(currentScope: String, groupIds: Set<String>, visibleIds: Set<String>): DeviceListSelection {
        val current = reconcile(currentScope, visibleIds)
        val targets = groupIds.intersect(visibleIds).filter { it.isNotBlank() }.toSet()
        return current.copy(ids = if (current.ids.containsAll(targets)) current.ids - targets else current.ids + targets)
    }

    fun clear(): DeviceListSelection = copy(ids = emptySet())
}
