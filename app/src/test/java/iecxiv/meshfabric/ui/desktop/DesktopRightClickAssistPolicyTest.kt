package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopRightClickAssistPolicyTest {
    @Test fun oneShotDoesNotChangeVisibility() {
        val arm = DesktopRightClickArm(true)
        arm.toggle(); assertTrue(arm.consume()); assertFalse(arm.consume()); assertTrue(arm.visible)
    }
    @Test fun hidingAndCancellationDisarm() {
        val arm = DesktopRightClickArm(true)
        arm.toggle(); arm.show(false); assertFalse(arm.consume())
        arm.show(true); assertFalse(arm.armed)
        arm.toggle(); arm.cancel(); assertFalse(arm.consume())
    }
    @Test fun hiddenControlCannotArm() { val arm = DesktopRightClickArm(); arm.toggle(); assertFalse(arm.consume()) }
    @Test fun expandedAndCollapsedBarsNeverOverlap() {
        for (bar in listOf(DesktopAssistBounds(12, 260, 348, 320), DesktopAssistBounds(12, 260, 60, 308))) {
            for (x in listOf(0f, 0.5f, 1f)) for (y in listOf(0f, 0.5f, 1f)) {
                val p = requireNotNull(desktopAssistPosition(360, 640, 48, bar, x, y, 12, 8))
                assertTrue(p.first + 48 <= bar.left - 8 || p.first >= bar.right + 8 || p.second + 48 <= bar.top - 8 || p.second >= bar.bottom + 8)
            }
        }
    }
    @Test fun noSpaceHidesInsteadOfOverlapping() {
        assertNull(desktopAssistPosition(100, 100, 48, DesktopAssistBounds(0, 0, 100, 100), 0f, 0f, 12, 8))
        assertNull(desktopAssistPosition(40, 40, 48, DesktopAssistBounds(0, 0, 10, 10), 1f, 1f, 12, 8))
    }
    @Test fun rotationAndInvalidCoordinatesStayWithinCanvas() {
        for ((w, h) in listOf(360 to 640, 640 to 360)) {
            val p = requireNotNull(desktopAssistPosition(w, h, 48, DesktopAssistBounds(12, h - 70, w - 12, h), Float.NaN, Float.POSITIVE_INFINITY, 12, 8))
            assertTrue(p.first in 12..w - 60); assertTrue(p.second in 12..h - 60)
        }
    }
}
