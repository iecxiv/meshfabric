package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class AccountInlineOptionTest {
    @Test fun passwordAndMfaRemainDirectDestinations() {
        assertEquals(7,profileOwnAccountMode(ProfileOwnAccountRequest("user//a","server","password"),"user//a","server"))
        assertEquals(10,profileOwnAccountMode(ProfileOwnAccountRequest("user//a","server","mfa"),"user//a","server"))
        assertTrue(accountInlineOption(7));assertTrue(accountInlineOption(10))
    }
    @Test fun deletionRemainsModalAndInvalidModesAreNotInline() {
        assertFalse(accountInlineOption(8));assertFalse(accountInlineOption(-1));assertFalse(accountInlineOption(11))
        assertNull(profileOwnAccountMode(ProfileOwnAccountRequest("user//a","server","mfa"),"user//b","server"))
        assertNull(profileOwnAccountMode(ProfileOwnAccountRequest("user//a","server","password"),"user//a","other"))
    }
}
