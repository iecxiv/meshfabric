package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class UserActionSubviewTest {
    @Test fun everyUserOperationIsIntegratedFromDirectoryTabs() {
        for(tab in 1..3) for(operation in listOf("create","edit","delete","lock","unlock")) {
            assertTrue(profileFormUsesSubview(tab,ProfileForm(true,operation)))
        }
    }
    @Test fun accountAndAbsentFormsDoNotOpenDirectorySubviews() {
        assertFalse(profileFormUsesSubview(0,ProfileForm(true,"edit")))
        assertFalse(profileFormUsesSubview(2,null))
        assertFalse(profileFormUsesSubview(-1,ProfileForm(true,"edit")))
        assertFalse(profileFormUsesSubview(4,ProfileForm(true,"edit")))
    }
    @Test fun deviceGroupFormOperationsAreIntegrated() {
        assertTrue(profileFormUsesSubview(1,ProfileForm(false,"create")))
        assertTrue(profileFormUsesSubview(1,ProfileForm(false,"edit")))
        assertTrue(profileFormUsesSubview(1,ProfileForm(false,"delete")))
    }
}
