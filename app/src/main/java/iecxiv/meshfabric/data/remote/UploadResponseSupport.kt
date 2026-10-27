package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

internal object UploadResponseSupport {
    fun text(packet: JsonObject, name: String): String? =
        packet.get(name)?.takeIf { it.isJsonPrimitive }?.asString

    fun matches(packet: JsonObject, expected: String, id: String?, parent: String?): Boolean {
        val action = text(packet, "action") ?: ""
        val replyId = text(packet, "reqid")
        val path = text(packet, "path")
        val directory = listOf("dir", "files", "list").any { packet.get(it)?.isJsonArray == true }
        if (id != null) {
            if (replyId != null && replyId != id) return false
            if (replyId == null && !(expected == "ls" && parent != null && path != null &&
                        UploadWire.key(path) == UploadWire.key(parent))) return false
        }
        if (expected == "ls" && parent != null && path != null && UploadWire.key(path) != UploadWire.key(parent)) return false
        return if (expected == "ls") action == "ls" || action == "dir" || (action.isEmpty() && directory)
        else action == expected
    }

    suspend fun next(channel: ReceiveChannel<JsonObject>): JsonObject {
        try {
            return channel.receive()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw IOException("Upload response transport was retired", e)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            throw IOException("Upload response transport failed", e)
        }
    }
}
