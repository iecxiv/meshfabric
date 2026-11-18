package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopMouseModePolicyTest {
    @Test fun leftTogglesToRight() {
        assertEquals(MouseMode.RIGHT_CLICK, desktopToggleClickMode(MouseMode.LEFT_CLICK, MouseMode.LEFT_CLICK))
    }

    @Test fun rightTogglesToLeft() {
        assertEquals(MouseMode.LEFT_CLICK, desktopToggleClickMode(MouseMode.RIGHT_CLICK, MouseMode.RIGHT_CLICK))
    }

    @Test fun scrollRestoresRightWithoutToggling() {
        assertEquals(MouseMode.RIGHT_CLICK, desktopToggleClickMode(MouseMode.SCROLL, MouseMode.RIGHT_CLICK))
    }

    @Test fun scrollRestoresLeftWithoutToggling() {
        assertEquals(MouseMode.LEFT_CLICK, desktopToggleClickMode(MouseMode.SCROLL, MouseMode.LEFT_CLICK))
    }

    @Test fun currentRightOverridesOldMemory() {
        assertEquals(MouseMode.RIGHT_CLICK, desktopRememberedClickMode(MouseMode.RIGHT_CLICK, MouseMode.LEFT_CLICK))
    }

    @Test fun currentLeftOverridesOldMemory() {
        assertEquals(MouseMode.LEFT_CLICK, desktopRememberedClickMode(MouseMode.LEFT_CLICK, MouseMode.RIGHT_CLICK))
    }

    @Test fun scrollingPreservesRememberedClickMode() {
        for (remembered in listOf(MouseMode.LEFT_CLICK, MouseMode.RIGHT_CLICK)) {
            assertEquals(remembered, desktopRememberedClickMode(MouseMode.SCROLL, remembered))
        }
    }

    @Test fun unsupportedRememberedModeFallsBackToLeft() {
        for (mode in listOf(MouseMode.MIDDLE_CLICK, MouseMode.DRAG_MOVE, MouseMode.SCROLL)) {
            assertEquals(MouseMode.LEFT_CLICK, desktopToggleClickMode(MouseMode.SCROLL, mode))
        }
    }
}
