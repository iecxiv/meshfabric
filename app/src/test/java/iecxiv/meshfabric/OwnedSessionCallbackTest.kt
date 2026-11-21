package iecxiv.meshfabric
import org.junit.Assert.*
import org.junit.Test
class OwnedSessionCallbackTest {
    @Test fun releasesOwnedSlot() {
        var slot: (() -> Unit)? = null
        val owner=OwnedSessionCallback({slot},{slot=it}) {}
        assertTrue(owner.ownsSlot); owner.release(); assertNull(slot)
    }
    @Test fun releaseCannotRemoveReplacement() {
        var slot: (() -> Unit)? = null
        val first=OwnedSessionCallback({slot},{slot=it}) {}
        val second=OwnedSessionCallback({slot},{slot=it}) {}
        first.release(); assertSame(second.callback,slot); assertTrue(second.ownsSlot)
    }
    @Test fun staleAndReleasedCallbacksCannotExecute() {
        var slot: (() -> Unit)? = null; var firstCalls=0; var secondCalls=0
        val first=OwnedSessionCallback({slot},{slot=it}) {firstCalls++}
        val second=OwnedSessionCallback({slot},{slot=it}) {secondCalls++}
        first.callback(); second.callback()
        assertEquals(0,firstCalls); assertEquals(1,secondCalls)
        second.release(); second.callback(); assertEquals(1,secondCalls)
    }
    @Test fun repeatedReleasePreservesSubsequentOwner() {
        var slot: (() -> Unit)? = null
        val first=OwnedSessionCallback({slot},{slot=it}) {}
        first.release()
        val second=OwnedSessionCallback({slot},{slot=it}) {}
        first.release(); assertSame(second.callback,slot)
    }
}
