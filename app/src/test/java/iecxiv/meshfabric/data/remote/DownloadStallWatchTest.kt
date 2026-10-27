package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class DownloadStallWatchTest {
    @Test fun noProgressExpiresAtDeadline() {
        var now = 0L
        val w = DownloadStallWatch(20_000L) { now }
        now = 19_999L; assertFalse(w.expired())
        now = 20_000L; assertTrue(w.expired())
    }
    @Test fun continuousProgressAllowsLongDownloads() {
        var now = 0L
        val w = DownloadStallWatch(20_000L) { now }
        repeat(100) { now += 15_000L; assertFalse(w.expired()); w.touch() }
        assertFalse(w.expired())
    }
    @Test fun receivingDataResetsIdleDeadline() {
        var now = 0L
        val w = DownloadStallWatch(20_000L) { now }
        now = 19_000L; w.touch()
        now = 25_000L; assertFalse(w.expired())
        now = 39_000L; assertTrue(w.expired())
    }
    @Test(expected = IllegalArgumentException::class) fun invalidTimeoutRejected() {
        DownloadStallWatch(0L) { 0L }
    }
}
