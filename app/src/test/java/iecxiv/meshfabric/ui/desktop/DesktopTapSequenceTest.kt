package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopTapSequenceTest {
    private data class Sent(val socket: Any, val target: DesktopTapTarget, val count: Int)
    private class Timer(val fire: () -> Unit) { var cancelled = false }
    private class Fixture {
        var socket: Any? = Any()
        val timers = mutableListOf<Timer>()
        val sent = mutableListOf<Sent>()
        var accept = true
        val clicks = DesktopTapSequence(
            binding = { socket },
            schedule = { _, fire ->
                val timer = Timer(fire)
                timers += timer
                val cancel: () -> Unit = { timer.cancelled = true }
                cancel
            },
            emit = { socket, target, count -> sent += Sent(socket, target, count); accept }
        )
        fun tap(x: Int = 10, y: Int = 20, swapped: Boolean = false) =
            clicks.tap(DesktopTapTarget(x, y, swapped), 300L, socket)
    }

    @Test fun singleClickWaitsForTheTimer() {
        val f = Fixture()
        assertTrue(f.tap())
        assertTrue(f.sent.isEmpty())
        f.timers.single().fire()
        assertEquals(listOf(1), f.sent.map { it.count })
    }
    @Test fun secondDownReservesTheFirstTapUntilRelease() {
        val f = Fixture()
        f.tap()
        assertTrue(f.clicks.reserveSecond())
        assertTrue(f.timers.single().cancelled)
        f.timers.single().fire()
        assertTrue(f.sent.isEmpty())
        f.tap(11, 21)
        assertEquals(listOf(2), f.sent.map { it.count })
        assertEquals(DesktopTapTarget(10, 20, false), f.sent.single().target)
    }
    @Test fun timerCannotCreateAnExtraClickAfterADouble() {
        val f = Fixture()
        f.tap(); f.clicks.reserveSecond(); f.tap()
        f.timers.single().fire()
        f.clicks.flush()
        assertEquals(listOf(2), f.sent.map { it.count })
    }
    @Test fun dragFlushesOnlyTheFirstClick() {
        val f = Fixture()
        f.tap(); f.clicks.reserveSecond(); f.clicks.flush()
        assertEquals(listOf(1), f.sent.map { it.count })
        f.timers.single().fire()
        assertEquals(1, f.sent.size)
    }
    @Test fun cancellationNeverSendsThePendingClick() {
        val f = Fixture()
        f.tap(); f.clicks.cancel(); f.timers.single().fire()
        assertTrue(f.sent.isEmpty())
    }
    @Test fun disconnectedOrReplacementSocketNeverReceivesTheOldClick() {
        for (replacement in listOf<Any?>(null, Any())) {
            val f = Fixture()
            f.tap(); f.socket = replacement; f.timers.single().fire()
            assertTrue(f.sent.isEmpty())
        }
    }
    @Test fun gestureStartedOnAnOldSocketCannotQueueAClickOnTheNewOne() {
        val f = Fixture()
        val startedOn = f.clicks.currentBinding()
        f.socket = Any()
        assertFalse(f.clicks.tap(DesktopTapTarget(1, 2, false), 300L, startedOn))
        assertTrue(f.timers.isEmpty())
        assertTrue(f.sent.isEmpty())
    }
    @Test fun secondDownAfterReconnectDoesNotReserveTheOldTap() {
        val f = Fixture()
        f.tap(); f.socket = Any()
        assertFalse(f.clicks.reserveSecond())
        f.tap(); f.timers.last().fire()
        assertEquals(listOf(1), f.sent.map { it.count })
        assertSame(f.socket, f.sent.single().socket)
    }
    @Test fun staleTimerCannotFlushANewerTap() {
        val f = Fixture()
        f.tap(); f.clicks.flush(); f.tap(50, 60)
        f.timers.first().fire()
        assertEquals(1, f.sent.size)
        f.timers.last().fire()
        assertEquals(DesktopTapTarget(50, 60, false), f.sent.last().target)
    }
    @Test fun failedSendIsNotRetried() {
        val f = Fixture()
        f.accept = false
        f.tap(); f.clicks.reserveSecond()
        assertFalse(f.tap())
        f.clicks.flush(); f.timers.single().fire()
        assertEquals(1, f.sent.size)
    }
    @Test fun threeTapsAreADoubleThenASingle() {
        val f = Fixture()
        f.tap(); f.clicks.reserveSecond(); f.tap(); f.tap()
        f.timers.last().fire()
        assertEquals(listOf(2, 1), f.sent.map { it.count })
    }
    @Test fun swappedButtonsCannotBeMergedWithAnOlderUnswappedClick() {
        val f = Fixture()
        f.tap(); f.clicks.reserveSecond(); f.tap(swapped = true)
        assertEquals(listOf(1), f.sent.map { it.count })
        f.timers.last().fire()
        assertTrue(f.sent.last().target.swapped)
    }
    @Test fun packetOrderUsesTheSamePositionAndDoesNotUseTheIgnoredDoubleFlag() {
        val packets = desktopTapPackets(DesktopTapTarget(0x1234, 0x5678, false), 2)
        assertEquals(listOf(0x00, 0x02, 0x04, 0x02, 0x04), packets.map { it[5].toInt() and 255 })
        for (packet in packets) {
            assertArrayEquals(byteArrayOf(0, 2, 0, 10, 0), packet.copyOfRange(0, 5))
            assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78), packet.copyOfRange(6, 10))
        }
        assertEquals(listOf(0x00, 0x08, 0x10, 0x08, 0x10),
            desktopTapPackets(DesktopTapTarget(1, 2, true), 2).map { it[5].toInt() and 255 })
    }
}
