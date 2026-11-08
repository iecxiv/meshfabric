package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class GroupMeshGrantRightsPickerTest {
    @Test fun selectingOneBitPreservesOtherSelections() {
        assertEquals(8L,profileGroupMeshToggleGrantBit(0,8,true))
        assertEquals(264L,profileGroupMeshToggleGrantBit(8,256,true))
        assertEquals(256L,profileGroupMeshToggleGrantBit(264,8,false))
        assertEquals(0L,profileGroupMeshToggleGrantBit(8,8,false))
    }
    @Test fun everyDisplayedBitMatchesTheExistingAllowlist() {
        var value=0L
        for(index in 3..23) {val bit=1L shl index;assertTrue(profileGroupMeshInitialGrantRights(bit));value=requireNotNull(profileGroupMeshToggleGrantBit(value,bit,true))}
        assertEquals(0x00fffff8L,value)
        for(index in 3..23) value=requireNotNull(profileGroupMeshToggleGrantBit(value,1L shl index,false))
        assertEquals(0L,value)
        assertFalse(profileGroupMeshInitialGrantRights(value))
    }
    @Test fun protectedUnknownAndMultipleBitsAreRejected() {
        for(bit in listOf(0L,1L,2L,4L,264L,0x01000000L,0x80000000L,0xffffffffL,-1L)) assertNull(profileGroupMeshToggleGrantBit(8,bit,true))
        assertNull(profileGroupMeshToggleGrantBit(0x80000008L,8,false))
        assertNull(profileGroupMeshToggleGrantBit(-1,8,false))
    }
    @Test fun readableSummaryUsesExistingLabels() {
        assertEquals("Control remoto",deviceGroupRightsLabel(8,true))
        assertEquals("Remote control",deviceGroupRightsLabel(8,false))
        assertEquals("Control remoto, Solo visualizar",deviceGroupRightsLabel(264,true))
    }
}
