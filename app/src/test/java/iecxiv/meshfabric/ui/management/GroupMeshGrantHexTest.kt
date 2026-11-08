package iecxiv.meshfabric.ui.management
import org.junit.Assert.*
import org.junit.Test
class GroupMeshGrantHexTest {
    @Test fun explicitHexOnly() {assertEquals(8L,profileGroupMeshGrantHex("0x8"));assertEquals(264L,profileGroupMeshGrantHex("0X108"));assertEquals(264L,profileGroupMeshGrantHex(" 108 "))}
    @Test fun emptyMalformedAndOverflowAreRejected() {for(value in listOf("","0x","-8","+8","1 08","xyz","100000000")) assertNull(profileGroupMeshGrantHex(value))}
    @Test fun zeroManagementAndUnknownBitsAreRejected() {for(value in listOf("0","1","2","4","9","FFFFFFFF","80000008")) assertNull(profileGroupMeshGrantHex(value))}
}
