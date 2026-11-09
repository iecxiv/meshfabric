package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class ProfileListReadCorrelationTest {
    @Test fun dataRequiresTheExactCurrentTag() {
        for(action in listOf("users","meshes")) {
            assertTrue(profileListReadMatches(action,"current","current",null,true))
            assertFalse(profileListReadMatches(action,"current","old",null,true))
            assertFalse(profileListReadMatches(action,"current",null,null,true))
            assertFalse(profileListReadMatches(action,"current",null,"current",true))
            assertFalse(profileListReadMatches(action,"current",12,"current",true))
        }
    }
    @Test fun permissionDenialCanMatchResponseidButNotData() {
        assertTrue(profileListReadMatches("users","current",null,"current",false))
        assertFalse(profileListReadMatches("users","current",null,"old",false))
        assertFalse(profileListReadMatches("users","current","old","current",false))
        assertFalse(profileListReadMatches("users","current",12,"current",false))
        assertFalse(profileListReadMatches("users","current",null,12,false))
    }
    @Test fun newRoundInvalidatesOldPacketsAndConsumptionInvalidatesDuplicates() {
        val pending=mutableMapOf("users" to "before-ack","meshes" to "before-ack-mesh")
        pending["users"]="after-ack";pending["meshes"]="after-ack-mesh"
        assertFalse(profileListReadMatches("users",pending["users"],"before-ack",null,true))
        assertFalse(profileListReadMatches("meshes",pending["meshes"],"before-ack-mesh",null,true))
        assertFalse(profileListReadMatches("users",pending["users"],"after-ack-mesh",null,true))
        assertTrue(profileListReadMatches("users",pending["users"],"after-ack",null,true))
        pending.remove("users")
        assertFalse(profileListReadMatches("users",pending["users"],"after-ack",null,true))
        assertTrue(profileListReadMatches("meshes",pending["meshes"],"after-ack-mesh",null,true))
    }
    @Test fun clearedOrUnknownRequestsNeverMatch() {
        assertFalse(profileListReadMatches("users",null,"current",null,true))
        assertFalse(profileListReadMatches("users","","",null,true))
        assertFalse(profileListReadMatches("users"," "," ",null,true))
        assertFalse(profileListReadMatches("nodes","current","current",null,true))
        assertFalse(profileListReadMatches("usergroups","current","current",null,true))
    }
}
