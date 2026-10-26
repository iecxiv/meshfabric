package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class SocketGenerationTest {
    @Test fun currentCallbackIsAccepted() {
        val guard=SocketGeneration()
        val epoch=guard.advance()
        var called=false
        guard.runIfCurrent(epoch) { called=true }
        assertTrue(called)
    }

    @Test fun oldCloseAndFailureCannotClearNewConnection() {
        val guard=SocketGeneration()
        val old=guard.advance()
        val current=guard.advance()
        var socket:String?="new"
        guard.runIfCurrent(old) { socket=null }
        guard.runIfCurrent(old) { socket=null }
        assertEquals("new",socket)
        guard.runIfCurrent(current) { socket="current" }
        assertEquals("current",socket)
    }

    @Test fun oldDataAndDelayedResizeAreRejected() {
        val guard=SocketGeneration()
        val old=guard.advance()
        guard.advance()
        val output=StringBuilder()
        var resizeCount=0
        guard.runIfCurrent(old) { output.append("stale") }
        guard.runIfCurrent(old) { resizeCount++ }
        assertEquals("",output.toString())
        assertEquals(0,resizeCount)
    }

    @Test fun disconnectInvalidatesPendingCallbacksAndTimeout() {
        val guard=SocketGeneration()
        val attempt=guard.advance()
        guard.advance()
        var reactivated=false
        var timedOut=false
        guard.runIfCurrent(attempt) { reactivated=true }
        guard.runIfCurrent(attempt) { timedOut=true }
        assertFalse(reactivated)
        assertFalse(timedOut)
    }

    @Test(timeout=2000) fun ownershipMayBeAdvancedInsideACallback() {
        val guard=SocketGeneration()
        val attempt=guard.advance()
        guard.runIfCurrent(attempt) { guard.advance() }
        var repeated=false
        guard.runIfCurrent(attempt) { repeated=true }
        assertFalse(repeated)
    }

    @Test(timeout=2000) fun exceptionReleasesTheOwnershipMonitor() {
        val guard=SocketGeneration()
        val epoch=guard.advance()
        try {
            guard.runIfCurrent(epoch) { throw IllegalStateException("test") }
            fail("Expected callback exception")
        } catch (_:IllegalStateException) { }
        assertTrue(guard.advance()>epoch)
    }
}
