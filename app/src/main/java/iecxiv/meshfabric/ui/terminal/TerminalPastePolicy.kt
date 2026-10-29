package iecxiv.meshfabric.ui.terminal

internal enum class TerminalPasteIssue { EMPTY, TOO_LARGE, CONTROL, UNICODE, CLIPBOARD }
internal enum class TerminalPasteResult { DISPATCHED, REJECTED, UNKNOWN }
internal object TerminalPastePolicy {
    const val MAX_CHARS = 16384
    fun issue(text: CharSequence?): TerminalPasteIssue? {
        if (text.isNullOrEmpty()) return TerminalPasteIssue.EMPTY
        if (text.length > MAX_CHARS) return TerminalPasteIssue.TOO_LARGE
        var i = 0
        while (i < text.length) {
            val c = text[i].code
            if ((c < 32 && c != 9 && c != 10 && c != 13) || c in 127..159 ||
                c in 0x202A..0x202E || c in 0x2066..0x2069) return TerminalPasteIssue.CONTROL
            if (c in 0xD800..0xDBFF) {
                if (i + 1 >= text.length || text[i + 1].code !in 0xDC00..0xDFFF) return TerminalPasteIssue.UNICODE
                i++
            } else if (c in 0xDC00..0xDFFF) return TerminalPasteIssue.UNICODE
            i++
        }
        return null
    }
    fun multiline(text: String): Boolean = text.any { it.code == 10 || it.code == 13 || it.code == 0x2028 || it.code == 0x2029 }
}
