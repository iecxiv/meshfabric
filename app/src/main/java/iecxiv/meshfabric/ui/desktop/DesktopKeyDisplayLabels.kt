package iecxiv.meshfabric.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import iecxiv.meshfabric.R

internal fun desktopKeyDisplayResource(key: Int): Int? = when (key) {
    16 -> R.string.desktop_key_shift
    8 -> R.string.desktop_key_backspace
    13 -> R.string.desktop_key_enter
    19 -> R.string.desktop_key_pause
    20 -> R.string.desktop_key_caps_lock
    32 -> R.string.desktop_key_space
    33 -> R.string.desktop_key_page_up
    34 -> R.string.desktop_key_page_down
    35 -> R.string.desktop_key_end
    36 -> R.string.desktop_key_home
    37 -> R.string.desktop_key_left
    38 -> R.string.desktop_key_up
    39 -> R.string.desktop_key_right
    40 -> R.string.desktop_key_down
    44 -> R.string.desktop_key_print_screen
    45 -> R.string.desktop_key_insert
    46 -> R.string.desktop_key_delete
    144 -> R.string.desktop_key_num_lock
    145 -> R.string.desktop_key_scroll_lock
    else -> null
}

@Composable
internal fun desktopKeyDisplayLabel(key: Int): String {
    val resource = desktopKeyDisplayResource(key)
    return if (resource == null) desktopKeyLabel(key) else stringResource(resource)
}

@Composable
internal fun desktopShortcutDisplayLabel(shortcut: DesktopShortcut): String {
    val labels = ArrayList<String>(shortcut.keys.size)
    for (key in shortcut.keys) labels.add(desktopKeyDisplayLabel(key))
    return labels.joinToString(" + ")
}
