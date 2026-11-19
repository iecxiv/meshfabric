package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.DevicePlatform
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import iecxiv.meshfabric.data.model.identifyDevicePlatform
import java.util.Locale

internal data class DeviceBatchUploadFile(val name: String, val size: Long, val sha256: String)
internal data class DeviceBatchUploadOptions(
    val windowsDirectory: String = "", val linuxDirectory: String = "",
    val createDirectoryIfMissing: Boolean = false, val overwriteFileIfExists: Boolean = false
)
internal enum class DeviceBatchUploadDirectoryState { EXISTS, MISSING, UNKNOWN }
internal data class DeviceBatchUploadListing(
    val nodeId: String, val directory: String, val requestId: String,
    val names: List<String>, val complete: Boolean,
    val directoryState: DeviceBatchUploadDirectoryState = DeviceBatchUploadDirectoryState.EXISTS,
    val regularFiles: Set<String> = emptySet(), val directories: Set<String> = emptySet()
)
internal data class DeviceBatchUploadDraft(
    val actorId: String, val nodeId: String, val mesh: String, val os: String,
    val platform: DevicePlatform, val directory: String, val file: DeviceBatchUploadFile,
    val options: DeviceBatchUploadOptions, val createDirectory: Boolean, val overwriteExistingFile: Boolean
) {
    val destination: String get() = directory.trimEnd('/') + "/" + file.name
}

// Deliberately portable names for the first batch-upload scope.
internal fun deviceBatchUploadName(name: String): Boolean {
    if (name.isBlank() || name in setOf(".", "..") || name.endsWith('.') || name.endsWith(' ')) return false
    if (!Charsets.UTF_8.newEncoder().canEncode(name) || name.toByteArray(Charsets.UTF_8).size > 255) return false
    if (name.any { it.isISOControl() || it in "/\\:*?\"<>|" }) return false
    val base = name.substringBefore('.').uppercase(Locale.ROOT)
    return base !in setOf("CON", "PRN", "AUX", "NUL") && !Regex("(COM|LPT)[0-9]").matches(base) &&
        base != "CONIN" + '$' && base != "CONOUT" + '$'
}

internal fun deviceBatchUploadDirectory(path: String, platform: DevicePlatform): String? {
    if (path.isBlank() || path != path.trim() || path.length > 4096 || path.any { it.isISOControl() } ||
        !Charsets.UTF_8.newEncoder().canEncode(path)) return null
    val windows = platform == DevicePlatform.WINDOWS
    if (!windows && platform !in setOf(DevicePlatform.LINUX, DevicePlatform.MACOS, DevicePlatform.FREEBSD, DevicePlatform.OPENBSD)) return null
    val value = if (windows) path.replace('\\', '/') else path
    if (value.contains("//")) return null
    val root: String
    val tail: String
    if (windows) {
        if (value.length < 3 || value[0] !in 'A'..'Z' && value[0] !in 'a'..'z' || value[1] != ':' || value[2] != '/') return null
        root = value[0].uppercaseChar().toString() + ":/"
        tail = value.substring(3).trimEnd('/')
    } else {
        if (!value.startsWith('/') || value.contains('\\')) return null
        root = "/"
        tail = value.substring(1).trimEnd('/')
    }
    if (tail.isNotEmpty() && tail.split('/').any { !deviceBatchUploadName(it) }) return null
    return root + tail
}

internal fun deviceBatchUploadRoute(options: DeviceBatchUploadOptions, platform: DevicePlatform): String? = when (platform) {
    DevicePlatform.WINDOWS -> deviceBatchUploadDirectory(options.windowsDirectory, platform)
    DevicePlatform.LINUX -> deviceBatchUploadDirectory(options.linuxDirectory, platform)
    else -> null
}

internal fun deviceBatchUploadAvailable(node: String, meta: GeneralMeta,
    inventory: List<Pair<MeshGroup, List<MeshNode>>>): Boolean {
    if (meta.rights !in -1L..0xffffffffL) return false
    val rights = meta.rights and 0xffffffffL
    if ((rights and 8L) == 0L || rights != 0xffffffffL && (rights and 1024L) != 0L) return false
    val group = inventory.singleOrNull { it.first.id == meta.mesh && it.first.mtype == 2 } ?: return false
    val current = inventory.flatMap { it.second }.singleOrNull { it.id == node } ?: return false
    return current.meshid == meta.mesh && (current.conn and 1) != 0 && group.second.count { it.id == node } == 1
}

internal fun deviceBatchUploadDraft(
    actor: String, node: String, meta: GeneralMeta, inventory: List<Pair<MeshGroup, List<MeshNode>>>,
    options: DeviceBatchUploadOptions, file: DeviceBatchUploadFile, listing: DeviceBatchUploadListing?, expectedRead: String
): DeviceBatchUploadDraft? = runCatching {
    require(deviceBatchAgentUpdateIdentity(actor, node))
    val meshParts = meta.mesh.split('/')
    require(meshParts.size == 3 && meshParts[0] == "mesh" && meshParts[1] == actor.split('/')[1] && meshParts[2].isNotBlank())
    require(meta.mesh.length <= 4096 && meta.mesh.none { it.isISOControl() })
    require(deviceBatchUploadAvailable(node, meta, inventory))
    require(deviceBatchUploadName(file.name) && file.size >= 0L && Regex("[0-9a-f]{64}").matches(file.sha256))
    val platform = identifyDevicePlatform(null, meta.os)
    val path = requireNotNull(deviceBatchUploadRoute(options, platform))
    val received = requireNotNull(listing)
    require(expectedRead.isNotBlank() && received.requestId == expectedRead && received.complete && received.nodeId == node && received.directory == path)
    require(received.directoryState != DeviceBatchUploadDirectoryState.UNKNOWN)
    require(received.names.size <= 10000 && received.names.all { it.isNotEmpty() && it.length <= 4096 && it.none { c -> c.isISOControl() || c == '/' || c == '\\' } })
    val keys = received.names.map { if (platform == DevicePlatform.WINDOWS) it.lowercase(Locale.ROOT) else it }
    require(keys.distinct().size == keys.size)
    require(received.regularFiles.all { it in received.names } && received.directories.all { it in received.names })
    require(received.regularFiles.intersect(received.directories).isEmpty())
    val missing = received.directoryState == DeviceBatchUploadDirectoryState.MISSING
    if (missing) require(options.createDirectoryIfMissing && received.names.isEmpty() && received.regularFiles.isEmpty() && received.directories.isEmpty())
    val collision = received.names.singleOrNull { it.equals(file.name, ignoreCase = platform == DevicePlatform.WINDOWS) }
    if (collision != null) require(options.overwriteFileIfExists && collision in received.regularFiles && collision !in received.directories)
    // These are reviewed intentions, not completed operations or atomic guarantees.
    DeviceBatchUploadDraft(actor, node, meta.mesh, meta.os, platform, path, file, options, missing, collision != null)
}.getOrNull()
