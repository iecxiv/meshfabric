package iecxiv.meshfabric.ui.desktop

internal object DesktopAndroidKeyMap {
    private val special = mapOf(
        19 to 0x26, 20 to 0x28, 21 to 0x25, 22 to 0x27, 23 to 0x0D,
        28 to 0x0C, 55 to 0xBC, 56 to 0xBE, 57 to 0x12, 58 to 0x12,
        59 to 0x10, 60 to 0x10, 61 to 0x09, 62 to 0x20, 66 to 0x0D,
        67 to 0x08, 68 to 0xC0, 69 to 0xBD, 70 to 0xBB, 71 to 0xDB,
        72 to 0xDD, 73 to 0xDC, 74 to 0xBA, 75 to 0xDE, 76 to 0xBF,
        82 to 0x5D, 92 to 0x21, 93 to 0x22, 111 to 0x1B, 112 to 0x2E,
        113 to 0x11, 114 to 0x11, 115 to 0x14, 116 to 0x91,
        117 to 0x5B, 118 to 0x5B, 120 to 0x2C, 121 to 0x13,
        122 to 0x24, 123 to 0x23, 124 to 0x2D, 143 to 0x90,
        154 to 0x6F, 155 to 0x6A, 156 to 0x6D, 157 to 0x6B,
        158 to 0x6E, 159 to 0x6C, 160 to 0x0D
    )
    fun virtualKey(code: Int): Int? = when (code) {
        in 29..54 -> 0x41 + code - 29
        in 7..16 -> 0x30 + code - 7
        in 131..142 -> 0x70 + code - 131
        in 144..153 -> 0x60 + code - 144
        else -> special[code]
    }
    fun modifier(code: Int): Boolean = code in listOf(57,58,59,60,113,114,117,118)
    fun noRepeat(code: Int): Boolean = modifier(code) || code in listOf(115,116,143)
    fun modifiers(meta: Int): List<Int> = buildList {
        if (meta and 1 != 0) add(0x10)
        if (meta and 0x1000 != 0) add(0x11)
        if (meta and 2 != 0) add(0x12)
        if (meta and 0x10000 != 0) add(0x5B)
    }
}

internal class DesktopKeyboardRouter(private val emit: (Int, Boolean) -> Unit) {
    private data class Identity(val device: Int, val code: Int)
    private data class Stroke(val vk: Int, val extras: List<Int>, val modifier: Boolean)
    private val held = linkedMapOf<Identity, Stroke>()
    private val references = mutableMapOf<Int, Int>()
    private val physicalModifiers = mutableMapOf<Int, Int>()
    private var active = false
    fun setActive(value: Boolean) { if (!value) releaseAll(); active = value }
    private fun press(vk: Int) {
        val count = references[vk] ?: 0
        references[vk] = count + 1
        if (count == 0) emit(vk, true)
    }
    private fun release(vk: Int) {
        val count = references[vk] ?: return
        if (count <= 1) { references.remove(vk); emit(vk, false) }
        else references[vk] = count - 1
    }
    private fun finish(stroke: Stroke) {
        if (stroke.modifier) {
            val count = physicalModifiers[stroke.vk] ?: 0
            if (count <= 1) physicalModifiers.remove(stroke.vk) else physicalModifiers[stroke.vk] = count - 1
        }
        release(stroke.vk)
        stroke.extras.asReversed().forEach(::release)
    }
    fun handle(action: Int, code: Int, repeat: Int = 0, meta: Int = 0, device: Int = 0): Boolean {
        if (!active || action !in 0..1) return false
        val identity = Identity(device, code)
        if (action == 1) {
            val stroke = held.remove(identity) ?: return false
            finish(stroke)
            return true
        }
        val vk = DesktopAndroidKeyMap.virtualKey(code) ?: return false
        if (held.containsKey(identity)) {
            if (repeat > 0 && !DesktopAndroidKeyMap.noRepeat(code)) emit(vk, true)
            return true
        }
        val modifier = DesktopAndroidKeyMap.modifier(code)
        val extras = if (modifier) emptyList() else DesktopAndroidKeyMap.modifiers(meta)
            .filter { it != vk && (physicalModifiers[it] ?: 0) == 0 }
        extras.forEach(::press)
        if (modifier) physicalModifiers[vk] = (physicalModifiers[vk] ?: 0) + 1
        press(vk)
        held[identity] = Stroke(vk, extras, modifier)
        return true
    }
    fun releaseAll() {
        held.values.toList().asReversed().forEach(::finish)
        held.clear()
        references.clear()
        physicalModifiers.clear()
    }
}
