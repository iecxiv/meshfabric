package iecxiv.meshfabric.ui.terminal

import org.junit.Assert.*
import org.junit.Test

class RetainedTerminalStoreTest {
    private class Entry(val identity: String, var attached: Boolean = false, var closes: Int = 0)
    private fun store(limit: Int = 4) = RetainedTerminalStore<String, Entry>(limit, { it.attached }, { it.closes++ })

    @Test fun navigationReusesSameRenderer() {
        val store = store()
        val first = store.acquire("a", { true }) { Entry("session") }
        val second = store.acquire("a", { true }) { error("Unexpected recreation") }
        assertSame(first, second)
        assertEquals(0, first.closes)
    }
    @Test fun sessionsHaveIndependentRenderers() {
        val store = store()
        val first = store.acquire("a", { true }) { Entry("a") }
        val second = store.acquire("b", { true }) { Entry("b") }
        assertNotSame(first, second)
    }
    @Test fun replacedSessionDisposesOldRenderer() {
        val store = store()
        val first = store.acquire("a", { true }) { Entry("old") }
        val second = store.acquire("a", { false }) { Entry("new") }
        assertNotSame(first, second)
        assertEquals(1, first.closes)
    }
    @Test fun disconnectRemovesAndDisposesRenderer() {
        val store = store()
        val first = store.acquire("a", { true }) { Entry("a") }
        store.remove("a"); store.remove("a")
        assertEquals(1, first.closes)
        assertEquals(0, store.size)
    }
    @Test fun logoutDisposesAllRenderersOnce() {
        val store = store()
        val first = store.acquire("a", { true }) { Entry("a") }
        val second = store.acquire("b", { true }) { Entry("b") }
        store.clear(); store.clear()
        assertEquals(1, first.closes)
        assertEquals(1, second.closes)
        assertEquals(0, store.size)
    }
    @Test fun oldestDetachedRendererIsEvicted() {
        val store = store(2)
        val first = store.acquire("a", { true }) { Entry("a") }
        store.acquire("b", { true }) { Entry("b") }
        store.acquire("c", { true }) { Entry("c") }
        assertEquals(1, first.closes)
        assertEquals(2, store.size)
    }
    @Test fun recentlyUsedRendererIsRetained() {
        val store = store(2)
        val first = store.acquire("a", { true }) { Entry("a") }
        val second = store.acquire("b", { true }) { Entry("b") }
        store.acquire("a", { true }) { error("Unexpected recreation") }
        store.acquire("c", { true }) { Entry("c") }
        assertEquals(0, first.closes)
        assertEquals(1, second.closes)
    }
    @Test fun attachedRendererIsNeverEvicted() {
        val store = store(1)
        val first = store.acquire("a", { true }) { Entry("a", attached = true) }
        val second = store.acquire("b", { true }) { Entry("b", attached = true) }
        assertEquals(0, first.closes)
        assertEquals(0, second.closes)
        first.attached = false
        store.trim()
        assertEquals(1, first.closes)
        assertEquals(0, second.closes)
    }
    @Test fun staleDisposalCannotDetachNewHost() {
        val lease = TerminalAttachmentLease()
        val old = lease.attach()
        val current = lease.attach()
        assertFalse(lease.release(old))
        assertEquals(current, lease.current)
        assertTrue(lease.release(current))
    }
    @Test fun duplicateDisposalIsIgnored() {
        val lease = TerminalAttachmentLease()
        val token = lease.attach()
        assertTrue(lease.release(token))
        assertFalse(lease.release(token))
    }
    @Test fun destructionInvalidatesAttachmentToken() {
        val lease = TerminalAttachmentLease()
        val token = lease.attach()
        lease.invalidate()
        assertFalse(lease.release(token))
        assertNull(lease.current)
    }
    @Test fun staleRecoveryCannotRemoveReplacementRenderer() {
        val store = store()
        val old = store.acquire("a", { true }) { Entry("old") }
        val current = store.acquire("a", { false }) { Entry("new") }
        assertFalse(store.removeIf("a", old))
        assertEquals(0, current.closes)
    }
    @Test fun recoveryRemovesOnlyExpectedRendererOnce() {
        val store = store()
        val current = store.acquire("a", { true }) { Entry("a") }
        assertTrue(store.removeIf("a", current))
        assertFalse(store.removeIf("a", current))
        assertEquals(1, current.closes)
    }
}
