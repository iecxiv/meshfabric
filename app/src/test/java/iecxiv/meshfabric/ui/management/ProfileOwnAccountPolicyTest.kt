package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class ProfileOwnAccountPolicyTest {
    @Test fun identityUsesExactNonemptyId() {
        assertTrue(profileOwnAccount("user/a/admin","user/a/admin"))
        assertFalse(profileOwnAccount("user/a/admin","user/b/admin"))
        assertFalse(profileOwnAccount("",""))
    }
    @Test fun onlyTwoOwnSecurityDestinations() {
        assertEquals(7,profileOwnAccountMode(ProfileOwnAccountRequest("user/a/me","server","password"),"user/a/me","server"))
        assertEquals(10,profileOwnAccountMode(ProfileOwnAccountRequest("user/a/me","server","mfa"),"user/a/me","server"))
        assertNull(profileOwnAccountMode(ProfileOwnAccountRequest("user/a/me","server","delete"),"user/a/me","server"))
    }
    @Test fun staleActorOrServerNeverOpens() {
        val request=ProfileOwnAccountRequest("user/a/me","server","password")
        assertNull(profileOwnAccountMode(request,"user/a/other","server"))
        assertNull(profileOwnAccountMode(request,"user/a/me","different"))
        assertNull(profileOwnAccountMode(request,"user/a/me",""))
    }
}
