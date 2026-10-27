package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class DefaultNetworkTrackerTest {
    @Test fun initialAvailableNetworkDoesNotTriggerReconnect() {
        val tracker=DefaultNetworkTracker("wifi")
        assertFalse(tracker.available("wifi"))
        assertFalse(tracker.available("wifi"))
    }

    @Test fun firstNetworkAfterOfflineStartupIsAChange() {
        val tracker=DefaultNetworkTracker<String>()
        assertTrue(tracker.available("wifi"))
        assertFalse(tracker.available("wifi"))
    }

    @Test fun lateLossOfPreviousNetworkDoesNotDisconnectNewDefault() {
        val tracker=DefaultNetworkTracker("wifi")
        assertTrue(tracker.available("cellular"))
        assertFalse(tracker.lost("wifi"))
        assertFalse(tracker.available("cellular"))
        assertTrue(tracker.lost("cellular"))
    }

    @Test fun repeatedLossIsIgnoredAndRestorationIsReported() {
        val tracker=DefaultNetworkTracker("wifi")
        assertTrue(tracker.lost("wifi"))
        assertFalse(tracker.lost("wifi"))
        assertTrue(tracker.available("wifi"))
    }

    @Test fun unrelatedNetworkLossIsIgnored() {
        val tracker=DefaultNetworkTracker("wifi")
        assertFalse(tracker.lost("other"))
        assertFalse(tracker.available("wifi"))
    }
}
