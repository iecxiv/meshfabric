package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class DownloadRecoveryTest {
    private fun work(id: String, node: String = "node-a") = DownloadWork(id, node, "/data/$id", "$id.bin")

    @Test fun fifoWaitsForNetworkAndSerializesWorkers() {
        val q = DownloadRecoveryQueue()
        q.enqueue(work("a")); q.enqueue(work("b"))
        assertNull(q.claimNext(false))
        assertEquals("a", q.claimNext(true)!!.transferId)
        assertNull(q.claimNext(true))
    }

    @Test fun interruptionPreservesProgressButNewAttemptRejectsOldCallbacks() {
        val q = DownloadRecoveryQueue(); q.enqueue(work("a"))
        val old = q.claimNext(true)!!
        assertTrue(q.begin(old, 0, 100)); assertTrue(q.progress(old, 40)); assertTrue(q.interrupt(old))
        assertEquals(40L, q.snapshot("a")!!.bytes)
        val fresh = q.claimNext(true)!!
        assertNotEquals(old, fresh)
        assertFalse(q.progress(old, 60)); assertFalse(q.fail(old)); assertFalse(q.complete(old))
        assertTrue(q.begin(fresh, 40, 100)); assertTrue(q.progress(fresh, 80))
    }

    @Test fun serverWithoutResumeCanRestartSameQueuedWorkAtZero() {
        val q = DownloadRecoveryQueue(); q.enqueue(work("a"))
        val a = q.claimNext(true)!!; q.begin(a, 0, 100); q.progress(a, 40); q.interrupt(a)
        val b = q.claimNext(true)!!
        assertTrue(q.begin(b, 0, 100)); assertEquals(0L, q.snapshot("a")!!.bytes)
        assertEquals(1, q.snapshots().size)
    }

    @Test fun cancelWhileWaitingNeverReturnsToQueue() {
        val q = DownloadRecoveryQueue(); q.enqueue(work("a")); q.enqueue(work("b"))
        val a = q.claimNext(true)!!; q.begin(a, 0, 100); q.interrupt(a)
        assertTrue(q.cancel("a"))
        assertEquals("b", q.claimNext(true)!!.transferId)
        assertFalse(q.begin(a, 0, 100)); assertFalse(q.complete(a))
    }

    @Test fun endAndLengthMustBeVerifiedBeforeCompletion() {
        val q = DownloadRecoveryQueue(); q.enqueue(work("a"))
        val a = q.claimNext(true)!!; q.begin(a, 0, 100); q.progress(a, 40)
        assertFalse(q.complete(a)); assertFalse(q.verify(a, 40, true))
        assertTrue(q.progress(a, 100)); assertFalse(q.verify(a, 100, false))
        assertTrue(q.verify(a, 100, true)); assertTrue(q.complete(a))
        assertEquals(DownloadStage.COMPLETED, q.snapshot("a")!!.stage)
    }

    @Test fun localVerificationSurvivesNetworkLoss() {
        val q = DownloadRecoveryQueue(); q.enqueue(work("a"))
        val a = q.claimNext(true)!!; q.begin(a, 0, 0); q.verify(a, 0, true)
        q.networkUnavailable()
        assertTrue(q.complete(a))
    }

    @Test fun samePathOnDifferentNodesHasIndependentIdentity() {
        val q = DownloadRecoveryQueue()
        q.enqueue(DownloadWork("a", "node-a", "/same", "same"))
        q.enqueue(DownloadWork("b", "node-b", "/same", "same"))
        q.cancel("a")
        assertEquals("b", q.claimNext(true)!!.transferId)
    }

    @Test fun invalidOrRegressiveProgressCannotAdvanceWork() {
        val q = DownloadRecoveryQueue(); q.enqueue(work("a"))
        val a = q.claimNext(true)!!; q.begin(a, 0, 100); q.progress(a, 40)
        assertFalse(q.progress(a, 30)); assertFalse(q.progress(a, 101))
        assertEquals(40L, q.snapshot("a")!!.bytes)
    }

    @Test fun resumeRequiresAdvertisedBytesAndStrongValidator() {
        assertTrue(DownloadResumePolicy.canResume(10, "bytes", "\"v1\""))
        assertFalse(DownloadResumePolicy.canResume(10, null, "\"v1\""))
        assertFalse(DownloadResumePolicy.canResume(10, "none", "\"v1\""))
        assertFalse(DownloadResumePolicy.canResume(10, "bytes", "W/\"v1\""))
        assertFalse(DownloadResumePolicy.canResume(0, "bytes", "\"v1\""))
    }

    @Test fun full200ReplacesPartialInsteadOfAppending() {
        assertEquals(HttpDownloadAction.Replace(100), DownloadResumePolicy.decide(40, 200, contentLength = 100))
    }

    @Test fun matching206CanAppend() {
        assertEquals(HttpDownloadAction.Append(100, 99), DownloadResumePolicy.decide(
            40, 206, "bytes 40-99/100", 60, "\"v1\"", "\"v1\""))
    }

    @Test fun wrongOffsetChangedValidatorAndMissingValidatorMustRestart() {
        assertEquals(HttpDownloadAction.RestartRequest, DownloadResumePolicy.decide(
            40, 206, "bytes 30-99/100", 70, "\"v1\"", "\"v1\""))
        assertEquals(HttpDownloadAction.RestartRequest, DownloadResumePolicy.decide(
            40, 206, "bytes 40-99/100", 60, "\"v1\"", "\"v2\""))
        assertEquals(HttpDownloadAction.RestartRequest, DownloadResumePolicy.decide(
            40, 206, "bytes 40-99/100", 60))
    }

    @Test fun malformedRangesAndInconsistentLengthCannotAppend() {
        assertNull(DownloadResumePolicy.parseRange("bytes 40-39/100"))
        assertNull(DownloadResumePolicy.parseRange("bytes 40-100/100"))
        assertNull(DownloadResumePolicy.parseRange("bytes 40-99/*"))
        assertNull(DownloadResumePolicy.parseRange("bytes 999999999999999999999-99/100"))
        assertEquals(HttpDownloadAction.RestartRequest, DownloadResumePolicy.decide(
            40, 206, "bytes 40-99/100", 59, "\"v1\"", "\"v1\""))
    }

    @Test fun compressedPartialCannotUseWireByteOffset() {
        assertEquals(HttpDownloadAction.RestartRequest, DownloadResumePolicy.decide(
            40, 206, "bytes 40-99/100", 60, "\"v1\"", "\"v1\"", "gzip"))
    }

    @Test fun range416DoesNotMeanDownloadComplete() {
        assertEquals(HttpDownloadAction.RestartRequest, DownloadResumePolicy.decide(100, 416))
    }

    @Test fun transientAndPermanentHttpFailuresAreDifferent() {
        assertEquals(HttpDownloadAction.Retry, DownloadResumePolicy.decide(0, 503))
        assertEquals(HttpDownloadAction.Retry, DownloadResumePolicy.decide(0, 429))
        assertEquals(HttpDownloadAction.Reject(404), DownloadResumePolicy.decide(0, 404))
    }

    @Test fun wireHeaderIsRemovedWithoutInterpretingFileContents() {
        val key = DownloadAttempt("a", 1)
        val payload = "{\"action\":\"download\"}".toByteArray()
        val block = DownloadWireProtocol.decode(key, key, byteArrayOf(1, 0, 0, 1) + payload)!!
        assertArrayEquals(payload, block.payload); assertTrue(block.isLast)
    }

    @Test fun oldSocketAttemptCannotWritePayloadOrFinishNewAttempt() {
        val current = DownloadAttempt("a", 2)
        assertNull(DownloadWireProtocol.decode(current, DownloadAttempt("a", 1), byteArrayOf(1, 0, 0, 1)))
        assertNull(DownloadWireProtocol.decode(null, current, byteArrayOf(1, 0, 0, 1)))
        assertNull(DownloadWireProtocol.decode(current, DownloadAttempt("b", 2), byteArrayOf(1, 0, 0, 1)))
    }

    @Test(expected = IllegalArgumentException::class) fun shortFrameIsRejected() {
        val key = DownloadAttempt("a", 1)
        DownloadWireProtocol.decode(key, key, byteArrayOf(1, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class) fun invalidHeaderIsRejected() {
        val key = DownloadAttempt("a", 1)
        DownloadWireProtocol.decode(key, key, byteArrayOf(1, 0, 0, 2))
    }

    @Test fun controlResponsesRequireExactAttemptId() {
        assertTrue(DownloadWireProtocol.acceptsControl("a:2", "a:2"))
        assertFalse(DownloadWireProtocol.acceptsControl("a:2", "a:1"))
        assertFalse(DownloadWireProtocol.acceptsControl("a:2", null))
    }
}
