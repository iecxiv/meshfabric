package iecxiv.meshfabric.data.remote

/** Owns the cancellable HTTP call for one download network generation. */
internal class DownloadNetworkAttempt {
    private var generation = 0L
    private var notifiedGeneration: Long? = null
    private var resource: Any? = null
    private var cancelAction: (() -> Unit)? = null

    @Synchronized fun takeReconnectNotice(): Boolean {
        if (notifiedGeneration == generation) return false
        notifiedGeneration = generation
        return true
    }

    @Synchronized fun current(): Long = generation
    @Synchronized fun isCurrent(epoch: Long): Boolean = epoch == generation

    fun bind(epoch: Long, identity: Any, cancel: () -> Unit): Boolean {
        var previous: (() -> Unit)? = null
        val accepted = synchronized(this) {
            if (epoch != generation) false else {
                previous = cancelAction
                resource = identity
                cancelAction = cancel
                true
            }
        }
        previous?.invoke()
        if (!accepted) cancel()
        return accepted
    }

    @Synchronized fun release(epoch: Long, identity: Any) {
        if (epoch == generation && resource === identity) {
            resource = null
            cancelAction = null
        }
    }

    fun invalidate() {
        val cancel = synchronized(this) {
            generation++
            val action = cancelAction
            resource = null
            cancelAction = null
            action
        }
        cancel?.invoke()
    }

    @Synchronized fun runIfCurrent(epoch: Long, block: () -> Unit): Boolean {
        if (epoch != generation) return false
        block()
        return true
    }
}
