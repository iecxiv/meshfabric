package iecxiv.meshfabric.ui.devices
import java.time.*
import org.junit.Assert.*
import org.junit.Test
class DeviceSharingFormTest {
    @Test fun localTimeConvertsToUtcSeconds() {assertEquals(Instant.parse("2026-11-21T12:00:00Z").epochSecond,shareDateSeconds("2026-11-21 13:00",ZoneOffset.ofHours(1)))}
    @Test fun invalidAndAmbiguousTimesAreRejected() {for(s in listOf("2026-02-30 10:00","2026-11-21 25:00","2026-11-21","2026-03-29 02:30","2026-10-25 02:30")) assertTrue(s,runCatching {shareDateSeconds(s,ZoneId.of("Europe/Madrid"))}.isFailure)}
    @Test fun formBuildsUnlimitedAndRecurringDrafts() {
        val now=Instant.parse("2026-11-21T12:00:00Z").epochSecond
        val f=ShareForm(guest="Guest",expiry=0,start="2026-11-21 13:00",end="2026-11-21 14:00")
        assertEquals(0L,shareFormDraft(f,"mesh//a",now,ZoneOffset.UTC).end)
        val d=shareFormDraft(f.copy(validity=ShareValidity.WEEKLY,duration=720),"mesh//a",now,ZoneOffset.UTC)
        assertEquals(ShareValidity.WEEKLY,d.validity);assertEquals(720,d.durationMinutes)
    }
}
