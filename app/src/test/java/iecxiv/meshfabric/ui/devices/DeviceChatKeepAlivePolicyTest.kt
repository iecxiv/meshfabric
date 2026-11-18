package iecxiv.meshfabric.ui.devices

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceChatKeepAlivePolicyTest {
    @Test fun openChatPhasesKeepTheServiceEligible() {
        for (phase in listOf("loading", "ready", "checking", "connecting", "waiting", "connected", "checking_send")) {
            assertTrue(phase, deviceChatNeedsKeepAlive(phase))
        }
    }
    @Test fun closedChatsDoNotKeepTheServiceAlive() {
        assertFalse(deviceChatNeedsKeepAlive("closed"))
    }
    @Test fun unknownPhasesAreNotAssumedActive() {
        assertFalse(deviceChatNeedsKeepAlive(""))
        assertFalse(deviceChatNeedsKeepAlive("unknown"))
    }
}
