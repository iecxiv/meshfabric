package iecxiv.meshfabric.data.model

import java.util.Locale

enum class DevicePlatform(val label: String) {
    WINDOWS("Windows"), LINUX("Linux"), ANDROID("Android"), MACOS("macOS"),
    FREEBSD("FreeBSD"), OPENBSD("OpenBSD"), CHROMEOS("ChromeOS"), UNKNOWN("")
}

// Explicit identifiers from Ylianst/MeshCentral agenttypes.js.
// Unknown IDs are not treated as Linux, and icons/names are not OS evidence.
internal fun identifyDevicePlatform(agentId: Int?, osDescription: String?): DevicePlatform {
    val known = when (agentId) {
        1, 2, 3, 4, 21, 22, 34, 42, 43, 10003, 10004, 10006, 11000, 11001, 11002 -> DevicePlatform.WINDOWS
        12, 14 -> DevicePlatform.ANDROID
        11, 16, 29, 10005 -> DevicePlatform.MACOS
        17 -> DevicePlatform.CHROMEOS
        30 -> DevicePlatform.FREEBSD
        37 -> DevicePlatform.OPENBSD
        5, 6, 7, 8, 9, 10, 13, 15, 18, 19, 20, 24, 25, 26, 27, 28, 32, 33, 35, 36, 40, 41, 45 -> DevicePlatform.LINUX
        else -> DevicePlatform.UNKNOWN
    }
    if (known != DevicePlatform.UNKNOWN) return known
    val text = osDescription.orEmpty().lowercase(Locale.ROOT)
    return when {
        "android" in text -> DevicePlatform.ANDROID
        "windows" in text -> DevicePlatform.WINDOWS
        "chromeos" in text || "chrome os" in text -> DevicePlatform.CHROMEOS
        "macos" in text || "mac os" in text || "darwin" in text -> DevicePlatform.MACOS
        "freebsd" in text -> DevicePlatform.FREEBSD
        "openbsd" in text -> DevicePlatform.OPENBSD
        "linux" in text || "ubuntu" in text || "debian" in text || "fedora" in text || "centos" in text || "rocky linux" in text -> DevicePlatform.LINUX
        else -> DevicePlatform.UNKNOWN
    }
}

val MeshNode.platform: DevicePlatform
    get() = identifyDevicePlatform(agent?.id, osdesc)

val MeshNode.osDisplayName: String
    get() = osdesc?.trim()?.takeIf { it.isNotEmpty() } ?: platform.label
