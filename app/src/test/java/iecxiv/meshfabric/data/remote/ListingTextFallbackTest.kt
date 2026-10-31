package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class ListingTextFallbackTest {
    @Test fun waitsThreeSeconds() {
        val retry = ListingTextFallback()
        assertFalse(retry.claim(true,1L,1L,2999L))
        assertTrue(retry.claim(true,1L,1L,3000L))
    }
    @Test fun neverRetriesOtherPlatforms() {
        assertFalse(ListingTextFallback().claim(false,1L,1L,14000L))
    }
    @Test fun onlyOneRetryForPendingRequest() {
        val retry = ListingTextFallback()
        assertTrue(retry.claim(true,1L,1L,3000L))
        assertFalse(retry.claim(true,1L,1L,8000L))
        assertFalse(retry.claim(true,1L,1L,14000L))
    }
    @Test fun nextRequestCanRetryAfterItsOwnDelay() {
        val retry = ListingTextFallback()
        assertTrue(retry.claim(true,1L,1L,3000L))
        assertFalse(retry.claim(true,1L,2L,200L))
        assertTrue(retry.claim(true,1L,2L,3000L))
    }
    @Test fun newSocketGenerationIsIndependent() {
        val retry = ListingTextFallback()
        assertTrue(retry.claim(true,1L,1L,3000L))
        assertTrue(retry.claim(true,3L,1L,3000L))
    }
    @Test fun duplicateListingCannotCompleteAnotherRequest() {
        val navigation = FileNavigationState()
        val first = navigation.next()!!
        assertEquals("/",navigation.accept(first.id.toString(),"/"))
        assertNull(navigation.accept(first.id.toString(),"/"))
        navigation.request("C:/")
        val next = navigation.next()!!
        assertNull(navigation.accept(first.id.toString(),"/"))
        assertEquals(next,navigation.pending)
        assertEquals("C:/",navigation.accept(next.id.toString(),"C:/"))
    }
}
