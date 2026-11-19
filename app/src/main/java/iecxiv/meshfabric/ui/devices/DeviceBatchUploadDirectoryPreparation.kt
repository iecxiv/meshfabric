package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.util.UUID

internal data class DeviceBatchUploadDirectoryReady(val listing: DeviceBatchUploadListing, val creationRequested: Boolean)
internal class DeviceBatchUploadDirectoryFailure(val mutationAttempted: Boolean, cause: Exception) :
    IOException("Directory preparation failed; creation attempted=$mutationAttempted", cause)

internal fun deviceBatchUploadSameIntent(a: DeviceBatchUploadDraft, b: DeviceBatchUploadDraft): Boolean =
    a.copy(createDirectory = false, overwriteExistingFile = false) == b.copy(createDirectory = false, overwriteExistingFile = false)

internal fun deviceBatchUploadMkdirPacket(reviewed: DeviceBatchUploadDraft, requestId: String): JsonObject {
    require(reviewed.options.createDirectoryIfMissing)
    require(deviceBatchUploadRoute(reviewed.options, reviewed.platform) == reviewed.directory)
    require(reviewed.directory != "/" && !Regex("^[A-Z]:/$").matches(reviewed.directory))
    require(requestId.isNotBlank() && requestId.length <= 128 && requestId.none { it.isISOControl() })
    return JsonObject().apply {
        addProperty("action", "mkdir"); addProperty("path", reviewed.directory); addProperty("reqid", requestId)
    }
}

/** Caller supplies a bound session, fresh metadata and correlated reads. No retries of mkdir. */
internal object DeviceBatchUploadDirectoryPreparation {
    suspend fun ensure(
        reviewed: DeviceBatchUploadDraft,
        actor: () -> String,
        inventory: () -> List<Pair<MeshGroup, List<MeshNode>>>,
        isCurrent: () -> Boolean,
        readMeta: suspend () -> GeneralMeta,
        readListing: suspend () -> DeviceBatchUploadListing,
        send: suspend (JsonObject) -> Boolean,
        onMutationAttempt: () -> Unit = {}
    ): DeviceBatchUploadDirectoryReady {
        var attempted = false
        suspend fun current() {
            currentCoroutineContext().ensureActive()
            check(isCurrent()) { "Upload context changed" }
        }
        fun plan(meta: GeneralMeta, listing: DeviceBatchUploadListing): DeviceBatchUploadDraft {
            val candidate = requireNotNull(deviceBatchUploadDraft(actor(), reviewed.nodeId, meta, inventory(),
                reviewed.options, reviewed.file, listing, listing.requestId))
            require(deviceBatchUploadSameIntent(reviewed, candidate))
            return candidate
        }
        try {
            current()
            val before = readMeta()
            current()
            val listing = readListing()
            current()
            val candidate = plan(before, listing)
            if (!candidate.createDirectory) return DeviceBatchUploadDirectoryReady(listing, false)
            require(reviewed.options.createDirectoryIfMissing)
            val latest = readMeta()
            current()
            plan(latest, listing)
            val packet = deviceBatchUploadMkdirPacket(reviewed, UUID.randomUUID().toString())
            current()
            attempted = true
            onMutationAttempt()
            if (!send(packet)) throw IOException("Directory request not confirmed")
            repeat(5) { attempt ->
                current()
                val observed = readListing()
                current()
                val verified = plan(latest, observed)
                if (!verified.createDirectory) return DeviceBatchUploadDirectoryReady(observed, true)
                if (attempt < 4) delay(200L)
            }
            throw IOException("Destination directory not observed after request")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw DeviceBatchUploadDirectoryFailure(attempted, failure)
        }
    }
}
