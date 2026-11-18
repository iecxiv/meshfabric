package iecxiv.meshfabric.ui.desktop

internal fun desktopRememberedClickMode(mode: MouseMode, remembered: MouseMode): MouseMode = when (mode) {
    MouseMode.LEFT_CLICK, MouseMode.RIGHT_CLICK -> mode
    else -> if (remembered == MouseMode.RIGHT_CLICK) MouseMode.RIGHT_CLICK else MouseMode.LEFT_CLICK
}

internal fun desktopToggleClickMode(mode: MouseMode, remembered: MouseMode): MouseMode = when (mode) {
    MouseMode.LEFT_CLICK -> MouseMode.RIGHT_CLICK
    MouseMode.RIGHT_CLICK -> MouseMode.LEFT_CLICK
    else -> desktopRememberedClickMode(mode, remembered)
}
