package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class DeviceGroupSearchStateTest {
    @Test fun directoriesKeepTheirOwnSearchRoute() {
        for(tab in 1..3) assertEquals(ProfileSearchTarget.DIRECTORY,profileSearchTarget(tab,false,0,false,false))
        assertEquals(ProfileSearchTarget.NONE,profileSearchTarget(0,false,1,false,false))
    }
    @Test fun onlyUncoveredDeviceTabCanSearchInsideGroup() {
        for(tab in 0..2) assertEquals(if(tab==1) ProfileSearchTarget.GROUP_DEVICES else ProfileSearchTarget.NONE,profileSearchTarget(1,true,tab,false,false))
        assertEquals(ProfileSearchTarget.NONE,profileSearchTarget(1,true,1,true,false))
        assertEquals(ProfileSearchTarget.NONE,profileSearchTarget(1,true,1,false,true))
        assertEquals(ProfileSearchTarget.NONE,profileSearchTarget(2,false,0,true,false))
    }
    @Test fun closingInternalSearchClearsOnlyThatGroup() {
        val a=DeviceGroupSearchState("alpha",true);val b=DeviceGroupSearchState("beta",true)
        a.toggle();assertFalse(a.open);assertEquals("",a.query)
        assertTrue(b.open);assertEquals("beta",b.query)
        a.toggle();assertTrue(a.open)
    }
}
