package iecxiv.meshfabric.data.remote

/** Serializes connection ownership and callback mutations using the same monitor. */
internal class SocketGeneration {
    @PublishedApi internal val monitor=Any()
    @PublishedApi internal var version=0L

    internal inline fun <T> locked(block:()->T):T=synchronized(monitor) { block() }
    fun current():Long=locked { version }
    fun advance():Long=locked { ++version }
    internal inline fun runIfCurrent(expected:Long,block:()->Unit) {
        locked { if(version==expected) block() }
    }
}
