package iecxiv.meshfabric.ui.terminal

import iecxiv.meshfabric.data.remote.TerminalOutputCursor
import iecxiv.meshfabric.data.remote.TerminalOutputSlice

internal data class TerminalOutputBatch(val token: String, val text: String)

/** Main-thread delivery state; only a matching parser ACK advances the cursor. */
internal class TerminalOutputDelivery {
    private var page: Long? = null
    private var serial = 0L
    private var pending: Pair<String, TerminalOutputCursor>? = null
    var cursor: TerminalOutputCursor? = null
        private set
    val hasPending: Boolean get() = pending != null

    fun attach(generation: Long) {
        page = generation
        cursor = null
        pending = null
    }
    fun invalidate() {
        page = null
        pending = null
        cursor = null
    }
    fun prepare(generation: Long, slice: TerminalOutputSlice): TerminalOutputBatch? {
        if (page != generation || pending != null || slice.gap || slice.text.isEmpty()) return null
        val token = "$generation-${++serial}"
        pending = token to TerminalOutputCursor(slice.epoch, slice.end)
        return TerminalOutputBatch(token, slice.text)
    }
    fun acknowledge(generation: Long, token: String): Boolean {
        val sent = pending ?: return false
        if (page != generation || sent.first != token) return false
        cursor = sent.second
        pending = null
        return true
    }
}
