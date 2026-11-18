package iecxiv.meshfabric.data.remote

/** Display IDs are protocol IDs, not zero-based menu positions. */
data class DesktopDisplayState(
    val ids: List<Int> = emptyList(),
    val selectedId: Int? = null,
    val pendingId: Int? = null,
    val selectionUnconfirmed: Boolean = false,
    val connectionGeneration: Long = 0L
)

internal object DesktopDisplayProtocol {
    const val ALL = 0xFFFF

    fun request(): ByteArray = byteArrayOf(0, 11, 0, 4)

    fun select(id: Int): ByteArray? {
        if (id !in 0..0xFFFF) return null
        return byteArrayOf(0, 12, 0, 6, (id ushr 8).toByte(), id.toByte())
    }

    fun decode(packet: ByteArray): DesktopDisplayState? {
        if (packet.size < 6 || word(packet, 0) != 11) return null
        val length = word(packet, 2)
        if (length != packet.size) return null
        val count = word(packet, 4)
        if (count == 0) {
            return if (length == 6 || length == 8) DesktopDisplayState() else null
        }
        if (length != 8 + count * 2) return null
        val ids = (0 until count).map { word(packet, 6 + it * 2) }
        if (ids.distinct().size != ids.size) return null
        val selected = word(packet, 6 + count * 2)
        if (selected !in ids) return null
        return DesktopDisplayState(ids = ids, selectedId = selected)
    }

    private fun word(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)
}
