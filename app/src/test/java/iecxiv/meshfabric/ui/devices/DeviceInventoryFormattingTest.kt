package iecxiv.meshfabric.ui.devices

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class DeviceInventoryFormattingTest {
    private val locale = Locale.forLanguageTag("es-ES")
    private val utc = ZoneId.of("UTC")

    @Test fun secondsAndMillisecondsProduceTheSameReadableTime() {
        val seconds = readableInventoryTime("1735689600", "lastconnect", "connection", locale, utc)
        val millis = readableInventoryTime("1735689600000", "lastconnect", "connection", locale, utc)
        assertEquals(seconds, millis)
        assertTrue(seconds!!.contains("2025"))
        assertTrue(seconds.contains("00:00:00"))
        assertTrue(seconds.contains("UTC"))
    }

    @Test fun isoAndWmiOffsetsRepresentTheSameInstant() {
        val iso = readableInventoryTime("2025-01-01T01:00:00+01:00", "lastbootuptime", "os", locale, utc)
        val wmi = readableInventoryTime("20250101010000.000000+060", "lastbootuptime", "os", locale, utc)
        assertEquals(iso, wmi)
    }

    @Test fun timestampsUseRequestedTimeZone() {
        val value = readableInventoryTime("2025-01-01T00:00:00Z", "timestamp", "metadata", locale, ZoneId.of("Europe/Madrid"))
        assertTrue(value!!.contains("01:00:00"))
    }

    @Test fun ordinaryNumbersAndUnknownTimesAreNeverGuessed() {
        assertNull(readableInventoryTime("1735689600000", "serialnumber", "bios", locale, utc))
        assertNull(readableInventoryTime("4096", "capacity", "memory", locale, utc))
        assertNull(readableInventoryTime("not-a-date", "lastconnect", "connection", locale, utc))
        assertNull(readableInventoryTime("123456", "lastconnect", "connection", locale, utc))
        assertEquals("No disponible", readableInventoryTime("0", "lastconnect", "connection", locale, utc))
    }

    @Test fun naiveTimesDoNotInventAZone() {
        assertTrue(readableInventoryTime("2025-01-01T12:30:00", "updatetime", "metadata", locale, utc)!!.contains("zona no indicada"))
    }

    @Test fun labelsKeepArrayContextAndOriginalValueUnchanged() {
        val raw = InventoryValue("windows / memory [2] / Capacity", "4294967296")
        val readable = readableInventory(raw, "memory", locale, utc)
        assertEquals("Capacidad", readable.label)
        assertEquals("Módulo de memoria 2", readable.context)
        assertEquals("4294967296", raw.value)
        assertFalse(readable.context.contains("windows"))
    }

    @Test fun networkContextAndFalseRemainUnderstandable() {
        val row = readableInventory(InventoryValue("netif2 / wlan0 [1] / internal", "false"), "network", locale, utc)
        assertEquals("Interfaz interna", row.label)
        assertTrue(row.context.contains("Wlan0 1"))
        assertEquals("No", row.value)
    }

    @Test fun explicitDurationsAreNotDates() {
        assertEquals("1 d · 1 h · 1 min · 1 s", readableInventoryTime("90061", "uptimeseconds", "os", locale, utc))
    }
}
