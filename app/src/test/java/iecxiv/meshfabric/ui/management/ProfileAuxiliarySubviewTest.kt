package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class ProfileAuxiliarySubviewTest {
    @Test fun bothAuxiliaryRoutesCoverEveryDirectoryTab() {
        for(tab in 1..3) {
            assertTrue(profileAuxiliarySubview(tab,true,false))
            assertTrue(profileAuxiliarySubview(tab,false,true))
            assertTrue(profileAuxiliarySubview(tab,true,true))
            assertFalse(profileAuxiliarySubview(tab,false,false))
        }
    }
    @Test fun accountAndInvalidTabsDoNotOpenDirectorySubviews() {
        for(tab in listOf(-1,0,4)) assertFalse(profileAuxiliarySubview(tab,true,true))
    }
}
