package iecxiv.meshfabric.ui.files
import org.junit.Assert.*
import org.junit.Test
class AndroidFileMenuTest {
    @Test fun readOnlyLocations() {
        listOf("", "/", "Audio", "Audio/a", "Images", "Videos", "Sdcard", "/sdcard/", "/storage/emulated/0", "Sdcard/../Audio", "sdcard-other/a").forEach {
            assertFalse(it, androidFileUploadAllowed(it))
        }
    }
    @Test fun uploadInsideSdcard() {
        listOf("Sdcard/Download", "/sdcard/Documents/", "/storage/emulated/0/Download", "/mnt/sdcard/a").forEach {
            assertTrue(it, androidFileUploadAllowed(it))
        }
    }
}
