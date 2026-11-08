package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class BottomNavigationOrderTest {
    @Test fun visualOrderUsesStablePageIds() {
        assertEquals(listOf(3,2,0,1,4),bottomNavigationPageOrder)
    }
    @Test fun equipmentKeepsItsExistingIdInTheMiddle() {
        assertEquals(0,bottomNavigationPageOrder[2])
    }
    @Test fun everyDestinationAppearsExactlyOnce() {
        assertEquals(listOf(0,1,2,3,4),bottomNavigationPageOrder.sorted())
        assertEquals(5,bottomNavigationPageOrder.distinct().size)
    }
}
