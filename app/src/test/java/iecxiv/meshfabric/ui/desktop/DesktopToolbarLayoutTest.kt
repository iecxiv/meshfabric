package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopToolbarLayoutTest {
    private fun aligned(width: Int, height: Int, diameter: Int, margin: Int, gap: Int, relative: Float, rtl: Boolean, full: Boolean) {
        val expandedWidth = (width - margin * 2).coerceAtLeast(0)
        val collapsedWidth = if (full) diameter * 2 + gap else diameter
        val expanded = desktopToolbarOrigin(width, height, expandedWidth, diameter, relative, margin, false, rtl)
        val collapsed = desktopToolbarOrigin(width, height, collapsedWidth, diameter, relative, margin, true, rtl)
        val a = if (rtl) expanded.first + expandedWidth - diameter / 2f else expanded.first + diameter / 2f
        val b = if (rtl) collapsed.first + collapsedWidth - diameter / 2f else collapsed.first + diameter / 2f
        assertEquals(a, b, 0f)
        assertEquals(expanded.second, collapsed.second)
    }

    @Test fun leftToRightAnchorDoesNotMove() {
        for (f in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) aligned(400, 800, 42, 12, 8, f, false, false)
    }
    @Test fun fullScreenExitDoesNotMoveTheAnchor() {
        for (f in listOf(0f, 0.5f, 1f)) aligned(400, 900, 42, 12, 8, f, false, true)
    }
    @Test fun rightToLeftAnchorDoesNotMove() {
        for (f in listOf(0f, 0.5f, 1f)) aligned(400, 800, 42, 12, 8, f, true, false)
    }
    @Test fun rightToLeftFullScreenExitDoesNotMoveTheAnchor() {
        for (f in listOf(0f, 0.5f, 1f)) aligned(400, 900, 42, 12, 8, f, true, true)
    }
    @Test fun oddPixelDiametersKeepTheSameCenter() {
        for (diameter in 1..101) {
            val padding = minOf(6, diameter / 2)
            val slot = diameter - padding * 2
            assertEquals(diameter / 2f, padding + slot / 2f, 0f)
            aligned(563, 997, diameter, 17, 11, 0.37f, false, true)
        }
    }
    @Test fun normalDiameterMatchesExpandedHeight() {
        assertEquals(42, desktopToolbarDiameter(400, 800, 42, 12, 8))
    }
    @Test fun shortViewportBoundsBothFormsEqually() {
        assertEquals(20, desktopToolbarDiameter(400, 20, 42, 12, 8))
        aligned(400, 20, 20, 12, 8, 1f, false, true)
    }
    @Test fun narrowViewportReservesTheExitButtonAndGap() {
        val diameter = desktopToolbarDiameter(100, 800, 42, 12, 8)
        assertEquals(34, diameter)
        assertTrue(diameter * 2 + 8 <= 100 - 24)
        aligned(100, 800, diameter, 12, 8, 0.5f, false, true)
    }
    @Test fun normalizedPositionsReachBothVerticalEdges() {
        assertEquals(758, desktopToolbarOrigin(400, 800, 42, 42, -1f, 12, true, false).second)
        assertEquals(0, desktopToolbarOrigin(400, 800, 42, 42, 2f, 12, true, false).second)
    }
    @Test fun nonFinitePositionHasASafeFallback() {
        for (f in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(758, desktopToolbarOrigin(400, 800, 42, 42, f, 12, true, false).second)
        }
    }
    @Test fun invalidDimensionsDoNotCreateNegativeOrigins() {
        assertEquals(0, desktopToolbarDiameter(-1, -1, 42, 12, 8))
        assertEquals(0 to 0, desktopToolbarOrigin(-1, -1, 42, 42, 1f, 12, true, true))
    }
    @Test fun marginsAreClampedInsideTheViewport() {
        assertEquals(0, desktopToolbarOrigin(20, 800, 42, 42, 0f, 12, true, false).first)
        assertEquals(0, desktopToolbarOrigin(20, 800, 42, 42, 0f, 12, true, true).first)
    }
    @Test fun newSessionAlwaysStartsWithLeftClick() {
        for (mode in MouseMode.entries) assertEquals(MouseMode.LEFT_CLICK, desktopToolbarStartMouseMode(true, mode))
    }
    @Test fun existingSessionKeepsItsMode() {
        for (mode in MouseMode.entries) assertEquals(mode, desktopToolbarStartMouseMode(false, mode))
    }
}
