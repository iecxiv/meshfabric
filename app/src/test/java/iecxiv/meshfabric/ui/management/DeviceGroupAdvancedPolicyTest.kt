package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class DeviceGroupAdvancedPolicyTest {
    @Test fun flagsDependOnTypeAndRecordingCapability() {
        assertEquals(27L,deviceGroupAdvancedFlagMask(2,false));assertEquals(31L,deviceGroupAdvancedFlagMask(2,true))
        assertEquals(3L,deviceGroupAdvancedFlagMask(4,true));assertEquals(0L,deviceGroupAdvancedFlagMask(3,false));assertEquals(4L,deviceGroupAdvancedFlagMask(3,true))
    }
    @Test fun mergesPreserveUnknownAndUnavailableBits() {
        assertEquals(0x80000004L,deviceGroupAdvancedMergeMask(0x80000006L,0L,27L))
        assertEquals(0x800001ffL,deviceGroupAdvancedMergeMask(0x80000000L,511L,511L))
        assertNull(deviceGroupAdvancedMergeMask(0L,32L,31L));assertNull(deviceGroupAdvancedMergeMask(-1L,0L,31L))
        assertNull(deviceGroupAdvancedMergeMask(0L,-1L,31L));assertNull(deviceGroupAdvancedMergeMask(0x100000000L,0L,31L))
    }
}
