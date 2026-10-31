package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class FileListingPathsTest {
    @Test fun androidRootUsesEmptyWirePath() {
        assertEquals("",FileListingPaths.wire("/",true,false))
        assertEquals("/",FileListingPaths.browser("",true,false))
    }
    @Test fun allFourVirtualRootsRoundTrip() {
        for (root in listOf("Sdcard","Images","Audio","Videos")) {
            assertEquals(root,FileListingPaths.wire("/"+root,true,false))
            assertEquals("/"+root,FileListingPaths.browser(root,true,false))
        }
    }
    @Test fun androidSubfolderRoundTrip() {
        val path = "/Sdcard/Download/My folder"
        assertEquals("Sdcard/Download/My folder",FileListingPaths.wire(path,true,false))
        assertEquals(path,FileListingPaths.browser("Sdcard/Download/My folder",true,false))
    }
    @Test fun linuxPathsAreUnchanged() {
        for (path in listOf("/","/home/user","/var/log")) {
            assertEquals(path,FileListingPaths.wire(path,false,false))
            assertEquals(path,FileListingPaths.browser(path,false,false))
        }
    }
    @Test fun windowsPathsAreUnchanged() {
        assertEquals("",FileListingPaths.wire("/",false,true))
        assertEquals("C:/Users",FileListingPaths.wire("C:/Users",false,true))
        assertEquals("C:/",FileListingPaths.browser("C:",false,true))
    }
}
