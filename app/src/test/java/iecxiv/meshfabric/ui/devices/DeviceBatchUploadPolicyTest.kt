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
    private val options = DeviceBatchUploadOptions(windowsDirectory = "C:/Temp", linuxDirectory = "/tmp")
    private val listing = DeviceBatchUploadListing(node.id, "/tmp", "read", emptyList(), true)
    private fun draft(info: GeneralMeta = meta, settings: DeviceBatchUploadOptions = options, local: DeviceBatchUploadFile = file,
        received: DeviceBatchUploadListing? = listing, expected: String = "read") =
        deviceBatchUploadDraft("user//operator", node.id, info, inventory, settings, local, received, expected)

    @Test fun bindsTargetFingerprintAndAllReviewedOptions() {
        val result = requireNotNull(draft())
        assertEquals("/tmp/report.txt", result.destination)
        assertEquals(file, result.file)
        assertEquals(options, result.options)
        assertEquals(node.id, result.nodeId)
        assertFalse(result.createDirectory)
        assertFalse(result.overwriteExistingFile)
    }
    @Test fun routesWindowsAndLinuxIndependently() {
        assertEquals("C:/Temp", deviceBatchUploadRoute(options, DevicePlatform.WINDOWS))
        assertEquals("/tmp", deviceBatchUploadRoute(options, DevicePlatform.LINUX))
        assertEquals("C:/Temp/report.txt", requireNotNull(draft(info = meta.copy(os = "Windows"), received = listing.copy(directory = "C:/Temp"))).destination)
    }
    @Test fun emptyRouteDoesNotFallBackToOtherSystem() {
        assertNull(deviceBatchUploadRoute(options.copy(windowsDirectory = ""), DevicePlatform.WINDOWS))
        assertNull(deviceBatchUploadRoute(options.copy(linuxDirectory = ""), DevicePlatform.LINUX))
        assertNotNull(draft(settings = options.copy(windowsDirectory = "")))
    }
    @Test fun unknownAndOtherPlatformsAreNotSilentlyRoutedAsLinux() {
        for (platform in listOf(DevicePlatform.UNKNOWN, DevicePlatform.ANDROID, DevicePlatform.CHROMEOS, DevicePlatform.MACOS, DevicePlatform.FREEBSD)) assertNull(deviceBatchUploadRoute(options, platform))
        assertNull(draft(info = meta.copy(os = "Unknown")))
    }
    @Test fun optionsDefaultToNoCreationAndNoOverwrite() {
        assertFalse(DeviceBatchUploadOptions().createDirectoryIfMissing)
        assertFalse(DeviceBatchUploadOptions().overwriteFileIfExists)
    }
    @Test fun missingDirectoryRequiresExplicitCreation() {
        val absent = listing.copy(directoryState = DeviceBatchUploadDirectoryState.MISSING)
        assertNull(draft(received = absent))
        assertTrue(requireNotNull(draft(settings = options.copy(createDirectoryIfMissing = true), received = absent)).createDirectory)
    }
    @Test fun unknownDirectoryIsNotTreatedAsMissing() {
        assertNull(draft(settings = options.copy(createDirectoryIfMissing = true), received = listing.copy(directoryState = DeviceBatchUploadDirectoryState.UNKNOWN)))
    }
    @Test fun inconsistentMissingDirectorySnapshotIsRejected() {
        assertNull(draft(settings = options.copy(createDirectoryIfMissing = true), received = listing.copy(directoryState = DeviceBatchUploadDirectoryState.MISSING, names = listOf("x"))))
    }
    @Test fun detectedCollisionRequiresOverwrite() {
        val existing = listing.copy(names = listOf(file.name), regularFiles = setOf(file.name))
        assertNull(draft(received = existing))
        assertTrue(requireNotNull(draft(settings = options.copy(overwriteFileIfExists = true), received = existing)).overwriteExistingFile)
    }
    @Test fun overwriteCannotReplaceDirectoryOrUnknownEntry() {
        val settings = options.copy(overwriteFileIfExists = true)
        assertNull(draft(settings = settings, received = listing.copy(names = listOf(file.name), directories = setOf(file.name))))
        assertNull(draft(settings = settings, received = listing.copy(names = listOf(file.name))))
    }
    @Test fun contradictoryEntryTypesAreRejected() {
        assertNull(draft(settings = options.copy(overwriteFileIfExists = true), received = listing.copy(names = listOf(file.name), directories = setOf(file.name), regularFiles = setOf(file.name))))
    }
    @Test fun windowsCollisionComparisonIgnoresCase() {
        val existing = listing.copy(directory = "C:/Temp", names = listOf("REPORT.TXT"), regularFiles = setOf("REPORT.TXT"))
        assertNull(draft(info = meta.copy(os = "Windows"), received = existing))
        assertNotNull(draft(info = meta.copy(os = "Windows"), settings = options.copy(overwriteFileIfExists = true), received = existing))
    }
    @Test fun ambiguousWindowsNamesAreRejected() {
        assertNull(draft(info = meta.copy(os = "Windows"), settings = options.copy(overwriteFileIfExists = true), received = listing.copy(directory = "C:/Temp", names = listOf("REPORT.TXT", "report.txt"))))
    }
    @Test fun unixCollisionComparisonPreservesCase() {
        assertNotNull(draft(received = listing.copy(names = listOf("REPORT.TXT"))))
    }
    @Test fun creationAndOverwriteAreIndependent() {
        val existing = listing.copy(names = listOf(file.name), regularFiles = setOf(file.name))
        assertNull(draft(settings = options.copy(createDirectoryIfMissing = true), received = existing))
        assertNull(draft(settings = options.copy(overwriteFileIfExists = true), received = listing.copy(directoryState = DeviceBatchUploadDirectoryState.MISSING)))
    }
    @Test fun removesFormer32MiBCapWithoutUsingIntSizes() {
        assertNotNull(draft(local = file.copy(size = 33L * 1024L * 1024L)))
        assertNotNull(draft(local = file.copy(size = 5L * 1024L * 1024L * 1024L)))
        assertNull(draft(local = file.copy(size = -1L)))
        assertNull(draft(local = file.copy(sha256 = "invalid")))
    }
    @Test fun changingAnyReviewedOptionChangesDraft() {
        val before = requireNotNull(draft())
        assertNotEquals(before, draft(settings = options.copy(createDirectoryIfMissing = true)))
        assertNotEquals(before, draft(settings = options.copy(overwriteFileIfExists = true)))
        assertNotEquals(before, draft(settings = options.copy(windowsDirectory = "D:/Temp")))
    }
    @Test fun missingIncompleteOrUnrelatedListingIsRejected() {
        assertNull(draft(received = null))
        assertNull(draft(received = listing.copy(complete = false)))
        assertNull(draft(received = listing.copy(nodeId = "node//other")))
        assertNull(draft(received = listing.copy(directory = "/other")))
        assertNull(draft(expected = "stale"))
    }
    @Test fun portableNamesAndByteLimitsRemainValidated() {
        assertTrue(deviceBatchUploadName("informe-á.txt"))
        for (name in listOf("", "..", "../x", "a\\b", "x:stream", "x\n", "x.", "x ", "CON", "nul.txt", "LPT1.log", "á".repeat(128))) assertFalse(deviceBatchUploadName(name))
    }
    @Test fun absolutePathsAreRequired() {
        assertEquals("C:/Temp", deviceBatchUploadDirectory("c:\\Temp\\", DevicePlatform.WINDOWS))
        for (path in listOf("C:Temp", "Temp", "\\\\host\\share", "C:/a/../b")) assertNull(deviceBatchUploadDirectory(path, DevicePlatform.WINDOWS))
        for (path in listOf("tmp", "~/tmp", "/a/../b", "//host/share")) assertNull(deviceBatchUploadDirectory(path, DevicePlatform.LINUX))
    }
    @Test fun permissionsAndAgentConnectivityRemainRequired() {
        assertFalse(deviceBatchUploadAvailable(node.id, meta.copy(rights = 0L), inventory))
        assertFalse(deviceBatchUploadAvailable(node.id, meta.copy(rights = 8L or 1024L), inventory))
        assertTrue(deviceBatchUploadAvailable(node.id, meta.copy(rights = -1L), inventory))
        for (conn in listOf(0, 2, 4)) assertFalse(deviceBatchUploadAvailable(node.id, meta, listOf(MeshGroup(id = node.meshid, mtype = 2) to listOf(node.copy(conn = conn)))))
    }
}
