package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class FileNavigationStateTest {
    @Test fun restoresDesiredPathAfterTransportReset() {
        val n = FileNavigationState()
        n.request("/home/data")
        n.next()
        n.transportReset()
        assertEquals("/home/data", n.next()!!.path)
    }
    @Test fun onlyOneRequestCanBeOutstanding() {
        val n = FileNavigationState()
        n.next()
        assertNull(n.next())
    }
    @Test fun olderNavigationCannotReplaceLatestChoice() {
        val n = FileNavigationState()
        n.request("/a")
        val a = n.next()!!
        n.request("/b")
        assertNull(n.accept(a.id.toString(), "/a"))
        assertEquals("/b", n.next()!!.path)
        assertEquals("/", n.confirmedPath)
    }
    @Test fun oldRequestIdIsIgnoredAfterReconnect() {
        val n = FileNavigationState()
        val old = n.next()!!
        n.transportReset()
        val fresh = n.next()!!
        assertNull(n.accept(old.id.toString(), "/"))
        assertEquals(fresh, n.pending)
    }
    @Test fun missingPathUsesOutstandingRequestNotRoot() {
        val n = FileNavigationState()
        n.request("/data")
        n.next()
        assertEquals("/data", n.accept(null, null))
    }
    @Test fun mismatchedPathCannotChangeLocation() {
        val n = FileNavigationState()
        n.request("/data")
        n.next()
        assertNull(n.accept(null, "/other"))
        assertEquals("/", n.confirmedPath)
    }
    @Test fun failedListingKeepsPreviousConfirmedPath() {
        val n = FileNavigationState()
        n.request("/data")
        n.next()
        n.accept(null, "/data")
        n.request("/missing")
        n.next()
        assertNull(n.accept(null, "/missing", false))
        assertEquals("/data", n.confirmedPath)
        assertEquals("/missing", n.desiredPath)
    }
    @Test fun explicitResetReturnsToRoot() {
        val n = FileNavigationState()
        n.request("/data")
        n.reset()
        assertEquals("/", n.next()!!.path)
    }
}
