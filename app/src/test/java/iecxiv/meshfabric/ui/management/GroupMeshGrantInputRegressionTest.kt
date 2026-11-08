package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class GroupMeshGrantInputRegressionTest {
    @Test fun exactlyOneOptionalPrefixIsAccepted() {
        assertEquals(8L,profileGroupMeshGrantHex("8"))
        assertEquals(8L,profileGroupMeshGrantHex("0x8"))
        assertEquals(8L,profileGroupMeshGrantHex("0X8"))
        for(value in listOf("0x0X8","0X0x8","0x0x8","0X0X8")) assertNull(profileGroupMeshGrantHex(value))
    }
    @Test fun oversizedValuesAreNotReinterpretedAsTheirValidPrefix() {
        val value="0x000001080"
        assertNotNull(profileGroupMeshGrantHex(value.take(10)))
        assertNull(profileGroupMeshGrantHex(value))
        assertNull(profileGroupMeshGrantHex("0x000000108"))
        assertNull(profileGroupMeshGrantHex("1080000000000000000000000"))
    }
    @Test fun whitespaceIsOnlyAllowedAroundTheWholeValue() {
        assertEquals(264L,profileGroupMeshGrantHex(" 0X108 "))
        for(value in listOf("0x 108","1 08","0 x108","0x+108")) assertNull(profileGroupMeshGrantHex(value))
    }
}
