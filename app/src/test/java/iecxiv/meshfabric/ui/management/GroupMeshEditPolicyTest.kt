package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class GroupMeshEditPolicyTest {
    @Test fun existingUnknownAndAdministrativeBitsArePreserved() {
        assertEquals(0x80000010L,profileGroupMeshEditedRights(0x80000008L,16L))
        assertEquals(18L,profileGroupMeshEditedRights(10L,16L))
        assertEquals(0x80000000L,profileGroupMeshEditedRights(0x80000008L,0L))
    }
    @Test fun zeroFullAdministratorAndNewUnknownBitsAreBlocked() {
        assertNull(profileGroupMeshEditedRights(8L,0L))
        assertNull(profileGroupMeshEditedRights(0xffffffffL,16L))
        for(value in listOf(-1L,1L,2L,4L,0x80000010L,0xffffffffL)) assertNull(profileGroupMeshEditedRights(8L,value))
        assertNull(profileGroupMeshEditedRights(-1L,16L))
        assertNull(profileGroupMeshEditedRights(0x100000000L,16L))
    }
}
