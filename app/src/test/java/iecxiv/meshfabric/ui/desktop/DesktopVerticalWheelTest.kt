package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopVerticalWheelTest {
    private val geometry = DesktopWheelGeometry(200f, 200f, 160f, 80f, 1f, 0f, 0f)

    @Test fun centerAndTopLeftProjectToRemotePixels() {
        assertEquals(100 to 50, geometry.project(100f, 100f)!!.remote(200, 100))
        assertEquals(0 to 0, geometry.project(20f, 60f)!!.remote(200, 100))
    }

    @Test fun letterboxingAndOutsideViewportAreIgnored() {
        assertNull(geometry.project(19f, 100f))
        assertNull(geometry.project(100f, 59f))
        assertNull(geometry.project(180f, 100f))
        assertNull(geometry.project(100f, 140f))
        assertNull(geometry.project(-1f, 100f))
        assertNull(geometry.copy(scale = 4f).project(201f, 100f))
    }

    @Test fun zoomAndPanUseTheDisplayedImageTransform() {
        val zoomed = geometry.copy(scale = 2f, offsetX = 20f, offsetY = -10f)
        assertEquals(100 to 50, zoomed.project(120f, 90f)!!.remote(200, 100))
    }

    @Test fun invalidGeometryAndResolutionAreIgnored() {
        assertNull(geometry.copy(scale = 0f).project(100f, 100f))
        assertNull(geometry.copy(imageWidth = 0f).project(100f, 100f))
        assertNull(geometry.project(Float.NaN, 100f))
        assertNull(geometry.copy(offsetX = Float.POSITIVE_INFINITY).project(100f, 100f))
        assertNull(geometry.project(100f, 100f)!!.remote(0, 100))
    }

    @Test fun remoteCoordinatesStayWithinPixelBounds() {
        assertEquals(199 to 99, DesktopWheelPoint(0.99999f, 0.99999f).remote(200, 100))
    }

    @Test fun verticalDirectionAndReverseAreMappedOnce() {
        assertEquals(120, DesktopVerticalWheelAccumulator(false).push(-1f))
        assertEquals(-120, DesktopVerticalWheelAccumulator(false).push(1f))
        assertEquals(-120, DesktopVerticalWheelAccumulator(true).push(-1f))
        assertEquals(120, DesktopVerticalWheelAccumulator(true).push(1f))
    }

    @Test fun fractionalWheelValuesAreAccumulated() {
        val accumulator = DesktopVerticalWheelAccumulator(false)
        assertEquals(0, accumulator.push(-0.005f))
        assertEquals(1, accumulator.push(-0.005f))
        accumulator.reset()
        assertEquals(0, accumulator.push(0f))
    }

    @Test fun invalidAndExtremeValuesAreSafe() {
        val accumulator = DesktopVerticalWheelAccumulator(false)
        assertEquals(0, accumulator.push(Float.NaN))
        assertEquals(0, accumulator.push(Float.POSITIVE_INFINITY))
        assertEquals(120, accumulator.push(-1f))
        assertEquals(-32767, accumulator.push(Float.MAX_VALUE))
        assertEquals(32767, accumulator.push(-Float.MAX_VALUE))
    }
}
