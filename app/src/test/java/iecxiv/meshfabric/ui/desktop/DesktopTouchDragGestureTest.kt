package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopTouchDragGestureTest {
    private class Fixture {
        val events = mutableListOf<String>()
        val start = DesktopWheelPoint(0.1f, 0.2f)
        val target = DesktopWheelPoint(0.4f, 0.5f)
        var available = true
        val drag = DesktopTouchDragGesture(start, 8f,
            press = { events += "down:$it"; available },
            move = { events += "move:$it"; available },
            release = { events += "up" })
    }
    @Test fun stationaryHoldRemainsATapWithoutAnySyntheticRightClick() {
        val f = Fixture()
        repeat(100) { f.drag.move(f.start, 0f) }
        assertTrue(f.events.isEmpty())
        assertTrue(f.drag.finish())
        assertFalse(f.drag.finish())
    }
    @Test fun thresholdStartsAtOriginalPositionAndHoldsUntilRelease() {
        val f = Fixture()
        f.drag.move(f.target, 9f)
        f.drag.move(f.target, 12f)
        assertEquals(listOf("down:${f.start}", "move:${f.target}", "move:${f.target}"), f.events)
        assertFalse(f.drag.finish())
        assertEquals("up", f.events.last())
        f.drag.cancel()
        assertEquals(1, f.events.count { it == "up" })
    }
    @Test fun multiTouchOrCancellationReleasesAndNeverResumesTheSameGesture() {
        val f = Fixture()
        f.drag.move(f.target, 10f)
        f.drag.cancel()
        val count = f.events.size
        f.drag.move(f.target, 30f)
        assertFalse(f.drag.finish())
        assertTrue(f.drag.isCancelled)
        assertEquals(count, f.events.size)
        assertEquals(1, f.events.count { it == "up" })
    }
    @Test fun cancelBeforeMovementDoesNotClick() {
        val f = Fixture(); f.drag.cancel()
        assertFalse(f.drag.finish()); assertTrue(f.events.isEmpty())
    }
    @Test fun leavingTheImageReleasesTheButton() {
        val f = Fixture(); f.drag.move(f.target, 10f); f.drag.move(null, 20f)
        assertTrue(f.drag.isCancelled); assertEquals("up", f.events.last()); assertFalse(f.drag.finish())
    }
    @Test fun failedPressIsNotRetried() {
        val f = Fixture(); f.available = false
        f.drag.move(f.target, 10f); f.drag.move(f.target, 20f)
        assertTrue(f.drag.isCancelled); assertEquals(1, f.events.size); assertFalse(f.drag.finish())
    }
    @Test fun failedMoveReleasesOnceAndSuppressesTap() {
        val f = Fixture(); f.drag.move(f.target, 10f); f.available = false; f.drag.move(f.target, 20f)
        assertTrue(f.drag.isCancelled); assertEquals(1, f.events.count { it == "up" }); assertFalse(f.drag.finish())
    }
}
