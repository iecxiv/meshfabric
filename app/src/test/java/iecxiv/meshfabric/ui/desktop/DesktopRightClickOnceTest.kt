package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopRightClickOnceTest {
    @Test fun rightClickReturnsToLeftWhenOneShotIsOn() =
        assertEquals(MouseMode.LEFT_CLICK, desktopModeAfterClick(MouseMode.RIGHT_CLICK, true))

    @Test fun rightClickStaysWhenOneShotIsOff() =
        assertEquals(MouseMode.RIGHT_CLICK, desktopModeAfterClick(MouseMode.RIGHT_CLICK, false))

    @Test fun otherModesAreNeverChanged() {
        for (mode in MouseMode.entries.filter { it != MouseMode.RIGHT_CLICK }) {
            assertEquals(mode, desktopModeAfterClick(mode, true))
            assertEquals(mode, desktopModeAfterClick(mode, false))
        }
    }
}
