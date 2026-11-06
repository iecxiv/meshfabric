package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class DesktopHoldKeysTest {
    @Test fun everyHoldTogglesDownThenUp() {
        desktopHoldKeys.forEach { key ->
            val events = mutableListOf<Pair<Int, Boolean>>()
            val keys = DesktopHeldKeys { code, down -> events.add(code to down) }
            keys.toggle(key)
            assertEquals(setOf(key), keys.held)
            keys.toggle(key)
            assertTrue(keys.held.isEmpty())
            assertEquals(listOf(key to true, key to false), events)
        }
    }
    @Test fun shortcutCannotReleaseAHeldModifier() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val keys = DesktopHeldKeys { key, down -> events.add(key to down) }
        keys.toggle(17)
        val shortcut = Any()
        keys.press(17, shortcut); keys.press(67, shortcut)
        keys.release(67, shortcut); keys.release(17, shortcut)
        assertEquals(setOf(17), keys.held)
        assertEquals(listOf(17 to true, 67 to true, 67 to false), events)
        keys.releaseHolds()
        assertEquals(17 to false, events.last())
    }
    @Test fun physicalReleaseDoesNotReleaseHold() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val keys = DesktopHeldKeys { key, down -> events.add(key to down) }
        keys.toggle(91); keys.physical(91, true); keys.physical(91, false)
        assertEquals(listOf(91 to true), events)
        keys.releaseHolds()
        assertEquals(91 to false, events.last())
    }
    @Test fun releaseHoldsDoesNotReleasePhysicalModifier() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val keys = DesktopHeldKeys { key, down -> events.add(key to down) }
        keys.physical(16, true); keys.toggle(16); keys.releaseHolds()
        assertEquals(listOf(16 to true), events)
        keys.physical(16, false)
        assertEquals(16 to false, events.last())
    }
    @Test fun releaseAllIsIdempotentAndClearsFourHolds() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val keys = DesktopHeldKeys { key, down -> events.add(key to down) }
        desktopHoldKeys.forEach(keys::toggle)
        keys.releaseAll(); keys.releaseAll()
        assertTrue(keys.held.isEmpty())
        assertEquals(8, events.size)
        assertEquals(desktopHoldKeys.asReversed().map { it to false }, events.takeLast(4))
    }
    @Test fun overlappingActionsReleaseOnlyLastReference() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val keys = DesktopHeldKeys { key, down -> events.add(key to down) }
        val first = Any(); val second = Any()
        keys.press(18, first); keys.press(18, second); keys.release(18, first)
        assertEquals(listOf(18 to true), events)
        keys.release(18, second)
        assertEquals(18 to false, events.last())
    }
    @Test fun allFourHoldsCanBeReleasedTogether() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val keys = DesktopHeldKeys { key, down -> events.add(key to down) }
        desktopHoldKeys.forEach(keys::toggle)
        keys.releaseHolds()
        assertTrue(keys.held.isEmpty())
        assertEquals(8, events.size)
    }
    @Test fun ordinaryPhysicalKeyRepeatsArePreserved() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val keys = DesktopHeldKeys { key, down -> events.add(key to down) }
        keys.physical(65, true); keys.physical(65, true); keys.physical(65, false)
        assertEquals(listOf(65 to true, 65 to true, 65 to false), events)
    }
    @Test fun asynchronousComboPreservesHold() = runBlocking {
        val socket = Any()
        val events = mutableListOf<Pair<Int, Boolean>>()
        val actions = DesktopKeyActions(this, { socket }) { _, key, down -> events.add(key to down) }
        actions.toggle(17)
        actions.combo(17, 67)
        delay(200)
        assertEquals(setOf(17), actions.held.value)
        assertEquals(listOf(17 to true, 67 to true, 67 to false), events)
        actions.clear()
    }
    @Test fun staleDelayedReleasesDoNotReachReplacementSocket() = runBlocking {
        var socket: Any? = Any()
        val events = mutableListOf<Pair<Int, Boolean>>()
        val actions = DesktopKeyActions(this, { socket }) { origin, key, down ->
            if (origin === socket) events.add(key to down)
        }
        actions.combo(17, 65)
        delay(30)
        socket = Any()
        events.clear()
        actions.toggle(91)
        delay(200)
        assertEquals(listOf(91 to true), events)
        assertEquals(setOf(91), actions.held.value)
        actions.clear()
    }
    @Test fun disconnectClearsUiHoldState() = runBlocking {
        var socket: Any? = Any()
        val actions = DesktopKeyActions(this, { socket }) { _, _, _ -> }
        actions.toggle(18)
        socket = null
        actions.releaseHolds()
        assertTrue(actions.held.value.isEmpty())
        actions.clear()
    }
}

class DesktopShortcutsTest {
    @Test fun orderedListRoundTripsIncludingModifierOnlyShortcut() {
        val list = listOf(DesktopShortcut(listOf(91)), DesktopShortcut(listOf(17, 16, 65)))
        assertEquals(list, DesktopShortcuts.decode(DesktopShortcuts.encode(list)))
    }
    @Test fun emptySavedListIsNotRestoredUnintentionally() {
        assertTrue(DesktopShortcuts.decode("").isEmpty())
        assertEquals(DesktopShortcuts.defaults, DesktopShortcuts.decode(null))
    }
    @Test fun corruptDataFallsBackToDefaults() {
        listOf("0", "256", "17,17", "garbage", "17;").forEach {
            assertEquals(DesktopShortcuts.defaults, DesktopShortcuts.decode(it))
        }
    }
    @Test fun defaultPresetsMatchRequestedReference() {
        assertEquals(listOf(17,18,46), DesktopShortcuts.defaults.first().keys)
        assertEquals(listOf(16,91,77), DesktopShortcuts.defaults.last().keys)
        assertTrue(DesktopShortcuts.selectableKeys.containsAll(listOf(65,90,48,57,112,135,38,40)))
    }
}
