package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class UserMeshEditPolicyTest {
    @Test fun unknownAndAdministrativeBitsArePreserved() {
        assertEquals(0x80000010L,profileUserMeshEditedRights(0x80000008L,16L))
        assertEquals(0x80000000L,profileUserMeshEditedRights(0x80000008L,0L))
        assertEquals(18L,profileUserMeshEditedRights(10L,16L))
        assertEquals(21L,profileUserMeshEditedRights(13L,16L))
    }
    @Test fun fullAdministratorZeroAndOutOfRangeAreBlocked() {
        assertNull(profileUserMeshEditedRights(0xffffffffL,8L))
        assertNull(profileUserMeshEditedRights(8L,0L))
        assertNull(profileUserMeshEditedRights(-1L,8L))
        assertNull(profileUserMeshEditedRights(0x100000000L,8L))
        for(value in listOf(-1L,1L,2L,4L,0x80000008L,0xffffffffL)) assertNull(profileUserMeshEditedRights(8L,value))
    }
}
