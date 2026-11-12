package iecxiv.meshfabric.ui.devices
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class DevicePowerPolicyTest {
    private fun decode(s:String,now:Long=2000000)=decodePower(JsonParser.parseString(s).asJsonArray,now)
    @Test fun decodesSecondsAndDeltas() { assertEquals(listOf(PowerPoint(1000000,1),PowerPoint(1030000,8)),decode("[0,1000,1,30,8]").points) }
    @Test fun emptyHistoryIsNotOff() { assertNull(powerSpans(decode("[]"),0,100).single().state) }
    @Test fun doesNotInventEarlierHistory() { val h=decode("[1,1000,8]");assertNull(powerSpans(h,0,1500000).first().state) }
    @Test fun carriesKnownStateIntoWindow() { assertEquals(1,powerSpans(decode("[0,1000,1]"),1200000,1300000).single().state) }
    @Test fun recognizesMillisecondCurrentSample() { val now=1800000001000L;val h=decode("[1,1800000000000,1]",now);assertTrue(h.currentOnly);assertNull(powerSpans(h,now-86400000,now).first().state) }
    @Test(expected=IllegalArgumentException::class) fun rejectsMalformedTimeline() { decode("[0,1,1,2]") }
    @Test(expected=IllegalArgumentException::class) fun rejectsNegativeDelta() { decode("[0,1,1,-2,8]") }
    @Test(expected=IllegalArgumentException::class) fun rejectsFutureData() { decode("[0,3000,1]") }
    @Test fun futureIsNotHistory() { assertTrue(powerSpans(decode("[]"),2000000,2100000).single().future) }
    @Test fun sevenLocalDatesIncludeDst() {
        val zone=ZoneId.of("Europe/Madrid");val now=Instant.parse("2026-10-27T12:00:00Z").toEpochMilli();val dates=powerDays(now,zone)
        assertEquals(7,dates.size);val d=LocalDate.of(2026,10,25);assertTrue(d in dates)
        assertEquals(25,Duration.between(d.atStartOfDay(zone),d.plusDays(1).atStartOfDay(zone)).toHours())
    }
    @Test fun csvUrlKeepsDomainAndEncodesNode() { val u=powerCsvUrl("https://example.com/company","node/company/a+b","es",ZoneId.of("UTC"),0);assertEquals("/company/devicepowerevents.ashx",u.encodedPath);assertEquals("node/company/a+b",u.queryParameter("id")) }
    @Test fun rejectsLoginPageAsCsv() { assertFalse(powerCsvValid("<html>Login</html>".toByteArray(),"text/csv"));assertTrue(powerCsvValid("Time,State\n1,1".toByteArray(),"text/csv")) }
}
