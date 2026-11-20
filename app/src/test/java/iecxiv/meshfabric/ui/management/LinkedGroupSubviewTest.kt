package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class LinkedGroupSubviewTest {
    @Test fun groupAndAssignmentOpenInsideEveryDirectoryTab() {
        for(tab in 1..3) {
            assertTrue(profileLinkedGroupSubview(tab,"ugrp/domain/a",null))
            assertTrue(profileLinkedGroupSubview(tab,null,"ugrp/domain/a" to "mesh/domain/b"))
        }
    }
    @Test fun noDestinationAndAccountDoNotCoverDirectory() {
        assertFalse(profileLinkedGroupSubview(2,null,null))
        for(tab in listOf(-1,0,4)) assertFalse(profileLinkedGroupSubview(tab,"ugrp/domain/a",null))
    }
    @Test fun remainingDestinationKeepsDirectoryCovered() {
        assertTrue(profileLinkedGroupSubview(1,null,"ugrp/domain/a" to "mesh/domain/b"))
        assertFalse(profileLinkedGroupSubview(1,null,null))
    }
}
