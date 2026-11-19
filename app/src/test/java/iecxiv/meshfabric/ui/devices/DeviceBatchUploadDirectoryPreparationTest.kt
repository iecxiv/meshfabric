package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.DevicePlatform
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchUploadDirectoryPreparationTest {
    private val node = MeshNode(id = "node//device", meshid = "mesh//group", conn = 1)
    private val meta = GeneralMeta("Device", emptyList(), 8L, "Linux", node.meshid)
    private val inventory = listOf(MeshGroup(id = node.meshid, mtype = 2) to listOf(node))
    private val options = DeviceBatchUploadOptions(linuxDirectory = "/tmp/new", createDirectoryIfMissing = true)
    private val file = DeviceBatchUploadFile("a.txt", 1L, "a".repeat(64))
    private val missing = DeviceBatchUploadListing(node.id, "/tmp/new", "read", emptyList(), true, DeviceBatchUploadDirectoryState.MISSING)
    private val exists = missing.copy(directoryState = DeviceBatchUploadDirectoryState.EXISTS)
    private val reviewed get() = requireNotNull(deviceBatchUploadDraft("user//operator", node.id, meta, inventory, options, file, missing, "read"))

    @Test fun legacyListingsWithoutActionStillRequirePathAndRequest() {
        val p = JsonParser.parseString("""{"reqid":"read","path":"/tmp","dir":[]}""").asJsonObject
        assertNotNull(deviceBatchUploadDecodeListing(p, node.id, "/tmp", DevicePlatform.LINUX, "read"))
        assertNull(deviceBatchUploadDecodeListing(p, node.id, "/tmp", DevicePlatform.LINUX, "other"))
        p.addProperty("action", 5)
        assertNull(deviceBatchUploadDecodeListing(p, node.id, "/tmp", DevicePlatform.LINUX, "read"))
    }
    @Test fun mkdirUsesTheReviewedDirectoryOnly() {
        val p = deviceBatchUploadMkdirPacket(reviewed, "mkdir")
        assertEquals("mkdir", p.get("action").asString)
        assertEquals("/tmp/new", p.get("path").asString)
        assertEquals("mkdir", p.get("reqid").asString)
        assertFalse(p.has("cmds"))
    }
    @Test fun mkdirRequiresExplicitOption() {
        assertTrue(runCatching { deviceBatchUploadMkdirPacket(reviewed.copy(options = options.copy(createDirectoryIfMissing = false)), "mkdir") }.isFailure)
    }
    @Test fun existingDirectoryDoesNotTriggerMkdir() = runBlocking {
        var sent = 0
        val result = DeviceBatchUploadDirectoryPreparation.ensure(reviewed, { "user//operator" }, { inventory }, { true },
            { meta }, { exists }, { sent++; true })
        assertFalse(result.creationRequested)
        assertEquals(0, sent)
    }
    @Test fun createsOnceAndRequiresReadback() = runBlocking {
        var sent = 0
        var reads = 0
        val result = DeviceBatchUploadDirectoryPreparation.ensure(reviewed, { "user//operator" }, { inventory }, { true },
            { meta }, { reads++; if (reads == 1) missing else exists }, { sent++; true })
        assertTrue(result.creationRequested)
        assertEquals(DeviceBatchUploadDirectoryState.EXISTS, result.listing.directoryState)
        assertEquals(1, sent)
        assertEquals(2, reads)
    }
    @Test fun unknownStateDoesNotTriggerCreation() = runBlocking {
        var sent = 0
        val failure = runCatching {
            DeviceBatchUploadDirectoryPreparation.ensure(reviewed, { "user//operator" }, { inventory }, { true },
                { meta }, { missing.copy(directoryState = DeviceBatchUploadDirectoryState.UNKNOWN) }, { sent++; true })
        }.exceptionOrNull()
        assertTrue(failure is DeviceBatchUploadDirectoryFailure)
        assertFalse((failure as DeviceBatchUploadDirectoryFailure).mutationAttempted)
        assertEquals(0, sent)
    }
    @Test fun permissionLossBeforeMutationPreventsSend() = runBlocking {
        var metadataReads = 0
        var sent = 0
        val failure = runCatching {
            DeviceBatchUploadDirectoryPreparation.ensure(reviewed, { "user//operator" }, { inventory }, { true },
                { metadataReads++; if (metadataReads == 1) meta else meta.copy(rights = 0L) },
                { missing }, { sent++; true })
        }.exceptionOrNull()
        assertTrue(failure is DeviceBatchUploadDirectoryFailure)
        assertFalse((failure as DeviceBatchUploadDirectoryFailure).mutationAttempted)
        assertEquals(0, sent)
    }
    @Test fun successfulQueueWithoutReadbackIsNotReportedAsReady() = runBlocking {
        var sent = 0
        var reads = 0
        val failure = runCatching {
            DeviceBatchUploadDirectoryPreparation.ensure(reviewed, { "user//operator" }, { inventory }, { true },
                { meta }, { reads++; if (reads == 1) missing else throw java.io.IOException("Read failed") }, { sent++; true })
        }.exceptionOrNull()
        assertTrue(failure is DeviceBatchUploadDirectoryFailure)
        assertTrue((failure as DeviceBatchUploadDirectoryFailure).mutationAttempted)
        assertEquals(1, sent)
    }
    @Test fun cancellationAfterRequestIsNotRetriedOrSwallowed() = runBlocking {
        var attempted = false
        var sent = 0
        val failure = runCatching {
            DeviceBatchUploadDirectoryPreparation.ensure(reviewed, { "user//operator" }, { inventory }, { true },
                { meta }, { if (sent == 0) missing else throw CancellationException("Cancelled") },
                { sent++; true }, { attempted = true })
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(attempted)
        assertEquals(1, sent)
    }
}
