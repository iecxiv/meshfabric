package iecxiv.meshfabric.ui.management
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
internal data class ServerEventDay(val date:LocalDate?,val entries:List<IndexedValue<ServerEventRecord>>)
internal fun serverEventDays(rows:List<ServerEventRecord>,zone:ZoneId=ZoneId.systemDefault()):List<ServerEventDay> =
    rows.withIndex().sortedWith(compareByDescending<IndexedValue<ServerEventRecord>> {it.value.time ?: Long.MIN_VALUE})
        .groupBy {it.value.time?.let {time->Instant.ofEpochMilli(time).atZone(zone).toLocalDate()}}
        .entries.sortedWith(compareByDescending {it.key}).map {ServerEventDay(it.key,it.value)}
