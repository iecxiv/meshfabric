package iecxiv.meshfabric.data.remote

/** Tracks default-network identity and transports without requiring Internet validation. */
internal class DefaultNetworkTracker<N:Any>(
    initialNetwork:N?=null,
    initialTransports:Set<Int>?=null
) {
    private var current:N?=initialNetwork
    private var transports:Set<Int>?=if(initialNetwork!=null) initialTransports?.toSet() else null

    @Synchronized fun available(network:N):Boolean {
        if(current==network) return false
        current=network
        transports=null
        return true
    }

    @Synchronized fun lost(network:N):Boolean {
        if(current!=network) return false
        current=null
        transports=null
        return true
    }

    @Synchronized fun capabilitiesChanged(network:N,newTransports:Set<Int>):Boolean {
        // Ignore late capabilities from a previous default network.
        if(current!=network) return false
        val previous=transports
        val snapshot=newTransports.toSet()
        transports=snapshot
        // A new network was already reported by available(); establish its baseline once.
        return previous!=null && previous!=snapshot
    }
}
