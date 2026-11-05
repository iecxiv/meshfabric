package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopRightClickPolicyTest {
    @Test fun nativeDownAndButtonPressSendOnlyOneClick() {
        val policy = DesktopRightClickPolicy()
        assertTrue(policy.nativeDown(1, 100))
        assertFalse(policy.nativeDown(1, 100))
        policy.nativeUp(1, 130)
        assertFalse(policy.nativeActive(1))
    }
    @Test fun mouseBackFollowingNativeClickIsConsumedWithoutAnotherClick() {
        val policy = DesktopRightClickPolicy()
        policy.nativeDown(1, 100)
        policy.nativeUp(1, 140)
        assertFalse(policy.backDown(1, 100))
        assertTrue(policy.backUp(1, 100, 140))
    }
    @Test fun legacyBackOnlyStrokeSendsOnceAndBalancesUp() {
        val policy = DesktopRightClickPolicy()
        assertTrue(policy.backDown(1, 100))
        assertFalse(policy.backDown(1, 100))
        assertTrue(policy.backUp(1, 100, 140))
        assertFalse(policy.backUp(1, 200, 210))
    }
    @Test fun legacyFirstThenNativeRepresentationDoesNotDuplicate() {
        val policy = DesktopRightClickPolicy()
        assertTrue(policy.backDown(1, 100))
        assertFalse(policy.nativeDown(1, 100))
        policy.nativeUp(1, 130)
        assertTrue(policy.backUp(1, 100, 130))
    }
    @Test fun successiveFastClicksAreNotDebounced() {
        val policy = DesktopRightClickPolicy()
        assertTrue(policy.nativeDown(1, 100))
        policy.nativeUp(1, 101)
        assertTrue(policy.nativeDown(1, 102))
    }
    @Test fun devicesAndTimestampsDoNotShareOwnership() {
        val policy = DesktopRightClickPolicy()
        policy.nativeDown(1, 100)
        policy.nativeUp(1, 130)
        assertFalse(policy.ownsNative(2, 100))
        assertFalse(policy.ownsNative(1, 131))
        assertTrue(policy.backDown(2, 100))
    }
    @Test fun resetDropsOldCapture() {
        val policy = DesktopRightClickPolicy()
        policy.nativeDown(1, 100)
        policy.backDown(1, 100)
        policy.reset()
        assertFalse(policy.nativeActive(1))
        assertFalse(policy.ownsNative(1, 100))
        assertFalse(policy.backUp(1, 100, 130))
    }
    @Test fun keyboardBackAndKnownSideButtonsAreNotLegacyRightClicks() {
        assertFalse(DesktopRightClickPolicy.acceptsLegacyBack(false, 0))
        for (scan in 275..279) assertFalse(DesktopRightClickPolicy.acceptsLegacyBack(true, scan))
        assertTrue(DesktopRightClickPolicy.acceptsLegacyBack(true, 273))
        assertTrue(DesktopRightClickPolicy.acceptsLegacyBack(true, 0))
    }
}
