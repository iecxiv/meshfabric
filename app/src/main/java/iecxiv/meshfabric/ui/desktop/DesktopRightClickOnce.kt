package iecxiv.meshfabric.ui.desktop

/** With one-shot right click on, a right click returns the mode button to left click after one use. */
internal fun desktopModeAfterClick(mode: MouseMode, oneShot: Boolean): MouseMode =
    if (oneShot && mode == MouseMode.RIGHT_CLICK) MouseMode.LEFT_CLICK else mode
