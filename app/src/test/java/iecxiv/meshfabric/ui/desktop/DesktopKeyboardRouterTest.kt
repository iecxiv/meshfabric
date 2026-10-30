package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopKeyboardRouterTest {
    private val events = mutableListOf<Pair<Int, Boolean>>()
    private fun router() = DesktopKeyboardRouter { key, down -> events.add(key to down) }.apply { setActive(true) }
    @Test fun arrowsAreKeyEventsNotTextEdits() {
        val router = router()
        for ((code, vk) in mapOf(19 to 38,20 to 40,21 to 37,22 to 39)) {
            assertTrue(router.handle(0,code)); assertTrue(router.handle(1,code))
            assertEquals(listOf(vk to true,vk to false),events); events.clear()
        }
    }
    @Test fun navigationAndFunctionKeysMapped() {
        for ((code,vk) in mapOf(61 to 9,66 to 13,67 to 8,111 to 27,112 to 46,122 to 36,123 to 35,124 to 45,92 to 33,93 to 34))
            assertEquals(vk,DesktopAndroidKeyMap.virtualKey(code))
        for (n in 0..11) assertEquals(112+n,DesktopAndroidKeyMap.virtualKey(131+n))
    }
    @Test fun lettersDigitsAndNumpadMapped() {
        for (n in 0..25) assertEquals(65+n,DesktopAndroidKeyMap.virtualKey(29+n))
        for (n in 0..9) {
            assertEquals(48+n,DesktopAndroidKeyMap.virtualKey(7+n))
            assertEquals(96+n,DesktopAndroidKeyMap.virtualKey(144+n))
        }
    }
    @Test fun physicalCtrlChordBalancedOnce() {
        val router=router();router.handle(0,113);router.handle(0,29,meta=0x1000)
        router.handle(1,29);router.handle(1,113)
        assertEquals(listOf(17 to true,65 to true,65 to false,17 to false),events)
    }
    @Test fun imeChordWithoutModifierEventsBalanced() {
        val router=router();router.handle(0,29,meta=0x1000);router.handle(1,29)
        assertEquals(listOf(17 to true,65 to true,65 to false,17 to false),events)
    }
    @Test fun repeatsDoNotCreateExtraReleaseDebt() {
        val router=router();router.handle(0,19);router.handle(0,19,repeat=1);router.handle(1,19)
        assertEquals(listOf(38 to true,38 to true,38 to false),events)
        router.releaseAll();assertEquals(3,events.size)
    }
    @Test fun modifiersDoNotRepeat() {
        val router=router();router.handle(0,113);router.handle(0,113,repeat=1);router.handle(1,113)
        assertEquals(listOf(17 to true,17 to false),events)
    }
    @Test fun shiftSidesShareAReleaseReference() {
        val router=router();router.handle(0,59);router.handle(0,60);router.handle(1,59)
        assertEquals(listOf(16 to true),events);router.handle(1,60)
        assertEquals(listOf(16 to true,16 to false),events)
    }
    @Test fun lostFocusReleasesInReverseOrderAndNeverReplays() {
        val router=router();router.handle(0,113);router.handle(0,29,meta=0x1000);router.setActive(false)
        assertEquals(listOf(17 to true,65 to true,65 to false,17 to false),events)
        router.setActive(true);assertFalse(router.handle(1,29));assertEquals(4,events.size)
    }
    @Test fun reservedAndUnknownKeysPassThrough() {
        val router=router()
        for (code in listOf(3,4,24,25,26,999)) assertFalse(router.handle(0,code))
        assertFalse(router.handle(2,19));assertTrue(events.isEmpty())
    }
    @Test fun inactiveDoesNotSend() {
        val router=router();router.setActive(false);assertFalse(router.handle(0,19));assertTrue(events.isEmpty())
    }
    @Test fun releasingPhysicalShiftDoesNotRetainItForAnArrow() {
        val router=router();router.handle(0,59);router.handle(0,19,meta=1);router.handle(1,59)
        assertEquals(listOf(16 to true,38 to true,16 to false),events)
        router.handle(1,19);assertEquals(38 to false,events.last())
    }
}
