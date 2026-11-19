package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.DevicePlatform
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchUploadPolicyTest {
    private val node = MeshNode(id = "node//device", meshid = "mesh//group", conn = 1)
    private val meta = GeneralMeta("Device", emptyList(), 8L, "Linux", node.meshid)
    private val inventory = listOf(MeshGroup(id = node.meshid, mtype = 2) to listOf(node))
    private val file = DeviceBatchUploadFile("report.txt", 12L, "a".repeat(64))
    private val listing = DeviceBatchUploadListing(node.id, "/tmp", "read", emptyList(), true)
    private fun draft(info: GeneralMeta = meta, path: String = "/tmp", local: DeviceBatchUploadFile = file,
        received: DeviceBatchUploadListing? = listing, expected: String = "read") =
        deviceBatchUploadDraft("user//operator", node.id, info, inventory, path, local, received, expected)

    @Test fun bindsExplicitTargetAndLocalFingerprint() {
        val result = requireNotNull(draft())
        assertEquals("/tmp/report.txt", result.destination)
        assertEquals(file, result.file)
        assertEquals(node.id, result.nodeId)
        assertEquals(meta.mesh, result.mesh)
    }
    @Test fun acceptsPortableUnicodeNames() { assertTrue(deviceBatchUploadName("informe-á.txt")) }
    @Test fun rejectsTraversalAndNonPortableNames() {
        for (name in listOf("", ".", "..", "../x", "a/b", "a\\b", "x:stream", "x\n", "x.", "x ", "CON", "nul.txt", "LPT1.log")) assertFalse(deviceBatchUploadName(name))
    }
    @Test fun nameLimitUsesUtf8Bytes() {
        assertTrue(deviceBatchUploadName("a".repeat(255)))
        assertFalse(deviceBatchUploadName("a".repeat(256)))
        assertFalse(deviceBatchUploadName("á".repeat(128)))
    }
    @Test fun normalizesAbsoluteWindowsDrivePaths() {
        assertEquals("C:/Temp", deviceBatchUploadDirectory("c:\\Temp\\", DevicePlatform.WINDOWS))
        assertEquals("C:/", deviceBatchUploadDirectory("C:/", DevicePlatform.WINDOWS))
    }
    @Test fun rejectsRelativeUncDeviceAndTraversalPaths() {
        for (path in listOf("C:Temp", "Temp", "\\\\host\\share", "\\\\?\\C:\\Temp", "C:/a/../b", "C:/a//b")) assertNull(deviceBatchUploadDirectory(path, DevicePlatform.WINDOWS))
        for (path in listOf("tmp", "~/tmp", "/a/../b", "/a/./b", "//host/share", "/a\\b")) assertNull(deviceBatchUploadDirectory(path, DevicePlatform.LINUX))
    }
    @Test fun unknownAndInitiallyUnsupportedPlatformsAreExcluded() {
        for (platform in listOf(DevicePlatform.UNKNOWN, DevicePlatform.ANDROID, DevicePlatform.CHROMEOS)) assertNull(deviceBatchUploadDirectory("/tmp", platform))
        assertNull(draft(info = meta.copy(os = "Unknown")))
    }
    @Test fun filePermissionsAreNotInferredFromConnectivity() {
        assertFalse(deviceBatchUploadAvailable(node.id, meta.copy(rights = 0L), inventory))
        assertFalse(deviceBatchUploadAvailable(node.id, meta.copy(rights = 8L or 1024L), inventory))
        assertTrue(deviceBatchUploadAvailable(node.id, meta.copy(rights = -1L), inventory))
    }
    @Test fun offlineAndAmtOnlyConnectionsAreExcluded() {
        for (conn in listOf(0, 2, 4)) {
            val rows = listOf(MeshGroup(id = node.meshid, mtype = 2) to listOf(node.copy(conn = conn)))
            assertFalse(deviceBatchUploadAvailable(node.id, meta, rows))
        }
    }
    @Test fun duplicateDevicesAreExcluded() {
        assertFalse(deviceBatchUploadAvailable(node.id, meta, listOf(MeshGroup(id = node.meshid, mtype = 2) to listOf(node, node))))
    }
    @Test fun missingIncompleteOrUnrelatedListingsCannotApproveUpload() {
        assertNull(draft(received = null))
        assertNull(draft(received = listing.copy(complete = false)))
        assertNull(draft(received = listing.copy(nodeId = "node//other")))
        assertNull(draft(received = listing.copy(directory = "/other")))
        assertNull(draft(expected = "stale"))
        assertNull(draft(expected = ""))
    }
    @Test fun detectedNameCollisionIsSkipped() {
        assertNull(draft(received = listing.copy(names = listOf(file.name))))
    }
    @Test fun windowsCollisionComparisonIgnoresCase() {
        assertNull(draft(info = meta.copy(os = "Windows"), path = "C:/Temp",
            received = listing.copy(directory = "C:/Temp", names = listOf("REPORT.TXT"))))
    }
    @Test fun unixCollisionComparisonPreservesCase() {
        assertNotNull(draft(received = listing.copy(names = listOf("REPORT.TXT"))))
    }
    @Test fun boundsLocalSizeAndRequiresADeclaredFingerprint() {
        assertNull(draft(local = file.copy(size = -1L)))
        assertNull(draft(local = file.copy(size = DEVICE_BATCH_UPLOAD_MAX_BYTES + 1L)))
        assertNull(draft(local = file.copy(sha256 = "invalid")))
        assertNotNull(draft(local = file.copy(size = 0L)))
    }
    @Test fun changedDestinationOrLocalContentChangesTheDraft() {
        val reviewed = requireNotNull(draft())
        assertNotEquals(reviewed, draft(path = "/var/tmp", received = listing.copy(directory = "/var/tmp")))
        assertNotEquals(reviewed, draft(local = file.copy(sha256 = "b".repeat(64))))
    }
}
