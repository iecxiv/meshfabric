package iecxiv.meshfabric.data.remote

internal data class DownloadAttempt(val transferId: String, val number: Long)

internal enum class DownloadStage {
    QUEUED, WAITING_NETWORK, CONNECTING, DOWNLOADING, VERIFYING, COMPLETED, CANCELLED, FAILED
}

internal data class DownloadWork(
    val id: String,
    val nodeId: String,
    val remotePath: String,
    val targetName: String
)

internal data class DownloadSnapshot(
    val work: DownloadWork,
    val stage: DownloadStage = DownloadStage.QUEUED,
    val bytes: Long = 0L,
    val total: Long? = null,
    val attemptNumber: Long = 0L
)

/** In-memory FIFO. Disk I/O and network callbacks belong to the integration layer. */
internal class DownloadRecoveryQueue {
    private val entries = linkedMapOf<String, DownloadSnapshot>()
    private var active: DownloadAttempt? = null

    @Synchronized fun enqueue(work: DownloadWork) {
        require(work.id.isNotBlank() && work.nodeId.isNotBlank() && work.remotePath.isNotBlank())
        require(work.targetName.isNotBlank() && !work.targetName.contains('/') && !work.targetName.contains('\\'))
        require(work.targetName != "." && work.targetName != "..")
        require(!entries.containsKey(work.id))
        entries[work.id] = DownloadSnapshot(work)
    }

    @Synchronized fun snapshots(): List<DownloadSnapshot> = entries.values.toList()
    @Synchronized fun snapshot(id: String): DownloadSnapshot? = entries[id]

    @Synchronized fun claimNext(networkReady: Boolean): DownloadAttempt? {
        if (!networkReady || active != null) return null
        val next = entries.values.firstOrNull {
            it.stage == DownloadStage.QUEUED || it.stage == DownloadStage.WAITING_NETWORK
        } ?: return null
        val attempt = DownloadAttempt(next.work.id, next.attemptNumber + 1L)
        entries[next.work.id] = next.copy(stage = DownloadStage.CONNECTING, attemptNumber = attempt.number)
        active = attempt
        return attempt
    }

    private fun current(key: DownloadAttempt): DownloadSnapshot? =
        if (active == key) entries[key.transferId] else null

    @Synchronized fun begin(key: DownloadAttempt, offset: Long, total: Long?): Boolean {
        val item = current(key) ?: return false
        if (item.stage != DownloadStage.CONNECTING || offset < 0 || (total != null && total < offset)) return false
        entries[key.transferId] = item.copy(stage = DownloadStage.DOWNLOADING, bytes = offset, total = total)
        return true
    }

    @Synchronized fun progress(key: DownloadAttempt, bytes: Long): Boolean {
        val item = current(key) ?: return false
        if (item.stage != DownloadStage.DOWNLOADING || bytes < item.bytes || (item.total != null && bytes > item.total)) return false
        entries[key.transferId] = item.copy(bytes = bytes)
        return true
    }

    @Synchronized fun interrupt(key: DownloadAttempt): Boolean {
        val item = current(key) ?: return false
        if (item.stage != DownloadStage.CONNECTING && item.stage != DownloadStage.DOWNLOADING) return false
        entries[key.transferId] = item.copy(stage = DownloadStage.WAITING_NETWORK)
        active = null
        return true
    }

    @Synchronized fun networkUnavailable() {
        active?.let { interrupt(it) }
        entries.replaceAll { _, item ->
            if (item.stage == DownloadStage.QUEUED) item.copy(stage = DownloadStage.WAITING_NETWORK) else item
        }
    }

    @Synchronized fun verify(key: DownloadAttempt, actualLength: Long, endConfirmed: Boolean): Boolean {
        val item = current(key) ?: return false
        if (item.stage != DownloadStage.DOWNLOADING || !endConfirmed || actualLength != item.bytes ||
            (item.total != null && actualLength != item.total)) return false
        entries[key.transferId] = item.copy(stage = DownloadStage.VERIFYING)
        return true
    }

    /** Call only after successful publication of the verified temporary file. */
    @Synchronized fun complete(key: DownloadAttempt): Boolean {
        val item = current(key) ?: return false
        if (item.stage != DownloadStage.VERIFYING) return false
        entries[key.transferId] = item.copy(stage = DownloadStage.COMPLETED)
        active = null
        return true
    }

    @Synchronized fun cancel(id: String): Boolean {
        val item = entries[id] ?: return false
        if (item.stage in setOf(DownloadStage.COMPLETED, DownloadStage.CANCELLED, DownloadStage.FAILED)) return false
        entries[id] = item.copy(stage = DownloadStage.CANCELLED)
        if (active?.transferId == id) active = null
        return true
    }

    @Synchronized fun fail(key: DownloadAttempt): Boolean {
        val item = current(key) ?: return false
        entries[key.transferId] = item.copy(stage = DownloadStage.FAILED)
        active = null
        return true
    }
}

internal sealed interface HttpDownloadAction {
    data class Replace(val total: Long?) : HttpDownloadAction
    data class Append(val total: Long, val responseEnd: Long) : HttpDownloadAction
    object RestartRequest : HttpDownloadAction
    object Retry : HttpDownloadAction
    data class Reject(val status: Int) : HttpDownloadAction
}

internal object DownloadResumePolicy {
    data class ByteRange(val start: Long, val end: Long, val total: Long)
    private val rangePattern = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)

    fun parseRange(value: String?): ByteRange? {
        val parts = value?.trim()?.let { rangePattern.matchEntire(it) } ?: return null
        val start = parts.groupValues[1].toLongOrNull() ?: return null
        val end = parts.groupValues[2].toLongOrNull() ?: return null
        val total = parts.groupValues[3].toLongOrNull() ?: return null
        if (start > end || end >= total) return null
        return ByteRange(start, end, total)
    }

    fun strongETag(value: String?): String? = value?.trim()?.takeIf {
        it.length >= 2 && it.startsWith('"') && it.endsWith('"') && !it.contains('\r') && !it.contains('\n')
    }

    fun canResume(partialBytes: Long, acceptRanges: String?, etag: String?): Boolean =
        partialBytes > 0L && strongETag(etag) != null &&
            acceptRanges?.split(',')?.any { it.trim().equals("bytes", ignoreCase = true) } == true

    fun decide(
        offset: Long,
        status: Int,
        contentRange: String? = null,
        contentLength: Long? = null,
        expectedETag: String? = null,
        responseETag: String? = null,
        contentEncoding: String? = null
    ): HttpDownloadAction {
        require(offset >= 0 && (contentLength == null || contentLength >= 0))
        if (status == 408 || status == 429 || status in 500..599) return HttpDownloadAction.Retry
        if (status == 416) return HttpDownloadAction.RestartRequest
        if (status == 200) return HttpDownloadAction.Replace(contentLength)
        if (status != 206) return HttpDownloadAction.Reject(status)
        val range = parseRange(contentRange) ?: return HttpDownloadAction.RestartRequest
        if (range.start != offset || (contentLength != null && contentLength != range.end - range.start + 1L)) {
            return HttpDownloadAction.RestartRequest
        }
        if (!contentEncoding.isNullOrBlank() && !contentEncoding.equals("identity", ignoreCase = true)) {
            return HttpDownloadAction.RestartRequest
        }
        if (offset == 0L) {
            return if (range.end == range.total - 1L) HttpDownloadAction.Replace(range.total)
            else HttpDownloadAction.RestartRequest
        }
        val expected = strongETag(expectedETag) ?: return HttpDownloadAction.RestartRequest
        if (strongETag(responseETag) != expected) return HttpDownloadAction.RestartRequest
        return HttpDownloadAction.Append(range.total, range.end)
    }
}

internal object DownloadWireProtocol {
    data class Block(val payload: ByteArray, val isLast: Boolean)

    /** Attempt identity is captured by the owning socket callback, not read from binary data. */
    fun decode(current: DownloadAttempt?, incoming: DownloadAttempt, bytes: ByteArray): Block? {
        if (current != incoming) return null
        require(bytes.size >= 4) { "Short download frame" }
        require(bytes[0] == 1.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0.toByte() &&
            (bytes[3] == 0.toByte() || bytes[3] == 1.toByte())) { "Invalid download header" }
        return Block(bytes.copyOfRange(4, bytes.size), bytes[3] == 1.toByte())
    }

    fun acceptsControl(expectedId: String, responseId: String?): Boolean =
        expectedId.isNotBlank() && responseId == expectedId
}
