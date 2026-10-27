package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UploadResponseSupportTest {
    private fun packet(text: String) = JsonParser.parseString(text).asJsonObject

    @Test fun nativeAgentDirectoryWithoutActionIsAccepted() {
        assertTrue(UploadResponseSupport.matches(packet("""{"path":"/data","dir":[],"reqid":7}"""), "ls", "7", "/data"))
    }
    @Test fun alternateDirectoryArraysAreAccepted() {
        assertTrue(UploadResponseSupport.matches(packet("""{"path":"/data","files":[],"reqid":"x"}"""), "ls", "x", "/data"))
        assertTrue(UploadResponseSupport.matches(packet("""{"path":"/data","list":[],"reqid":"x"}"""), "ls", "x", "/data"))
    }
    @Test fun normalAcknowledgementsAndCloseStillCorrelate() {
        assertTrue(UploadResponseSupport.matches(packet("""{"action":"uploadack","reqid":"x"}"""), "uploadack", "x", null))
        assertTrue(UploadResponseSupport.matches(packet("""{"action":"uploaddone","reqid":"x"}"""), "uploaddone", "x", null))
    }
    @Test fun staleAcknowledgementCannotAdvanceNewAttempt() {
        assertFalse(UploadResponseSupport.matches(packet("""{"action":"uploadack","reqid":"old"}"""), "uploadack", "new", null))
    }
    @Test fun missingAcknowledgementIdIsNotAccepted() {
        assertFalse(UploadResponseSupport.matches(packet("""{"action":"uploadack"}"""), "uploadack", "new", "/data"))
    }
    @Test fun matchingPathDoesNotOverrideWrongRequestId() {
        assertFalse(UploadResponseSupport.matches(packet("""{"path":"/data","dir":[],"reqid":"old"}"""), "ls", "new", "/data"))
    }
    @Test fun wrongDirectoryIsNotAcceptedEvenWithMatchingId() {
        assertFalse(UploadResponseSupport.matches(packet("""{"path":"/other","dir":[],"reqid":"x"}"""), "ls", "x", "/data"))
    }
    @Test fun legacyListingWithoutIdUsesOutstandingDirectory() {
        assertTrue(UploadResponseSupport.matches(packet("""{"path":"C:\\data\\","dir":[]}"""), "ls", "x", "C:/data"))
    }
    @Test fun arbitraryJsonIsNotMisreadAsDirectory() {
        assertFalse(UploadResponseSupport.matches(packet("""{"path":"/data","data":"file content","reqid":"x"}"""), "ls", "x", "/data"))
    }
    @Test fun networkIoFailureReachesRetryAsIoException() = runBlocking {
        val channel = Channel<JsonObject>()
        channel.close(IOException("network changed"))
        var failed = false
        try { UploadResponseSupport.next(channel) } catch (_: IOException) { failed = true }
        assertTrue(failed)
        assertTrue(coroutineContext.isActive)
    }
    @Test fun cancelledTransportDoesNotCancelActiveWorker() = runBlocking {
        val channel = Channel<JsonObject>()
        channel.cancel()
        var retryable = false
        try { UploadResponseSupport.next(channel) } catch (_: IOException) { retryable = true }
        assertTrue(retryable)
        assertTrue(coroutineContext.isActive)
    }
    @Test fun userCancellationIsNotConvertedIntoRetry() = runBlocking {
        val channel = Channel<JsonObject>()
        var retryable = false
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            try { UploadResponseSupport.next(channel) } catch (_: IOException) { retryable = true }
        }
        worker.cancelAndJoin()
        assertFalse(retryable)
        assertTrue(worker.isCancelled)
    }
    @Test fun boundedWaitStillTimesOutWithoutCancellingParent() = runBlocking {
        val channel = Channel<JsonObject>()
        assertNull(withTimeoutOrNull(25L) { UploadResponseSupport.next(channel) })
        assertTrue(coroutineContext.isActive)
        channel.cancel()
    }
}
