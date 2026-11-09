package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class UserMeshGrantPolicyTest {
    @Test fun initialGrantExcludesAdministrativeAndUnknownBits() {
        for(rights in listOf(8L,264L,0x00fffff8L)) assertTrue(profileUserMeshInitialGrantRights(rights))
        for(rights in listOf(0L,1L,2L,4L,9L,-1L,0xffffffffL,0x80000008L,0x01000008L)) assertFalse(profileUserMeshInitialGrantRights(rights))
    }
    @Test fun directGrantHasItsOwnChannel() {
        assertTrue(profileUserMeshGrantChannel("addmeshuser",true))
        assertFalse(profileUserMeshGrantChannel("addmeshuser",false))
        assertFalse(profileUserMeshGrantChannel("removemeshuser",true))
        assertFalse(profileUserMeshRevokeChannel("addmeshuser",true))
    }
}
