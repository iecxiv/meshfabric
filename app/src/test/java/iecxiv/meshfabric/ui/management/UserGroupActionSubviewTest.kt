package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class UserGroupActionSubviewTest {
    @Test fun directoryRemainsAvailableWithoutActions() {
        assertFalse(userGroupSubviewActive(*BooleanArray(8)))
    }
    @Test fun everyActionCoversTheDirectory() {
        for(index in 0 until 8) {
            val states=BooleanArray(8);states[index]=true
            assertTrue(userGroupSubviewActive(*states))
        }
    }
    @Test fun closingOneActionDoesNotExposeDirectoryIfAnotherRemains() {
        assertTrue(userGroupSubviewActive(false,true,false))
        assertFalse(userGroupSubviewActive(false,false,false))
    }
}
