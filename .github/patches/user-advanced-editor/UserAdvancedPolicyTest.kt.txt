package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class UserAdvancedPolicyTest {
    @Test fun unsignedMasksAreExact() { assertEquals(0xffffffffL,userAdvancedMask(-1)); assertEquals(0L,userAdvancedMask(null)); assertEquals(0x80000000L,userAdvancedMask(2147483648L)) }
    @Test fun invalidMasksAreRejected() { for(v in listOf<Any>("2",true,0.5,Double.NaN,4294967296L)) assertThrows(IllegalArgumentException::class.java) { userAdvancedMask(v) } }
    @Test fun selfRightsCannotChange() { assertFalse(userSiteChangeAllowed(0xffffffffL,2L,0xffffffffL,true)); assertTrue(userSiteChangeAllowed(2L,2L,2L,true)) }
    @Test fun limitedDelegationMatchesServerMask() { assertEquals(226L,userDelegableMask(2L,false)); assertEquals(994L,userDelegableMask(770L,false)); assertFalse(userSiteChangeAllowed(2L,0L,1L,false)); assertTrue(userSiteChangeAllowed(2L,0L,32L,false)) }
    @Test fun fullAdminCanChangeAnotherAccount() { assertTrue(userSiteChangeAllowed(0xffffffffL,0L,0xffffffffL,false)) }
    @Test fun noRemoteControlSuppressesChildRestrictions() { assertEquals(8L,userCanonicalRestrictions(8L or 65536L or 256L or 512L or 1024L or 4194304L or 8388608L)) }
    @Test fun noDesktopSuppressesViewOnly() { assertEquals(65536L,userCanonicalRestrictions(65536L or 256L)) }
    @Test fun unknownRestrictionsArePreserved() { assertEquals(0x80000008L,userCanonicalRestrictions(0x80000008L)) }
}
