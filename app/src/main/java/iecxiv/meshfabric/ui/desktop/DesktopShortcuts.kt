package iecxiv.meshfabric.ui.desktop

internal data class DesktopShortcut(val keys: List<Int>) {
    init { require(keys.isNotEmpty() && keys.size <= 5 && keys.distinct() == keys && keys.all { it in 1..255 }) }
    val label: String get() = keys.joinToString(" + ") { desktopKeyLabel(it) }
}

internal fun desktopKeyLabel(key: Int): String = when (key) {
    16 -> "Shift"; 17 -> "Ctrl"; 18 -> "Alt"; 91 -> "Win"
    8 -> "Backspace"; 9 -> "Tab"; 13 -> "Enter"; 19 -> "Pause"; 20 -> "Caps Lock"
    27 -> "Esc"; 32 -> "Space"; 33 -> "Page Up"; 34 -> "Page Down"
    35 -> "End"; 36 -> "Home"; 37 -> "Left"; 38 -> "Up"; 39 -> "Right"; 40 -> "Down"
    44 -> "Print Screen"; 45 -> "Insert"; 46 -> "Delete"; 144 -> "Num Lock"; 145 -> "Scroll Lock"
    in 48..57, in 65..90 -> key.toChar().toString()
    in 112..135 -> "F${key - 111}"
    else -> "VK $key"
}

internal object DesktopShortcuts {
    val defaults: List<DesktopShortcut> get() = listOf(
        listOf(17, 18, 46), listOf(91), listOf(91, 40), listOf(91, 38),
        listOf(91, 76), listOf(91, 77), listOf(16, 91, 77)
    ).map(::DesktopShortcut)
    val selectableKeys = (listOf(8, 9, 13, 19, 20, 27, 32, 33, 34, 35, 36, 37, 38, 39, 40, 44, 45, 46) +
        (48..57).toList() + (65..90).toList() + (112..135).toList() + listOf(144, 145))
    fun encode(items: List<DesktopShortcut>): String {
        require(items.size <= 64)
        return items.joinToString(";") { it.keys.joinToString(",") }
    }
    fun decode(value: String?): List<DesktopShortcut> {
        if (value == null || value.length > 8192) return defaults
        if (value.isEmpty()) return emptyList()
        return runCatching {
            val rows = value.split(';')
            require(rows.size <= 64)
            rows.map { row -> DesktopShortcut(row.split(',').map { it.toInt() }) }
        }.getOrElse { defaults }
    }
}
