package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class UploadIntegrationTest {
    @Test fun escapesLeadingZeroWithoutDroppingPayload() {
        assertArrayEquals(byteArrayOf(0, 0, 7), UploadWire.encode(byteArrayOf(0, 7)))
    }
    @Test fun escapesJsonMarker() {
        assertArrayEquals(byteArrayOf(0, 123, 125), UploadWire.encode(byteArrayOf(123, 125)))
    }
    @Test fun ordinaryBinaryAndEmptyDataRemainUnchanged() {
        assertArrayEquals(byteArrayOf(1, 2, 3), UploadWire.encode(byteArrayOf(1, 2, 3)))
        assertArrayEquals(byteArrayOf(), UploadWire.encode(byteArrayOf()))
    }
    @Test fun confirmedSizeComparisonRejectsIncompleteRemoteFile() {
        val local = RemoteUploadEntry("staged", 100, 10, false)
        assertFalse(UploadWire.same(local, RemoteUploadEntry("target", 0, 10, false)))
        assertFalse(UploadWire.same(local, RemoteUploadEntry("target", 99, 10, false)))
        assertTrue(UploadWire.same(local, RemoteUploadEntry("target", 100, 10, false)))
    }
    @Test fun emptyFileIsLegitimatelyZeroBytes() {
        assertTrue(UploadWire.same(RemoteUploadEntry("part", 0, 10, false), RemoteUploadEntry("final", 0, 10, false)))
    }
    @Test fun changedMetadataOrDirectoryCannotBePublishedAsVerifiedFile() {
        val part = RemoteUploadEntry("part", 100, 10, false)
        assertFalse(UploadWire.same(part, RemoteUploadEntry("final", 100, 11, false)))
        assertFalse(UploadWire.same(part, RemoteUploadEntry("final", 100, 10, true)))
        assertFalse(UploadWire.same(part, null))
    }
    @Test fun pathHandlingPreservesWindowsDriveRoot() {
        assertEquals("C:/", UploadWire.parent("C:\\file.bin"))
        assertEquals("file.bin", UploadWire.name("C:\\file.bin"))
        assertEquals("/", UploadWire.parent("/file.bin"))
        assertEquals("/data/file.bin", UploadWire.join("/data", "file.bin"))
    }
    @Test fun refreshAfterUploadRejectsOldZeroByteListing() {
        val nav = FileNavigationState()
        nav.request("/data")
        val stale = nav.next()!!
        nav.refresh()
        assertNull(nav.accept(stale.id.toString(), "/data"))
        val fresh = nav.next()!!
        assertEquals("/data", nav.accept(fresh.id.toString(), "/data"))
    }
    @Test fun refreshPreservesDirectorySelectedDuringUpload() {
        val nav = FileNavigationState()
        nav.request("/other")
        nav.refresh()
        assertEquals("/other", nav.next()!!.path)
    }
    @Test fun retiredUploadAttemptCannotReuseOldSocketOrNotice() {
        val gate = DownloadNetworkAttempt()
        val old = gate.current()
        gate.invalidate()
        var cancelled = false
        assertFalse(gate.bind(old, Any()) { cancelled = true })
        assertTrue(cancelled)
        assertFalse(gate.takeReconnectNotice(old))
        assertTrue(gate.takeReconnectNotice())
    }
}
