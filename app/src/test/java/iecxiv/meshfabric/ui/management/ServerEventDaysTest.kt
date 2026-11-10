package iecxiv.meshfabric.ui.management
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
class ServerEventDaysTest {
    private fun row(time:Long?)=ServerEventRecord("manual","event","","","",time)
    private val utc=ZoneId.of("UTC")
    @Test fun knownDaysAreNewestFirst() {
        val groups=serverEventDays(listOf(row(0),row(86400000)),utc)
        assertEquals(listOf(LocalDate.of(1970,1,2),LocalDate.of(1970,1,1)),groups.map {it.date})
    }
    @Test fun sameDayHasOneHeaderAndNewestEventFirst() {
        val groups=serverEventDays(listOf(row(0),row(1000)),utc)
        assertEquals(1,groups.size);assertEquals(listOf(1000L,0L),groups.single().entries.map {it.value.time})
    }
    @Test fun missingDatesStayUnknownAndLast() {
        val groups=serverEventDays(listOf(row(null),row(0)),utc)
        assertEquals(2,groups.size);assertNull(groups.last().date);assertNull(groups.last().entries.single().value.time)
    }
    @Test fun timezoneDeterminesTheEventDayNotTheReceiveDay() {
        val time=Instant.parse("2026-11-10T00:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026,11,9),serverEventDays(listOf(row(time)),ZoneId.of("America/Los_Angeles")).single().date)
        assertEquals(LocalDate.of(2026,11,10),serverEventDays(listOf(row(time)),utc).single().date)
    }
    @Test fun duplicateRecordsRetainDistinctOriginalIndices() {
        val event=row(0);val groups=serverEventDays(listOf(event,event),utc)
        assertEquals(listOf(0,1),groups.single().entries.map {it.index});assertEquals(2,groups.single().entries.size)
    }
}
