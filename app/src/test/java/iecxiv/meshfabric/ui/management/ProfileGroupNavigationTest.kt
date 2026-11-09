package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class ProfileGroupNavigationTest {
    @Test fun blankSearchMatches() { assertTrue(deviceGroupMatches("  ","Office","Servers")) }
    @Test fun searchMatchesNameAndDescription() { assertTrue(deviceGroupMatches("office","OFFICE",""));assertTrue(deviceGroupMatches("servers","Office","Linux servers"));assertFalse(deviceGroupMatches("absent","Office","Servers")) }
    @Test fun fullAndEmptyRightsAreReadable() { assertEquals("Administrador del grupo",deviceGroupRightsLabel(0xffffffffL,true));assertEquals("No direct rights",deviceGroupRightsLabel(0L,false)) }
    @Test fun knownAndUnknownRightsAreNotHex() { assertEquals("Edit group",deviceGroupRightsLabel(1L,false));assertEquals("Additional rights",deviceGroupRightsLabel(0x80000000L,false)) }
    @Test fun userContextCanBeRestoredWhenStillVisible() { assertEquals("user//person",profileReturnUserId("user//person",setOf("user//person"),true)) }
    @Test fun deletedOrDisconnectedUserIsNotRestored() { assertNull(profileReturnUserId("user//person",emptySet(),true));assertNull(profileReturnUserId("user//person",setOf("user//person"),false));assertNull(profileReturnUserId(null,emptySet(),true)) }
}
