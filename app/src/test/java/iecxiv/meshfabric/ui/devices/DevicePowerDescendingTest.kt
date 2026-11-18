package iecxiv.meshfabric.ui.devices

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DevicePowerDescendingTest {
    @Test fun todayComesFirstAndSevenDaysDescend() {
        val zone=ZoneId.of("Europe/Madrid")
        val now=Instant.parse("2026-11-17T07:13:00Z").toEpochMilli()
        val days=powerDays(now,zone)
        assertEquals((0..6).map {LocalDate.of(2026,11,17).minusDays(it.toLong())},days)
        assertEquals(7,days.distinct().size)
    }
    @Test fun todayUsesLocalZoneNotUtcDate() {
        val now=Instant.parse("2026-11-16T23:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026,11,17),powerDays(now,ZoneId.of("Europe/Madrid")).first())
        assertEquals(LocalDate.of(2026,11,16),powerDays(now,ZoneId.of("UTC")).first())
    }
    @Test fun descendingDaysDoNotReverseSpans() {
        val zone=ZoneId.of("UTC")
        val now=Instant.parse("2026-11-17T12:00:00Z").toEpochMilli()
        val start=powerDays(now,zone).first().atStartOfDay(zone).toInstant().toEpochMilli()
        val end=start+86_400_000L
        val history=PowerHistory(listOf(PowerPoint(start,1),PowerPoint(start+3_600_000L,8)),now)
        val spans=powerSpans(history,start,end)
        assertEquals(listOf(1,8,null),spans.map {it.state})
        assertTrue(spans.last().future)
        assertTrue(spans.zipWithNext().all {(a,b)->a.end==b.start})
    }
}
