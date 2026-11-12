package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class NoticeInboxPolicyTest {
private fun entry(id:Long)=NoticeInboxEntry(AccountNotice(id,"general","message","",""))
@Test fun unreadNoticesAreNeverAutomaticallyEvicted() {
val rows=(1L..205L).fold(emptyList<NoticeInboxEntry>()) { list,id->noticeInboxAppend(list,entry(id)) }
assertEquals(205,rows.size); assertEquals(205L,rows.first().notice.serial); assertEquals(1L,rows.last().notice.serial)
}
@Test fun readAndUnreadAreLocalAndTargeted() {
val rows=listOf(entry(1),entry(2)); val read=noticeInboxRead(rows,1)
assertTrue(read[0].read); assertFalse(read[1].read); assertFalse(rows[0].read)
assertFalse(noticeInboxRead(read,1,false)[0].read); assertTrue(noticeInboxRead(rows).all { it.read })
}
@Test fun sourceChangesAndLogoutInvalidateTheScope() {
val a=Triple("server","cookie-a",false)
assertTrue(noticeScopeValid(a,a,"user//alice",false))
assertFalse(noticeScopeValid(a,a.copy(second="cookie-b"),"user//alice",false))
assertFalse(noticeScopeValid(a,a,"user//alice",true)); assertFalse(noticeScopeValid(a,a,"",false))
}
}
