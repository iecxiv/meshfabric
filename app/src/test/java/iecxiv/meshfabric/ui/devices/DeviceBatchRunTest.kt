package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.ui.management.CommandPhase
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchRunTest {
    private val meta = GeneralMeta("Device", emptyList(), 131072L, "Linux", "mesh//one")
    private fun inventory(conn: Int = 1) = listOf(MeshGroup(id = meta.mesh, mtype = 2) to listOf(MeshNode(id = "node//one", meshid = meta.mesh, conn = conn)))

    @Test fun acceptsUnicodeMultilineScripts() { assertTrue(generalRunTextValid("echo á\necho b")) }
    @Test fun rejectsBlankScripts() { assertFalse(generalRunTextValid(" \n")) }
    @Test fun rejectsOversizedOrNullContainingScripts() {
        assertFalse(generalRunTextValid("a".repeat(64001)))
        assertFalse(generalRunTextValid("a\u0000b"))
    }
    @Test fun windowsAllowsCmdAndPowerShellButNotUnixShell() {
        assertTrue(deviceBatchRunInterpreterAllowed("Windows", "cmd"))
        assertTrue(deviceBatchRunInterpreterAllowed("Windows", "powershell"))
        assertFalse(deviceBatchRunInterpreterAllowed("Windows", "shell"))
    }
    @Test fun unixUsesTheExistingShellPolicy() {
        assertTrue(deviceBatchRunInterpreterAllowed("Debian Linux", "shell"))
        assertFalse(deviceBatchRunInterpreterAllowed("Debian Linux", "cmd"))
    }
    @Test fun unknownSystemsStillRequireAnExplicitInterpreter() {
        assertFalse(deviceBatchRunInterpreterAllowed(null, ""))
        assertTrue(deviceBatchRunInterpreterAllowed(null, "shell"))
    }
    @Test fun invalidInterpreterIsNotReplacedAutomatically() { assertFalse(deviceBatchRunInterpreterAllowed("Linux", "invalid")) }
    @Test fun reviewedContextCanMatch() {
        assertTrue(generalRunContextMatches(GeneralRunDraft("echo a", "shell", meta.os, meta.mesh), meta))
    }
    @Test fun movingGroupInvalidatesABatchDraft() {
        assertFalse(generalRunContextMatches(GeneralRunDraft("echo a", "shell", meta.os, meta.mesh), meta.copy(mesh = "mesh//other")))
    }
    @Test fun changingOperatingSystemInvalidatesTheDraft() {
        assertFalse(generalRunContextMatches(GeneralRunDraft("echo a", "shell", meta.os, meta.mesh), meta.copy(os = "Windows")))
    }
    @Test fun legacyIndividualDraftRemainsCompatible() {
        assertTrue(generalRunContextMatches(GeneralRunDraft("echo a", "shell", meta.os), meta))
    }
    @Test fun executionRequiresPermissionAndAnOnlineAgent() {
        assertTrue(generalRunAvailable("node//one", meta, inventory()))
        assertFalse(generalRunAvailable("node//one", meta.copy(rights = 0L), inventory()))
        for (conn in listOf(0, 2, 4)) assertFalse(generalRunAvailable("node//one", meta, inventory(conn)))
    }
    @Test fun acceptanceAndOutputDoNotBecomeSuccessfulCompletion() {
        assertNull(deviceBatchRunCompletion("sent", "", CommandPhase.ACCEPTED))
        assertEquals(DeviceBatchRunPhase.OUTPUT, deviceBatchRunCompletion("sent", "", CommandPhase.OUTPUT, "text")?.phase)
        assertEquals(DeviceBatchRunPhase.UNCONFIRMED, deviceBatchRunCompletion("error", "unconfirmed", null)?.phase)
        assertEquals(DeviceBatchRunPhase.NOT_SENT, deviceBatchRunCompletion("error", "changed", null)?.phase)
        assertEquals(DeviceBatchRunPhase.REJECTED, deviceBatchRunCompletion("sent", "", CommandPhase.ERROR)?.phase)
    }
    @Test fun capturedOutputRespectsPerDeviceAndGlobalBudgets() {
        val value = "a".repeat(20000)
        assertEquals(16000, deviceBatchRunOutput(value, Int.MAX_VALUE).length)
        assertEquals(10, deviceBatchRunOutput(value, 10).length)
        assertEquals("", deviceBatchRunOutput(value, 0))
        assertEquals("", deviceBatchRunOutput(value, -1))
    }
}
