package iecxiv.meshfabric.data.remote

/** Tracks default-network identity only; local servers do not require Internet validation. */
internal class DefaultNetworkTracker<N:Any>(initialNetwork:N?=null) {
    private var current:N?=initialNetwork

    @Synchronized fun available(network:N):Boolean {
        if(current==network) return false
        current=network
        return true
    }

    @Synchronized fun lost(network:N):Boolean {
        if(current!=network) return false
        current=null
        return true
    }
}
