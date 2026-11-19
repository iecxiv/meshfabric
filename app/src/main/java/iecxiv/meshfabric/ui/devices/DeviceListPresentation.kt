package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonElement
import iecxiv.meshfabric.data.model.*
import java.util.Locale

internal enum class DeviceListFilter { ALL, ONLINE, OFFLINE, SESSIONS, STARRED, AMT, HELP, TAGGED, UNTAGGED }
internal fun deviceListDisplayName(node: MeshNode, osName: Boolean): String =
    if (osName) node.rname?.takeIf { it.isNotBlank() && it.none { c -> c.isISOControl() } } ?: node.name else node.name
internal fun deviceListPowerSection(value: Int?): Int = when (value) { 1 -> 1; 6, 8 -> 2; else -> 0 }
internal fun deviceListUsesSections(sort: DeviceListSort) = sort in setOf(DeviceListSort.GROUP, DeviceListSort.POWER, DeviceListSort.TAGS, DeviceListSort.GROUP_TAGS)
internal fun deviceListEffectiveShowDisconnected(saved: Boolean, filter: DeviceListFilter) = filter == DeviceListFilter.OFFLINE || saved
private fun positive(value: JsonElement?, depth: Int = 0): Boolean {
    if (value == null || value.isJsonNull || depth > 12) return false
    return when {
        value.isJsonObject -> value.asJsonObject.entrySet().any { positive(it.value, depth + 1) }
        value.isJsonArray -> value.asJsonArray.any { positive(it, depth + 1) }
        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> runCatching { value.asBigDecimal.signum() > 0 }.getOrDefault(false)
        else -> false
    }
}
internal fun deviceListMatchesFilter(node: MeshNode, filter: DeviceListFilter, stars: Set<String>, localSessions: Set<String>): Boolean {
    val sessions = node.sessions?.takeIf { it.isJsonObject }?.asJsonObject
    val tags = node.tags.orEmpty().filter { it.isNotBlank() }
    return when (filter) {
        DeviceListFilter.ALL -> true
        DeviceListFilter.ONLINE -> node.isOnline
        DeviceListFilter.OFFLINE -> !node.isOnline
        DeviceListFilter.STARRED -> node.id in stars
        DeviceListFilter.AMT -> node.intelamt?.isJsonObject == true
        DeviceListFilter.HELP -> positive(sessions?.get("help")) || sessions?.get("help")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() } == true
        DeviceListFilter.TAGGED -> tags.isNotEmpty()
        DeviceListFilter.UNTAGGED -> tags.isEmpty()
        DeviceListFilter.SESSIONS -> listOf("kvm", "terminal", "files", "registry", "tcp", "udp").any { positive(sessions?.get(it)) } ||
            node.id in localSessions || node.id.removePrefix("node//").removePrefix("node/").trim() in localSessions
    }
}
internal fun deviceListFiltered(groups: List<Pair<MeshGroup, List<MeshNode>>>, filter: DeviceListFilter,
    showDisconnected: Boolean, query: String, osName: Boolean, stars: Set<String>, localSessions: Set<String>): List<Pair<MeshGroup, List<MeshNode>>> {
    val q = query.trim().lowercase(Locale.ROOT)
    val show = deviceListEffectiveShowDisconnected(showDisconnected, filter)
    return groups.mapNotNull { (group, nodes) ->
        val kept = nodes.filter { n -> (show || n.isOnline) && deviceListMatchesFilter(n, filter, stars, localSessions) &&
            (q.isEmpty() || (listOf(deviceListDisplayName(n, osName), n.name, n.rname.orEmpty(), n.osdesc.orEmpty(), group.name, n.ip.orEmpty()) +
                n.tags.orEmpty() + n.networkIpv4.map { it.address }).any { it.lowercase(Locale.ROOT).contains(q) }) }
        if (kept.isNotEmpty() || nodes.isEmpty() && q.isEmpty() && filter == DeviceListFilter.ALL && show) group to kept else null
    }
}
internal fun deviceListSections(groups: List<Pair<MeshGroup, List<MeshNode>>>, sort: DeviceListSort,
    reverse: Boolean, osName: Boolean, es: Boolean): List<Pair<MeshGroup, List<MeshNode>>> {
    val names = Comparator<MeshNode> { a, b ->
        val c = deviceListDisplayName(a, osName).trim().compareTo(deviceListDisplayName(b, osName).trim(), true)
        if (c != 0) c else a.id.compareTo(b.id)
    }
    if (sort == DeviceListSort.GROUP) {
        val ordered = groups.sortedWith(compareBy<Pair<MeshGroup,List<MeshNode>>,String>(String.CASE_INSENSITIVE_ORDER) { it.first.name }.thenBy { it.first.id })
        return (if (reverse) ordered.reversed() else ordered).map { (g, ns) -> g to ns.distinctBy { it.id }.sortedWith(Comparator { a, b ->
            val status = b.isOnline.compareTo(a.isOnline); if (status != 0) status else names.compare(a,b)
        }) }
    }
    val entries = groups.flatMap { (g, ns) -> ns.map { g to it } }.distinctBy { it.second.id }
    if (entries.isEmpty()) return emptyList()
    if (!deviceListUsesSections(sort)) {
        val rows = entries.associate { (g,n) -> n.id to deviceListInventorySortRow(g,n).copy(name=deviceListDisplayName(n,osName)) }
        val comparator = deviceListComparator(sort, reverse)
        return listOf(MeshGroup(id="browse/flat/${sort.name}") to entries.map { it.second }.sortedWith(Comparator { a,b -> comparator.compare(rows.getValue(a.id),rows.getValue(b.id)) }))
    }
    if (sort == DeviceListSort.POWER) {
        val order = (if (reverse) listOf(2,1) else listOf(1,2)) + 0
        return order.mapNotNull { state ->
            val ns = entries.map { it.second }.filter { deviceListPowerSection(it.pwr) == state }.sortedWith(names)
            val title = when (state) { 1 -> if(es) "Encendido" else "Powered on"; 2 -> if(es) "Apagado" else "Powered off"; else -> if(es) "Desconocido" else "Unknown" }
            if(ns.isEmpty()) null else MeshGroup(id="browse/power/$state",name=title) to ns
        }
    }
    val buckets = linkedMapOf<String,Pair<String,MutableList<MeshNode>>>()
    fun add(key:String,title:String,node:MeshNode) { buckets.getOrPut(key) { title to mutableListOf() }.second.add(node) }
    entries.forEach { (g,n) ->
        val tags = n.tags.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if(sort == DeviceListSort.TAGS) {
            if(tags.isEmpty()) add("browse/tag/empty",if(es) "Sin etiqueta" else "Untagged",n)
            else tags.forEach { tag -> add("browse/tag/${tag.length}:$tag",tag,n) }
        } else {
            if(tags.isEmpty()) add("browse/group/${g.id.length}:${g.id}/empty",g.name,n)
            else tags.forEach { tag -> add("browse/group/${g.id.length}:${g.id}/${tag.length}:$tag",g.name+" - "+tag,n) }
        }
    }
    val ordered = buckets.entries.sortedWith(compareBy<Map.Entry<String,Pair<String,MutableList<MeshNode>>>,String>(String.CASE_INSENSITIVE_ORDER) { it.value.first }.thenBy { it.key })
    return (if(reverse) ordered.reversed() else ordered).map { (key,value) -> MeshGroup(id=key,name=value.first) to value.second.distinctBy { it.id }.sortedWith(names) }
}
