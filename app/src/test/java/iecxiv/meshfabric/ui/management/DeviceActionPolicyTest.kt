package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test

class DeviceActionPolicyTest {
    @Test fun linuxOnlyOffersShell() {
        assertEquals(listOf("shell"),commandInterpreters("Debian GNU/Linux 12").map { it.key })
        assertEquals(3,commandInterpreters("Ubuntu Linux").single().type)
        assertEquals("shell",defaultInterpreterKey("Linux"))
    }
    @Test fun proxmoxIsUnix() { assertEquals(DevicePlatform.UNIX,devicePlatform("Proxmox VE 8")) }
    @Test fun windowsOffersOnlyCmdAndPowerShell() {
        assertEquals(listOf("cmd","powershell"),commandInterpreters("Microsoft Windows 11").map { it.key })
        assertEquals("cmd",defaultInterpreterKey("Windows Server"))
    }
    @Test fun unknownRequiresAnExplicitChoice() {
        assertEquals(DevicePlatform.UNKNOWN,devicePlatform("Sistema desconocido"))
        assertEquals(3,commandInterpreters("Sistema desconocido").size)
        assertEquals("",defaultInterpreterKey("Sistema desconocido"))
    }
    @Test fun missingOsIsNotAssumedToBeLinux() { assertEquals(DevicePlatform.UNKNOWN,devicePlatform(null)); assertEquals("",defaultInterpreterKey(null)) }
    @Test fun offlineHidesAgentActionsButKeepsServerActions() { assertFalse(actionVisible(true,false)); assertTrue(actionVisible(false,false)) }
    @Test fun onlineKeepsBothKindsOfActions() { assertTrue(actionVisible(true,true)); assertTrue(actionVisible(false,true)) }
    @Test fun fieldlessActionsHaveOnlyTheConfirmation() { assertFalse(needsReviewStep(0)); assertTrue(needsReviewStep(2)) }
}
