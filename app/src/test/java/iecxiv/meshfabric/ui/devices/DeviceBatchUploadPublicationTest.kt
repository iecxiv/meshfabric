package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.DevicePlatform
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchUploadPublicationTest {
    private val file = DeviceBatchUploadFile("a.txt", 3L, "a".repeat(64))
    private val options = DeviceBatchUploadOptions(linuxDirectory = "/tmp")
    private val draft = DeviceBatchUploadDraft("user//operator", "node//device", "mesh//group", "Linux",
        DevicePlatform.LINUX, "/tmp", file, options, false, false)
    private fun snapshot(names: List<String>, files: Set<String> = names.toSet(), size: Long = 3L) =
        BatchUploadSnapshot(DeviceBatchUploadListing(draft.nodeId, draft.directory, "read", names, true,
            regularFiles = files), files.associateWith { size })
    @Test fun missingDestinationCanBePublished() { assertTrue(batchUploadPublishable(draft, snapshot(emptyList()))) }
    @Test fun existingFileRequiresExplicitOverwrite() {
        assertFalse(batchUploadPublishable(draft, snapshot(listOf("a.txt"))))
        assertTrue(batchUploadPublishable(draft.copy(options = options.copy(overwriteFileIfExists = true)), snapshot(listOf("a.txt"))))
    }
    @Test fun overwriteDoesNotAllowDirectoryOrUnknownType() {
        assertFalse(batchUploadPublishable(draft.copy(options = options.copy(overwriteFileIfExists = true)), snapshot(listOf("a.txt"), emptySet())))
    }
    @Test fun incompleteOrWrongNodeListingCannotAuthorizePublication() {
        val s = snapshot(emptyList())
        assertFalse(batchUploadPublishable(draft, s.copy(listing = s.listing.copy(complete = false))))
        assertFalse(batchUploadPublishable(draft, s.copy(listing = s.listing.copy(nodeId = "node//other"))))
    }
    @Test fun observationRequiresFinalFileAndNoStagingName() {
        assertTrue(batchUploadObserved(snapshot(listOf("a.txt")), draft, "stage"))
        assertFalse(batchUploadObserved(snapshot(listOf("a.txt", "stage")), draft, "stage"))
        assertFalse(batchUploadObserved(snapshot(listOf("a.txt"), size = 2L), draft, "stage"))
        assertFalse(batchUploadObserved(snapshot(listOf("a.txt"), files = emptySet()), draft, "stage"))
    }
    @Test fun windowsNamesAreCaseInsensitive() {
        val windows = draft.copy(platform = DevicePlatform.WINDOWS)
        assertFalse(batchUploadPublishable(windows, snapshot(listOf("A.TXT"))))
        assertTrue(batchUploadObserved(snapshot(listOf("A.TXT")), windows, "stage"))
    }
}
