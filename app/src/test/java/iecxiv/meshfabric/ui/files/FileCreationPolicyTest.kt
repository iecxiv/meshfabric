package iecxiv.meshfabric.ui.files
import org.junit.Assert.*
import org.junit.Test
class FileCreationPolicyTest {
    @Test fun windowsDriveListCannotCreate() {
        listOf("", "/").forEach { assertFalse(fileCreationAllowed(false, true, it)) }
    }
    @Test fun windowsDirectoriesCanCreate() {
        listOf("C:/", "C:/Documents", "D:/", "C:\\").forEach {
            assertTrue(fileCreationAllowed(false, true, it))
        }
    }
    @Test fun androidNeverCreates() {
        listOf("/", "Audio", "Images", "Videos", "Sdcard", "Sdcard/Download").forEach {
            assertFalse(fileCreationAllowed(true, false, it))
        }
    }
    @Test fun otherSystemsUnchanged() {
        assertTrue(fileCreationAllowed(false, false, "/"))
    }
}
