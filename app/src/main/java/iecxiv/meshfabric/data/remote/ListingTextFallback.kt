package iecxiv.meshfabric.data.remote

/** One read-only compatibility retry per pending listing and socket generation. */
internal class ListingTextFallback {
    private var attempted: Pair<Long, Long>? = null
    fun claim(windows: Boolean, epoch: Long, requestId: Long, elapsedMs: Long): Boolean {
        if (!windows || elapsedMs < 3000L) return false
        val key = epoch to requestId
        if (attempted == key) return false
        attempted = key
        return true
    }
}
