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
    @Test fun androidSubfolderAndParentRoundTrip() {
        val path = "/Sdcard/Download/My folder"
        assertEquals("Sdcard/Download/My folder",FileListingPaths.wire(path,true,false))
        assertEquals(path,FileListingPaths.browser("Sdcard/Download/My folder",true,false))
        assertEquals("/Sdcard/Download",WindowsDriveSupport.parent(path,false))
        assertEquals("/",WindowsDriveSupport.parent("/Images",false))
    }
    @Test fun acceptsNormalizedAndroidRootResponse() {
        val navigation = FileNavigationState()
        val request = navigation.next()!!
        assertEquals("/",navigation.accept(request.id.toString(),FileListingPaths.browser("",true,false)))
        assertEquals("/",navigation.confirmedPath)
    }
    @Test fun preservesRequestCorrelation() {
        val navigation = FileNavigationState()
        navigation.request("/Images")
        val request = navigation.next()!!
        val response = FileListingPaths.browser("Images",true,false)
        assertNull(navigation.accept((request.id+1).toString(),response))
        assertNotNull(navigation.pending)
        assertEquals("/Images",navigation.accept(request.id.toString(),response))
        navigation.refresh()
        assertEquals("/Images",navigation.next()!!.path)
    }
    @Test fun linuxPathsAreUnchanged() {
        for (path in listOf("/","/home/user","/var/log")) {
            assertEquals(path,FileListingPaths.wire(path,false,false))
            assertEquals(path,FileListingPaths.browser(path,false,false))
        }
    }
    @Test fun windowsDriveSelectorIsPreserved() {
        assertEquals("",FileListingPaths.wire("/",false,true))
        assertEquals("C:/Users",FileListingPaths.wire("C:/Users",false,true))
        assertEquals("C:/",FileListingPaths.browser("C:",false,true))
    }
}
