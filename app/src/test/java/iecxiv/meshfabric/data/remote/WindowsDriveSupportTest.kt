package iecxiv.meshfabric.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class WindowsDriveSupportTest {
    private fun drive(name: String) = JsonObject().apply { addProperty("n", name); addProperty("t", 1) }
    private fun root(vararg drives: JsonObject) = JsonObject().apply {
        addProperty("path", ""); add("dir", JsonArray().apply { drives.forEach { add(it) } })
    }
    @Test fun windowsSelectorUsesEmptyWirePathButLinuxKeepsSlash() {
        assertEquals("", WindowsDriveSupport.wire("/", true))
        assertEquals("/", WindowsDriveSupport.wire("/", false))
    }
    @Test fun drivePathsAreAbsoluteAndNormalizeSeparators() {
        assertEquals("C:/", WindowsDriveSupport.drivePath("c:"))
        assertEquals("C:/", WindowsDriveSupport.wire("C:\\\\", true))
        assertEquals("C:/Users", WindowsDriveSupport.directory("C:\\Users"))
    }
    @Test fun modernMetadataAndDirectoryNavigationArePreserved() {
        val packet = drive("C:\\").apply { addProperty("s", 1000); addProperty("f", 250); addProperty("dt", "FIXED") }
        val d = WindowsDriveSupport.rootListing(root(packet), true)!!.single()
        assertEquals("C:/", d.path); assertTrue(d.item().isDir)
        assertEquals(1000L, d.capacity!!); assertEquals(250L, d.free!!)
    }
    @Test fun recoveryCoreDoesNotInventCapacity() {
        val packet = drive("D:").apply { addProperty("dt", "removable") }
        val d = WindowsDriveSupport.rootListing(root(packet), true)!!.single()
        assertEquals("REMOVABLE", d.type); assertNull(d.capacity); assertNull(d.free)
    }
    @Test fun emptyListingIsValidAndLinuxIsNotReinterpreted() {
        assertTrue(WindowsDriveSupport.rootListing(root(), true)!!.isEmpty())
        assertNull(WindowsDriveSupport.rootListing(root(drive("C:")), false))
    }
    @Test fun ordinaryDirectoryAndOtherActionsAreNotIntercepted() {
        assertNull(WindowsDriveSupport.rootListing(root().apply { addProperty("path", "C:/Users") }, true))
        assertNull(WindowsDriveSupport.rootListing(root().apply { addProperty("action", "download") }, true))
    }
    @Test fun duplicatesAndUnsafeEntriesAreFiltered() {
        val invalid = drive("file").apply { addProperty("t", 3) }
        val ds = WindowsDriveSupport.rootListing(root(drive("C:"), drive("c:/"), drive("C:/../bad"), invalid), true)!!
        assertEquals(1, ds.size)
    }
    @Test fun upFromDriveRootReturnsToSelector() {
        assertEquals("/", WindowsDriveSupport.parent("C:\\", true))
        assertEquals("C:/", WindowsDriveSupport.parent("C:/Users", true))
        assertEquals("/home", WindowsDriveSupport.parent("/home/user", false))
    }
    @Test fun mountedVolumeAndUncRootsAreProtected() {
        assertEquals("/", WindowsDriveSupport.parent("C:/Mount/Data/", true, listOf("C:/Mount/Data/")))
        assertEquals("/", WindowsDriveSupport.parent("\\\\server\\share\\", true))
        assertNotNull(WindowsDriveSupport.drivePath("\\\\server\\share\\"))
    }
    @Test fun staleRootReplyCannotReplaceQueuedDriveNavigation() {
        val n = FileNavigationState(); val old = n.next()!!
        n.request("C:/"); assertNull(n.accept(old.id.toString(), "/"))
        val fresh = n.next()!!
        assertNull(n.accept(old.id.toString(), "/")); assertEquals(fresh, n.pending)
        assertEquals("C:/", n.accept(fresh.id.toString(), WindowsDriveSupport.directory("C:\\")))
    }
    @Test fun unknownAndInconsistentSpaceIsNotDisplayedAsKnown() {
        val unknown = WindowsDriveSupport.rootListing(root(drive("C:").apply { addProperty("s", 0); addProperty("f", 0) }), true)!!.single()
        assertNull(unknown.capacity); assertNull(unknown.free)
        val bad = WindowsDriveSupport.rootListing(root(drive("C:").apply { addProperty("s", 100); addProperty("f", 200) }), true)!!.single()
        assertNull(bad.free)
    }
}
