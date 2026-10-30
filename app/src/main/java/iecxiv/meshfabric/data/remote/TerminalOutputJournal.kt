package iecxiv.meshfabric.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TerminalOutputCursor(val epoch: Long, val offset: Long)
data class TerminalOutputSlice(val epoch: Long, val start: Long, val end: Long, val text: String, val gap: Boolean)

/** Bounded stream journal. Revisions are wakeups; the cursor, not event count, orders output. */
internal class TerminalOutputJournal(private val capacity: Int = 1_048_576) {
    private val data = StringBuilder()
    private var epoch = 0L
    private var start = 0L
    private var end = 0L
    private val changed = MutableStateFlow(0L)
    val revision: StateFlow<Long> = changed.asStateFlow()
    init { require(capacity >= 2) }

    @Synchronized fun append(text: String) {
        if (text.isEmpty()) return
        data.append(text)
        end += text.length
        var remove = (data.length - capacity).coerceAtLeast(0)
        if (remove > 0 && remove < data.length &&
            Character.isHighSurrogate(data[remove - 1]) && Character.isLowSurrogate(data[remove])) remove++
        if (remove > 0) { data.delete(0, remove); start += remove }
        changed.value++
    }

    @Synchronized fun clear() {
        epoch++
        data.clear()
        start = 0L
        end = 0L
        changed.value++
    }

    /** Explicit best-effort history replay; live read() remains strict. */
    @Synchronized fun readHistory(cursor: TerminalOutputCursor?, maxChars: Int = 16_384): TerminalOutputSlice =
        read(cursor ?: TerminalOutputCursor(epoch, start), maxChars)

    @Synchronized fun read(cursor: TerminalOutputCursor?, maxChars: Int = 16_384): TerminalOutputSlice {
        require(maxChars >= 2)
        val position = cursor?.offset ?: 0L
        if ((cursor != null && cursor.epoch != epoch) || position < start || position > end) {
            return TerminalOutputSlice(epoch, start, start, "", true)
        }
        val from = (position - start).toInt()
        var to = minOf(data.length, from + maxChars)
        if (to > from && to < data.length &&
            Character.isHighSurrogate(data[to - 1]) && Character.isLowSurrogate(data[to])) to--
        return TerminalOutputSlice(epoch, position, start + to, data.substring(from, to), false)
    }
}
