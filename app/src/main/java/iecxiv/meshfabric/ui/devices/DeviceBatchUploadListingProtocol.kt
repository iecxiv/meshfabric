package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.DevicePlatform
import iecxiv.meshfabric.data.remote.UploadWire

private fun uploadListingText(packet: JsonObject, key: String): String? = packet.get(key)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

internal fun deviceBatchUploadListPacket(directory: String, platform: DevicePlatform, requestId: String): JsonObject {
    require(requestId.isNotBlank() && requestId.length <= 128 && requestId.none { it.isISOControl() })
    val path = requireNotNull(deviceBatchUploadDirectory(directory, platform))
    return JsonObject().apply { addProperty("action", "ls"); addProperty("path", path); addProperty("reqid", requestId) }
}

internal fun deviceBatchUploadDecodeListing(packet: JsonObject, node: String, directory: String,
    platform: DevicePlatform, requestId: String): DeviceBatchUploadListing? = runCatching {
    require(requestId.isNotBlank() && uploadListingText(packet, "reqid") == requestId)
    // Some agents omit action on listings, but must still echo request and path.
    require(!packet.has("action") || uploadListingText(packet, "action") in setOf("ls", "dir"))
    require(!packet.has("error") && !packet.has("err"))
    require(!packet.has("result") || uploadListingText(packet, "result").equals("ok", true))
    require(!packet.has("nodeid") || uploadListingText(packet, "nodeid") == node)
    val expected = requireNotNull(deviceBatchUploadDirectory(directory, platform))
    val received = requireNotNull(deviceBatchUploadDirectory(requireNotNull(uploadListingText(packet, "path")), platform))
    require(expected.equals(received, ignoreCase = platform == DevicePlatform.WINDOWS))
    val fields = listOf("dir", "files", "list").filter { packet.has(it) }
    require(fields.size == 1)
    val array = packet.get(fields.single())
    require(array.isJsonArray && array.asJsonArray.size() <= 10000)
    val names = mutableListOf<String>()
    val files = mutableSetOf<String>()
    val directories = mutableSetOf<String>()
    array.asJsonArray.forEach { raw ->
        require(raw.isJsonObject)
        val entry = raw.asJsonObject
        val short = uploadListingText(entry, "n")
        val long = uploadListingText(entry, "name")
        require(short == null || long == null || short == long)
        val name = requireNotNull(long ?: short)
        require(name.isNotBlank() && name !in setOf(".", "..") && name.length <= 4096 &&
            Charsets.UTF_8.newEncoder().canEncode(name) && name.none { it.isISOControl() || it == '/' || it == '\\' })
        require(names.none { it.equals(name, ignoreCase = platform == DevicePlatform.WINDOWS) })
        names.add(name)
        val type = entry.get("t")
        if (type != null) {
            require(type.isJsonPrimitive && type.asJsonPrimitive.isNumber)
            when (type.asBigDecimal.intValueExact()) {
                1, 2 -> directories.add(name)
                3 -> files.add(name)
                else -> Unit
            }
        }
    }
    DeviceBatchUploadListing(node, expected, requestId, names.toList(), true,
        DeviceBatchUploadDirectoryState.EXISTS, files.toSet(), directories.toSet())
}.getOrNull()

internal fun deviceBatchUploadDirectoryEvidence(parent: DeviceBatchUploadListing?, target: String,
    platform: DevicePlatform): DeviceBatchUploadDirectoryState {
    val path = deviceBatchUploadDirectory(target, platform) ?: return DeviceBatchUploadDirectoryState.UNKNOWN
    if (path == "/" || Regex("^[A-Z]:/$").matches(path)) return DeviceBatchUploadDirectoryState.UNKNOWN
    val expectedParent = deviceBatchUploadDirectory(UploadWire.parent(path), platform) ?: return DeviceBatchUploadDirectoryState.UNKNOWN
    if (parent == null || !parent.complete || parent.requestId.isBlank() || parent.directoryState != DeviceBatchUploadDirectoryState.EXISTS ||
        !parent.directory.equals(expectedParent, ignoreCase = platform == DevicePlatform.WINDOWS)) return DeviceBatchUploadDirectoryState.UNKNOWN
    val matches = parent.names.filter { it.equals(UploadWire.name(path), ignoreCase = platform == DevicePlatform.WINDOWS) }
    if (matches.isEmpty()) return DeviceBatchUploadDirectoryState.MISSING
    val name = matches.singleOrNull() ?: return DeviceBatchUploadDirectoryState.UNKNOWN
    return if (name in parent.directories && name !in parent.regularFiles) DeviceBatchUploadDirectoryState.EXISTS else DeviceBatchUploadDirectoryState.UNKNOWN
}
