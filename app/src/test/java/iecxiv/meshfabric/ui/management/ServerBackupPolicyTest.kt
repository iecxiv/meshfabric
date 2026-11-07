package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ServerBackupPolicyTest {
    @Test fun backupRightRequiresBackupBitOrFullAdministrator() {
        assertTrue(backupAllowed(1L))
        assertTrue(backupAllowed(-1L))
        assertTrue(backupAllowed(4294967295L))
        assertFalse(backupAllowed(0L))
        assertFalse(backupAllowed(4L))
        assertFalse(backupAllowed(16L))
    }
    @Test fun zipHeaderIsCheckedBeforeSaving() {
        val local=byteArrayOf(0x50,0x4B,3,4)
        assertTrue(validBackupHeader(local,4,100L))
        assertTrue(validBackupHeader(byteArrayOf(0x50,0x4B,5,6),4,22L))
        assertFalse(validBackupHeader("<htm".toByteArray(),4,100L))
        assertFalse(validBackupHeader(local,2,100L))
        assertFalse(validBackupHeader(local,4,10L))
    }
    @Test fun suggestedFileNameIsStable() {
        assertEquals("meshfabric-server-backup-20261106-2245.zip",backupFileName(LocalDateTime.of(2026,11,6,22,45)))
    }
}
