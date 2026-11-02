package iecxiv.meshfabric.ui.desktop

/** Preserve existing ASCII virtual keys; use KVM command 85 for other UTF-16 units. */
internal object DesktopTextEncoding {
    private fun key(code: Int, up: Int) = byteArrayOf(0, 1, 0, 6, up.toByte(), code.toByte())

    fun packets(text: String): Sequence<ByteArray> = sequence {
        for (char in text) {
            val vk = when (char) {
                in 'a'..'z' -> 0x41 + (char - 'a')
                in 'A'..'Z' -> 0x41 + (char - 'A')
                in '0'..'9' -> 0x30 + (char - '0')
                ' ' -> 0x20
                '\n', '\r' -> 0x0D
                '\t' -> 0x09
                '\b' -> 0x08
                else -> null
            }
            if (vk != null) {
                val shift = char in 'A'..'Z'
                if (shift) yield(key(0x10, 0))
                yield(key(vk, 0))
                yield(key(vk, 1))
                if (shift) yield(key(0x10, 1))
            } else {
                for (up in 0..1) {
                    yield(byteArrayOf(0, 85, 0, 7, up.toByte(),
                        (char.code ushr 8).toByte(), char.code.toByte()))
                }
            }
        }
    }
}
