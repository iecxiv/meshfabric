package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopTwoFingerTapTest {
    private fun p(id: Long, x: Float = 0f, y: Float = 0f) = DesktopPointerSample(id, x, y)
    private fun detector() = DesktopTwoFingerTap(slop = 10f, timeoutMillis = 500L)

    @Test fun twoFingersLiftedWithoutMovingAreATap() {
        val d = detector()
        d.update(0, listOf(p(1, 10f, 10f)))
        d.update(20, listOf(p(1, 10f, 10f), p(2, 100f, 100f)))
        d.update(120, listOf(p(1, 11f, 10f), p(2, 100f, 101f)))
        assertTrue(d.isTap(130))
    }

    @Test fun oneFingerIsNotATap() {
        val d = detector()
        d.update(0, listOf(p(1)))
        d.update(50, listOf(p(1)))
        assertFalse(d.isTap(60))
    }

    @Test fun movingBeyondTheSlopIsNotATap() {
        val d = detector()
        d.update(0, listOf(p(1, 10f, 10f)))
        d.update(20, listOf(p(1, 10f, 10f), p(2, 100f, 100f)))
        d.update(80, listOf(p(1, 10f, 10f), p(2, 140f, 100f)))
        assertFalse(d.isTap(100))
    }

    @Test fun threeFingersAreNotATap() {
        val d = detector()
        d.update(0, listOf(p(1), p(2, 50f, 0f), p(3, 100f, 0f)))
        assertFalse(d.isTap(50))
    }

    @Test fun aSlowTouchIsNotATap() {
        val d = detector()
        d.update(0, listOf(p(1)))
        d.update(20, listOf(p(1), p(2, 80f, 0f)))
        assertFalse(d.isTap(900))
    }

    @Test fun fingersOneAfterAnotherAreNotATap() {
        val d = detector()
        d.update(0, listOf(p(1)))
        d.update(100, listOf(p(2, 80f, 0f)))
        assertFalse(d.isTap(150))
    }

    @Test fun resetStartsOver() {
        val d = detector()
        d.update(0, listOf(p(1), p(2, 80f, 0f), p(3, 160f, 0f)))
        d.reset()
        d.update(1000, listOf(p(4)))
        d.update(1010, listOf(p(4), p(5, 80f, 0f)))
        assertTrue(d.isTap(1050))
    }
}
