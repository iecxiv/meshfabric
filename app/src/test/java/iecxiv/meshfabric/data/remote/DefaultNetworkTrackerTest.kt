package iecxiv.meshfabric.data.remote

import org.junit.Assert.*
import org.junit.Test

class DefaultNetworkTrackerTest {
    // Android transport values; keep the tracker tests independent of Android runtime.
    private val vpnWifi=setOf(4,1)
    private val vpnCellular=setOf(4,0)

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

    @Test fun firstCapabilitiesEstablishBaselineWithoutReconnect() {
        val tracker=DefaultNetworkTracker("vpn")
        assertFalse(tracker.capabilitiesChanged("vpn",vpnWifi))
        assertFalse(tracker.capabilitiesChanged("vpn",vpnWifi))
    }

    @Test fun sameVpnReportsWifiToCellularAndBack() {
        val tracker=DefaultNetworkTracker("vpn",vpnWifi)
        assertFalse(tracker.available("vpn"))
        assertTrue(tracker.capabilitiesChanged("vpn",vpnCellular))
        assertFalse(tracker.capabilitiesChanged("vpn",vpnCellular))
        assertTrue(tracker.capabilitiesChanged("vpn",vpnWifi))
    }

    @Test fun sameVpnReportsCellularToWifiAndBack() {
        val tracker=DefaultNetworkTracker("vpn",vpnCellular)
        assertTrue(tracker.capabilitiesChanged("vpn",vpnWifi))
        assertFalse(tracker.capabilitiesChanged("vpn",vpnWifi))
        assertTrue(tracker.capabilitiesChanged("vpn",vpnCellular))
    }

    @Test fun repeatedTransportSetsAndDifferentOrderingAreIgnored() {
        val tracker=DefaultNetworkTracker("vpn",vpnWifi)
        assertFalse(tracker.capabilitiesChanged("vpn",setOf(1,4)))
        assertFalse(tracker.capabilitiesChanged("vpn",setOf(4,1)))
    }

    @Test fun firstCapabilitiesOfNewDefaultDoNotDuplicateAvailableEvent() {
        val tracker=DefaultNetworkTracker("wifi",setOf(1))
        assertTrue(tracker.available("vpn"))
        assertFalse(tracker.capabilitiesChanged("vpn",vpnWifi))
        assertTrue(tracker.capabilitiesChanged("vpn",vpnCellular))
    }

    @Test fun staleCapabilitiesCannotOverwriteCurrentDefaultTransports() {
        val tracker=DefaultNetworkTracker("oldVpn",vpnWifi)
        assertTrue(tracker.available("newVpn"))
        assertFalse(tracker.capabilitiesChanged("newVpn",vpnCellular))
        assertFalse(tracker.capabilitiesChanged("oldVpn",vpnWifi))
        assertFalse(tracker.capabilitiesChanged("newVpn",vpnCellular))
        assertTrue(tracker.capabilitiesChanged("newVpn",vpnWifi))
    }

    @Test fun capabilitiesAfterLossAreIgnoredUntilAvailable() {
        val tracker=DefaultNetworkTracker("vpn",vpnWifi)
        assertTrue(tracker.lost("vpn"))
        assertFalse(tracker.capabilitiesChanged("vpn",vpnCellular))
        assertTrue(tracker.available("vpn"))
        assertFalse(tracker.capabilitiesChanged("vpn",vpnCellular))
        assertTrue(tracker.capabilitiesChanged("vpn",vpnWifi))
    }

    @Test fun initialTransportSetIsDefensivelyCopied() {
        val initial=mutableSetOf(4,1)
        val tracker=DefaultNetworkTracker("vpn",initial)
        initial.clear()
        initial.addAll(vpnCellular)
        assertFalse(tracker.capabilitiesChanged("vpn",vpnWifi))
        assertTrue(tracker.capabilitiesChanged("vpn",vpnCellular))
    }
}
