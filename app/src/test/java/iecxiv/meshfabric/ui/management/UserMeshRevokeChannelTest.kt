package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class UserMeshRevokeChannelTest {
    @Test fun directRevocationRequiresBothActionAndPrivateContext() {
        assertTrue(profileUserMeshRevokeChannel("removemeshuser",true))
        assertFalse(profileUserMeshRevokeChannel("removemeshuser",false))
        for(action in listOf("addmeshuser","edituser","removeuserfromusergroup","","adddeviceuser")) {
            assertFalse(profileUserMeshRevokeChannel(action,true))
            assertFalse(profileUserMeshRevokeChannel(action,false))
        }
    }
}
