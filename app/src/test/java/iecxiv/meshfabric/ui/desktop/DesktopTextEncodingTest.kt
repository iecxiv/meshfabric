package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopTextEncodingTest {
    @Test fun periodAndCommaUseSevenByteUnicodeDownAndUp() {
        val packets = DesktopTextEncoding.packets(".,").toList()
        assertEquals(4, packets.size)
        assertArrayEquals(byteArrayOf(0,85,0,7,0,0,46), packets[0])
        assertArrayEquals(byteArrayOf(0,85,0,7,1,0,46), packets[1])
        assertArrayEquals(byteArrayOf(0,85,0,7,0,0,44), packets[2])
        assertArrayEquals(byteArrayOf(0,85,0,7,1,0,44), packets[3])
    }

    @Test fun punctuationAccentsAndSurrogateUnitsPreserveOrder() {
        val text = ".,;:-_@/\\!?()[]{}'\"\u00f1\u00e9\u20ac\uD83D\uDE00"
        val packets = DesktopTextEncoding.packets(text).toList()
        assertEquals(text.length * 2, packets.size)
        text.forEachIndexed { index, char ->
            for (up in 0..1) {
                assertArrayEquals(byteArrayOf(0,85,0,7,up.toByte(),
                    (char.code ushr 8).toByte(),char.code.toByte()), packets[index * 2 + up])
            }
        }
    }

    @Test fun asciiLettersDigitsSpaceAndControlsRetainVirtualKeys() {
        val text = "a0 \n\r\t\b"
        val expected = listOf(65,48,32,13,13,9,8)
        val packets = DesktopTextEncoding.packets(text).toList()
        assertEquals(expected.size * 2, packets.size)
        expected.forEachIndexed { index, vk ->
            for (up in 0..1) assertArrayEquals(byteArrayOf(0,1,0,6,up.toByte(),vk.toByte()), packets[index * 2 + up])
        }
    }

    @Test fun uppercaseKeepsBalancedShift() {
        val packets = DesktopTextEncoding.packets("A").toList()
        assertEquals(4, packets.size)
        assertArrayEquals(byteArrayOf(0,1,0,6,0,16), packets[0])
        assertArrayEquals(byteArrayOf(0,1,0,6,0,65), packets[1])
        assertArrayEquals(byteArrayOf(0,1,0,6,1,65), packets[2])
        assertArrayEquals(byteArrayOf(0,1,0,6,1,16), packets[3])
    }

    @Test fun emptyInputDoesNotProducePackets() {
        assertFalse(DesktopTextEncoding.packets("").iterator().hasNext())
    }
}
