package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class PresentationPolishPolicyTest {
    private fun row(name:String="surface",group:String="agent")=ServerEventRecord("manual","event","","node//x",name,1L,groupName=group)
    @Test fun deviceAndGroupHaveOneInsertedSpace() {assertEquals("surface (agent)",row().deviceLabel())}
    @Test fun absentGroupDoesNotAddWhitespaceOrParentheses() {assertEquals("surface",row(group="").deviceLabel())}
    @Test fun missingDeviceDoesNotInventAName() {assertEquals("",row(name="").deviceLabel())}
    @Test fun historyDateMatchesSecondsAndMilliseconds() {assertEquals(userDate(1700000000L),userDate(1700000000000L));assertTrue(userDate(1700000000L).isNotBlank())}
    @Test fun invalidHistoryDateIsNotInvented() {assertEquals("",userDate(null));assertEquals("",userDate("invalid"));assertEquals("",userDate(-1L))}
    @Test fun statisticsRefreshIsOnlyAvailableForReadyIdleStatistics() {
        for(tab in listOf(0,2,3)) assertFalse(serverStatisticsRefreshEnabled(tab,true,false))
        assertTrue(serverStatisticsRefreshEnabled(1,true,false));assertFalse(serverStatisticsRefreshEnabled(1,false,false));assertFalse(serverStatisticsRefreshEnabled(1,true,true))
    }
}
