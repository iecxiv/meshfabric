package iecxiv.meshfabric.ui.desktop

import org.junit.Assert.*
import org.junit.Test

class DesktopClipboardAutoPolicyTest {
    @Test fun activationDoesNotOverwriteEitherClipboard() {
        val policy = DesktopClipboardAutoPolicy()
        policy.observeLocal("android")
        assertNull(policy.takeLocalToSend())
        assertNull(policy.observeRemote("remote"))
    }

    @Test fun subsequentLocalChangesAreSentOnceAndDoNotEchoBack() {
        val policy = DesktopClipboardAutoPolicy()
        policy.observeLocal("base")
        policy.observeRemote("remote base")
        policy.observeLocal("new local")
        assertEquals("new local", policy.takeLocalToSend())
        assertNull(policy.takeLocalToSend())
        assertNull(policy.observeRemote("new local"))
        policy.observeLocal("new local")
        assertNull(policy.takeLocalToSend())
    }

    @Test fun remoteChangesAreNotSentBackToRemote() {
        val policy = DesktopClipboardAutoPolicy()
        policy.observeLocal("base")
        policy.observeRemote("remote base")
        assertEquals("new remote", policy.observeRemote("new remote"))
        policy.observeLocal("new remote")
        assertNull(policy.takeLocalToSend())
        assertNull(policy.observeRemote("new remote"))
    }

    @Test fun latestLocalChangeWinsWhileWaiting() {
        val policy = DesktopClipboardAutoPolicy()
        policy.observeLocal("base")
        policy.observeRemote("remote")
        policy.observeLocal("first")
        assertEquals("first", policy.takeLocalToSend())
        policy.observeLocal("second")
        assertNull(policy.observeRemote("first"))
        assertEquals("second", policy.takeLocalToSend())
        assertNull(policy.observeRemote("second"))
    }

    @Test fun emptyTextIsARealChange() {
        val policy = DesktopClipboardAutoPolicy()
        policy.observeLocal("local")
        policy.observeRemote("remote")
        policy.observeLocal("")
        assertEquals("", policy.takeLocalToSend())
        assertNull(policy.observeRemote(""))
        assertEquals("later", policy.observeRemote("later"))
        assertEquals("", policy.observeRemote(""))
    }

    @Test fun failedWriteDoesNotRetryOrReplaceLocalWithFirstSnapshot() {
        val policy = DesktopClipboardAutoPolicy()
        policy.observeLocal("base")
        policy.observeRemote("old remote")
        policy.observeLocal("new local")
        policy.takeLocalToSend()
        policy.writeFailed()
        assertNull(policy.takeLocalToSend())
        assertNull(policy.observeRemote("old remote"))
        assertEquals("new remote", policy.observeRemote("new remote"))
    }

    @Test fun resetAndAbsentClipboardEstablishFreshBaselines() {
        val policy = DesktopClipboardAutoPolicy()
        policy.seedLocalIfUnknown("")
        policy.observeLocal("copied text")
        assertEquals("copied text", policy.takeLocalToSend())
        policy.reset()
        policy.observeLocal("existing on reactivation")
        assertNull(policy.takeLocalToSend())
        assertNull(policy.observeRemote("existing remote"))
    }

    @Test fun unsupportedLocalDataDiscardsUnsentText() {
        val policy = DesktopClipboardAutoPolicy()
        policy.observeLocal("base")
        policy.observeLocal("pending")
        policy.discardPendingLocal()
        assertNull(policy.takeLocalToSend())
    }

    @Test fun nodePoliciesAreIndependent() {
        val first = DesktopClipboardAutoPolicy()
        val second = DesktopClipboardAutoPolicy()
        first.observeLocal("base")
        second.observeLocal("base")
        first.observeLocal("only first")
        assertEquals("only first", first.takeLocalToSend())
        assertNull(second.takeLocalToSend())
    }
}
