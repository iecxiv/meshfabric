package iecxiv.meshfabric.data.remote

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

internal object TerminalProtocol {
    fun isHandshake(text: String, connected: Boolean): Boolean =
        !connected && (text == "c" || text == "cr")

    fun options(cols: Int, rows: Int): String = packet("options", cols, rows)
    fun resize(cols: Int, rows: Int): String = packet("termsize", cols, rows)

    private fun packet(type: String, cols: Int, rows: Int): String {
        require(cols >= 5 && rows >= 2)
        return "{\"ctrlChannel\":\"102938\",\"type\":\"$type\",\"cols\":$cols,\"rows\":$rows}"
    }
}

/** Keeps incomplete UTF-8 bytes within one socket generation only. */
internal class TerminalUtf8Decoder {
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    fun decode(bytes: ByteArray): String {
        val input = ByteBuffer.wrap(pending + bytes)
        val output = CharBuffer.allocate(input.remaining() + 1)
        val result = decoder.decode(input, output, false)
        check(!result.isOverflow)
        if (result.isError) result.throwException()
        pending = ByteArray(input.remaining())
        input.get(pending)
        output.flip()
        return output.toString()
    }

    fun reset() {
        pending = ByteArray(0)
        decoder.reset()
    }
}

/** Deduplicates only messages accepted by the local WebSocket send queue, not remote ACKs. */
internal class TerminalResizeState {
    private var lastQueued: Pair<Int, Int>? = null

    fun reset() { lastQueued = null }

    fun send(cols: Int, rows: Int, force: Boolean = false, enqueue: (String) -> Boolean): Boolean {
        if (cols < 5 || rows < 2) return false
        val size = cols to rows
        if (!force && lastQueued == size) return false
        if (!enqueue(TerminalProtocol.resize(cols, rows))) return false
        lastQueued = size
        return true
    }
}
