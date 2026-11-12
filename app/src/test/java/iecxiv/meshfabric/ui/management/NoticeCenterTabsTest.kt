package iecxiv.meshfabric.ui.management

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeCenterTabsTest {
    @Test fun inboxIsTheInitialSection() {assertEquals(NoticeSection.INBOX,NoticeSection.entries.first())}
    @Test fun sectionsAreDistinctAndStable() {assertEquals(listOf("INBOX","BROADCAST","MESSAGES"),NoticeSection.entries.map {it.name})}
}
