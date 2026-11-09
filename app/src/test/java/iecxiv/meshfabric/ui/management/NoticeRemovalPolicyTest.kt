package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class NoticeRemovalPolicyTest {
    private fun row(id:Long)=NoticeInboxEntry(AccountNotice(id,"general","body","",""))
    @Test fun removesOnlyTheSelectedEntry() {
        val source=listOf(row(1),row(2))
        assertEquals(listOf(2L),noticeInboxRemove(source,1).map { it.notice.serial })
        assertEquals(2,source.size)
    }
    @Test fun unknownRemovalIsHarmless() {
        val source=listOf(row(1))
        assertEquals(source,noticeInboxRemove(source,99))
    }
    @Test fun readIsTargetedAndReversible() {
        val source=listOf(row(1),row(2))
        val changed=noticeInboxRead(source,1)
        assertTrue(changed[0].read)
        assertFalse(changed[1].read)
        assertEquals(source,noticeInboxRead(changed,1,false))
    }
}
