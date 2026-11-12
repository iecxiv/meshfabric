package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class NoticePersistencePolicyTest {
    private fun entry(id:Long,read:Boolean=false)=NoticeInboxEntry(AccountNotice(id,"general","text","",""),read=read)
    @Test fun moreThan200UnreadAreRetained() {assertEquals(500,noticeInboxRetain((1L..500L).map {entry(it)}).size)}
    @Test fun onlyOldReadEntriesAreTrimmed() {val rows=noticeInboxRetain((1L..250L).map {entry(it,true)}+entry(999));assertEquals(201,rows.size);assertEquals(999L,rows.last().notice.serial)}
    @Test fun scopeIsStableWithoutACookie() {assertEquals(noticeInboxStorageKey("https://server/","user//a"),noticeInboxStorageKey("https://server","user//a"))}
    @Test fun accountsAndServersAreSeparated() {val a=noticeInboxStorageKey("https://server","user//a");assertNotEquals(a,noticeInboxStorageKey("https://other","user//a"));assertNotEquals(a,noticeInboxStorageKey("https://server","user//b"))}
    @Test fun readStateChangesOnlyExplicitly() {val rows=listOf(entry(1));assertFalse(rows.single().read);assertTrue(noticeInboxRead(rows,1).single().read)}
}
