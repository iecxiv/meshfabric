package iecxiv.meshfabric.ui.theme
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class AutomaticAppearancePolicyTest {
    @Test fun dayBoundaries() {
        assertEquals(false,scheduledAppearanceDark(420,420,1200));assertEquals(true,scheduledAppearanceDark(1200,420,1200));assertEquals(true,scheduledAppearanceDark(419,420,1200))
    }
    @Test fun overnightInterval() {
        assertEquals(false,scheduledAppearanceDark(60,1200,420));assertEquals(true,scheduledAppearanceDark(600,1200,420))
    }
    @Test fun invalidSchedulesFallback() {
        assertNull(scheduledAppearanceDark(0,420,420));assertNull(scheduledAppearanceDark(0,-1,1200));assertNull(scheduledAppearanceDark(1440,420,1200))
    }
    @Test fun madridSummerReference() {
        val city=solarCities.first { it.id=="madrid" }
        val w=solarWindow(LocalDate.of(2026,6,21),city)!!
        val zone=ZoneId.of(city.zone)
        assertEquals(6,w.sunrise.atZone(zone).hour);assertTrue(w.sunrise.atZone(zone).minute in 35..55)
        assertEquals(21,w.sunset.atZone(zone).hour);assertTrue(w.sunset.atZone(zone).minute in 35..59)
        assertEquals(false,solarAppearanceDark(Instant.parse("2026-06-21T12:00:00Z"),city))
        assertEquals(true,solarAppearanceDark(Instant.parse("2026-06-21T00:00:00Z"),city))
    }
    @Test fun polarFallback() {
        val city=solarCities.first { it.id=="tromso" }
        assertNull(solarWindow(LocalDate.of(2026,6,21),city));assertNull(solarWindow(LocalDate.of(2026,12,21),city))
    }
    @Test fun internationalDatesAndBoundaries() {
        for(city in solarCities) {
            val date=LocalDate.of(2026,3,20);val w=solarWindow(date,city)!!;val zone=ZoneId.of(city.zone)
            assertEquals(date,w.sunrise.atZone(zone).toLocalDate());assertEquals(date,w.sunset.atZone(zone).toLocalDate())
            assertTrue(Duration.between(w.sunrise,w.sunset).toHours() in 10L..14L)
            assertEquals(false,solarAppearanceDark(w.sunrise,city));assertEquals(true,solarAppearanceDark(w.sunset,city))
        }
    }
    @Test fun invalidCityFallback() {
        val city=solarCities.first()
        assertNull(solarWindow(LocalDate.of(2026,1,1),city.copy(latitude=Double.NaN)))
        assertNull(solarAppearanceDark(Instant.EPOCH,city.copy(zone="invalid")))
    }
    @Test fun catalogueIntegrity() {
        assertEquals(24,solarCities.size);assertEquals(solarCities.size,solarCities.map {it.id}.toSet().size)
        for(city in solarCities) assertNotNull(ZoneId.of(city.zone))
    }
}
