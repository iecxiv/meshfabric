package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.DevicePlatform
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchUploadListingProtocolTest {
    private val linux = DevicePlatform.LINUX
    private val node = "node//device"
    private fun packet(entries: String = "[]"): JsonObject = JsonParser.parseString(
        """{"action":"ls","reqid":"read","path":"/tmp","dir":$entries}""").asJsonObject
    private fun decode(value: JsonObject = packet()) = deviceBatchUploadDecodeListing(value, node, "/tmp", linux, "read")

    @Test fun buildsAnExplicitCorrelatedRead() {
        val p = deviceBatchUploadListPacket("/tmp", linux, "read")
        assertEquals("ls", p.get("action").asString)
        assertEquals("/tmp", p.get("path").asString)
        assertEquals("read", p.get("reqid").asString)
    }
    @Test fun emptyVerifiedListingIsNotAnError() {
        assertTrue(requireNotNull(decode()).complete)
        assertEquals(DeviceBatchUploadDirectoryState.EXISTS, requireNotNull(decode()).directoryState)
    }
    @Test fun wrongOrMissingCorrelationIsRejected() {
        assertNull(decode(packet().apply { addProperty("reqid", "stale") }))
        assertNull(decode(packet().apply { remove("reqid") }))
        assertNull(decode(packet().apply { addProperty("path", "/other") }))
        assertNull(decode(packet().apply { addProperty("nodeid", "node//other") }))
    }
    @Test fun errorsAreNotInterpretedAsEmptyOrMissingFolders() {
        assertNull(decode(packet().apply { addProperty("error", "denied") }))
        assertNull(decode(packet().apply { addProperty("result", "error") }))
        assertNull(decode(packet().apply { remove("dir") }))
    }
    @Test fun preservesEntryTypesWithoutGuessingUnknownOnes() {
        val listing = requireNotNull(decode(packet("""[{"n":"folder","t":2},{"n":"file","t":3},{"n":"unknown","t":9}]""")))
        assertEquals(setOf("folder"), listing.directories)
        assertEquals(setOf("file"), listing.regularFiles)
        assertTrue("unknown" in listing.names)
    }
    @Test fun malformedEntriesCannotDisappearFromTheListing() {
        assertNull(decode(packet("""[{"t":3}]""")))
        assertNull(decode(packet("""[{"n":"a","name":"b","t":3}]""")))
        assertNull(decode(packet("""[{"n":"../x","t":3}]""")))
        assertNull(decode(packet("""[{"n":"a","t":"3"}]""")))
    }
    @Test fun multiplePayloadArraysAreAmbiguous() {
        assertNull(decode(packet().apply { add("files", JsonParser.parseString("[]")) }))
    }
    @Test fun windowsDuplicatesAreRejected() {
        val p = packet("""[{"n":"A","t":3},{"n":"a","t":3}]""").apply { addProperty("path", "C:/Temp") }
        assertNull(deviceBatchUploadDecodeListing(p, node, "C:/Temp", DevicePlatform.WINDOWS, "read"))
    }
    @Test fun parentAbsenceProvidesMissingEvidence() {
        val parent = requireNotNull(decode())
        assertEquals(DeviceBatchUploadDirectoryState.MISSING, deviceBatchUploadDirectoryEvidence(parent, "/tmp/new", linux))
    }
    @Test fun parentDirectoryTypeProvidesExistingEvidence() {
        val parent = requireNotNull(decode(packet("""[{"n":"new","t":2}]""")))
        assertEquals(DeviceBatchUploadDirectoryState.EXISTS, deviceBatchUploadDirectoryEvidence(parent, "/tmp/new", linux))
    }
    @Test fun unknownOrFileEntryCannotAuthorizeDirectoryCreation() {
        for (type in listOf(3, 9)) {
            val parent = requireNotNull(decode(packet("""[{"n":"new","t":$type}]""")))
            assertEquals(DeviceBatchUploadDirectoryState.UNKNOWN, deviceBatchUploadDirectoryEvidence(parent, "/tmp/new", linux))
        }
    }
    @Test fun unverifiedParentCannotProveAbsence() {
        assertEquals(DeviceBatchUploadDirectoryState.UNKNOWN, deviceBatchUploadDirectoryEvidence(null, "/tmp/new", linux))
        assertEquals(DeviceBatchUploadDirectoryState.UNKNOWN, deviceBatchUploadDirectoryEvidence(requireNotNull(decode()).copy(complete = false), "/tmp/new", linux))
        assertEquals(DeviceBatchUploadDirectoryState.UNKNOWN, deviceBatchUploadDirectoryEvidence(requireNotNull(decode()), "/other/new", linux))
    }
}
