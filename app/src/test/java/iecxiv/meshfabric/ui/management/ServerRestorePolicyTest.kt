package iecxiv.meshfabric.ui.management

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ServerRestorePolicyTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun restorePermissionIsSeparateFromBackup() {
        assertTrue(restoreAllowed(4))
        assertTrue(restoreAllowed(-1))
        assertTrue(restoreAllowed(4294967295))
        assertFalse(restoreAllowed(1))
        assertFalse(restoreAllowed(16))
    }
    @Test fun dangerousArchiveNamesAreRejected() {
        assertTrue(safeRestorePath("meshcentral-data/config.json"))
        listOf("../config.json","a/../b","/etc/passwd","C:\\Windows\\file","\\\\host\\file","a\\..\\b").forEach { assertFalse(it,safeRestorePath(it)) }
    }
    @Test fun endpointPreservesDomainAndLoginKey() {
        val endpoint=restoreEndpoint("https://example.test/domain/?key=value","restoreserver.ashx")
        assertEquals("/domain/restoreserver.ashx",endpoint.encodedPath)
        assertEquals("value",endpoint.queryParameter("key"))
    }
    @Test fun validNedbArchiveIsReadWithoutExtraction() {
        val file=temporary.newFile("backup.zip")
        ZipOutputStream(file.outputStream()).use { zip->
            listOf("meshcentral-data/config.json","meshcentral-data/meshcentral.db").forEach { name->zip.putNextEntry(ZipEntry(name)); zip.write("{}".toByteArray()); zip.closeEntry() }
        }
        assertTrue(verifyRestoreArchive(file,"",false))
        assertFalse(java.io.File(temporary.root,"meshcentral-data").exists())
    }
    @Test(expected=IllegalArgumentException::class) fun otherZipFilesAreNotAcceptedAsServerBackups() {
        val file=temporary.newFile("unrelated.zip")
        ZipOutputStream(file.outputStream()).use { zip->zip.putNextEntry(ZipEntry("notes.txt")); zip.write("notes".toByteArray()); zip.closeEntry() }
        verifyRestoreArchive(file,"",false)
    }
}
