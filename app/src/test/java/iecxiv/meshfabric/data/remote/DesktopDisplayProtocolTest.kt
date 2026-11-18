package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class DesktopDisplayProtocolTest {
    private fun response(ids: List<Int>, selected: Int): ByteArray {
        val packet = ByteArray(8 + ids.size * 2)
        fun word(offset: Int, value: Int) {
            packet[offset] = (value ushr 8).toByte()
            packet[offset + 1] = value.toByte()
        }
        word(0, 11)
        word(2, packet.size)
        word(4, ids.size)
        ids.forEachIndexed { index, id -> word(6 + index * 2, id) }
        word(6 + ids.size * 2, selected)
        return packet
    }

    @Test fun requestUsesCommand11AndLength4() {
        assertArrayEquals(byteArrayOf(0, 11, 0, 4), DesktopDisplayProtocol.request())
    }

    @Test fun selectionUsesCommand12AndLength6() {
        assertArrayEquals(byteArrayOf(0, 12, 0, 6, 0, 2), DesktopDisplayProtocol.select(2))
    }

    @Test fun allUsesUnsigned65535() {
        assertArrayEquals(byteArrayOf(0, 12, 0, 6, -1, -1), DesktopDisplayProtocol.select(DesktopDisplayProtocol.ALL))
    }

    @Test fun selectionPreservesBothIdBytes() {
        assertArrayEquals(byteArrayOf(0, 12, 0, 6, 0x12, 0x34), DesktopDisplayProtocol.select(0x1234))
    }

    @Test fun outOfRangeSelectionIsRejected() {
        assertNull(DesktopDisplayProtocol.select(-1))
        assertNull(DesktopDisplayProtocol.select(65536))
    }

    @Test fun sparseIdsAreNotMenuIndices() {
        assertEquals(DesktopDisplayState(listOf(2, 7, 65535), 7),
            DesktopDisplayProtocol.decode(response(listOf(2, 7, 65535), 7)))
    }

    @Test fun allSelectionComesFromServer() {
        assertEquals(65535, DesktopDisplayProtocol.decode(response(listOf(1, 2, 65535), 65535))?.selectedId)
    }

    @Test fun allIsNotInventedWhenNotAdvertised() {
        assertEquals(listOf(4), DesktopDisplayProtocol.decode(response(listOf(4), 4))?.ids)
    }

    @Test fun highAndZeroIdsRemainUnsigned() {
        assertEquals(listOf(0, 32768), DesktopDisplayProtocol.decode(response(listOf(0, 32768), 32768))?.ids)
    }

    @Test fun emptyReportsAreSupported() {
        assertEquals(DesktopDisplayState(), DesktopDisplayProtocol.decode(byteArrayOf(0, 11, 0, 6, 0, 0)))
        assertEquals(DesktopDisplayState(), DesktopDisplayProtocol.decode(response(emptyList(), 0)))
    }

    @Test fun truncatedPacketsAreRejected() {
        val packet = response(listOf(1, 2, 65535), 1)
        for (size in 0 until packet.size) assertNull(DesktopDisplayProtocol.decode(packet.copyOf(size)))
    }

    @Test fun wrongLengthAndCommandAreRejected() {
        val length = response(listOf(1), 1).also { it[3] = 4 }
        val command = response(listOf(1), 1).also { it[1] = 12 }
        assertNull(DesktopDisplayProtocol.decode(length))
        assertNull(DesktopDisplayProtocol.decode(command))
    }

    @Test fun inconsistentCountIsRejected() {
        assertNull(DesktopDisplayProtocol.decode(response(listOf(1), 1).also { it[5] = 2 }))
    }

    @Test fun duplicateIdsAndUnknownSelectionAreRejected() {
        assertNull(DesktopDisplayProtocol.decode(response(listOf(1, 1), 1)))
        assertNull(DesktopDisplayProtocol.decode(response(listOf(1, 2), 3)))
    }

    @Test fun malformedReplyCannotReplaceConfirmedState() {
        val confirmed = DesktopDisplayProtocol.decode(response(listOf(1, 65535), 1))!!
        val replacement = DesktopDisplayProtocol.decode(byteArrayOf(0, 11, 0, 4)) ?: confirmed
        assertEquals(confirmed, replacement)
        assertNull(confirmed.pendingId)
        assertFalse(confirmed.selectionUnconfirmed)
    }
}
