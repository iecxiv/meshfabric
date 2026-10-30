package iecxiv.meshfabric.data.remote

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class WindowsMultiDriveListingTest {
    @Test fun capturedRootReplyWithoutActionPreservesAllFourDrives() {
        val json = JsonParser.parseString("""{"path":"","reqid":1,"dir":[{"n":"C:\\","t":1},{"n":"D:\\","t":1},{"n":"F:\\","t":1},{"n":"X:\\","t":1}]}""").asJsonObject
        assertFalse(json.has("action"))
        val drives = WindowsDriveSupport.rootListing(json, true)!!
        assertEquals(listOf("C:/", "D:/", "F:/", "X:/"), drives.map { it.path })
        assertTrue(drives.all { it.item().isDir })
        assertEquals("", WindowsDriveSupport.wire("/", true))
        for (drive in drives) {
            assertEquals("/", WindowsDriveSupport.parent(drive.path, true))
            assertEquals(drive.path, WindowsDriveSupport.wire(drive.path, true))
        }
        assertNull(WindowsDriveSupport.rootListing(json, false))
    }
}
