package iecxiv.meshfabric.ui.terminal

internal val terminalBaseKeyIds = listOf(
    "esc", "tab", "ctrl-c", "enter", "backspace", "left", "down", "up", "right",
    "home", "end", "insert", "delete", "pageup", "pagedown"
)
internal val terminalCatalogKeyIds: List<String> =
    terminalBaseKeyIds + ('a'..'z').map { "ctrl-$it" }.filter { it != "ctrl-c" }
internal val terminalDefaultKeyIds: List<String> =
    terminalBaseKeyIds + listOf("ctrl-z", "ctrl-x", "ctrl-o", "ctrl-w")

internal fun parseTerminalKeyIds(raw: String?): List<String> {
    if (raw == null) return terminalDefaultKeyIds
    val allowed = terminalCatalogKeyIds.toSet()
    return raw.split(',').map { it.trim() }.filter { it in allowed }.distinct()
}

internal fun serializeTerminalKeyIds(ids: List<String>): String {
    val allowed = terminalCatalogKeyIds.toSet()
    return ids.filter { it in allowed }.distinct().joinToString(",")
}
