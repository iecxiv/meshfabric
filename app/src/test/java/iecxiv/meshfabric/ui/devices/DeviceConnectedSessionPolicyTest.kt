package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.SessionStatus
import org.junit.Assert.*
import org.junit.Test

class DeviceConnectedSessionPolicyTest {
    @Test fun onlyConnectedSessionsExposeDisconnectAction() {
        val states = SessionStatus.values()
        for (desktop in states) for (terminal in states) for (files in states) {
            val expected = listOf(desktop, terminal, files).any { it == SessionStatus.CONNECTED }
            assertEquals(expected, hasDeviceConnectedSession(desktop, terminal, files))
        }
    }
}
