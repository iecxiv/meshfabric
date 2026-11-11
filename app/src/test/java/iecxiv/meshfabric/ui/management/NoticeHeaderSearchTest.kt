package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class NoticeHeaderSearchTest {
    @Test fun startsHiddenAndEmpty() {assertEquals(NoticeSearchState(false,""),NoticeSearchState())}
    @Test fun opensOnToggle() {assertTrue(NoticeSearchState().toggle().open)}
    @Test fun closingClearsFilter() {assertEquals(NoticeSearchState(),NoticeSearchState(true,"Ana").toggle())}
    @Test fun switchingContextClearsSearch() {assertEquals(NoticeSearchState(),NoticeSearchState(true,"Oficina").cleared())}
    @Test fun closedSearchCannotFilter() {assertEquals(NoticeSearchState(),NoticeSearchState().edit("Ana"))}
    @Test fun boundsInput() {assertEquals(256,NoticeSearchState(true).edit("x".repeat(300)).query.length)}
    @Test fun reopeningStartsEmpty() {assertEquals(NoticeSearchState(true,""),NoticeSearchState(true,"Ana").toggle().toggle())}
}
