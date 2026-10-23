package iecxiv.meshfabric.data.model

import com.google.gson.annotations.SerializedName

data class UserInfo(
    @SerializedName("_id") val id: String = "",
    val name: String = "",
    val siteadmin: Int = 0,
    val email: String? = null
)

data class MeshGroup(
    @SerializedName("_id") val id: String = "",
    val name: String = "",
    val desc: String = "",
    val mtype: Int = 0
)

data class AgentInfo(
    val id: Int = 0,
    val caps: Int = 0,
    val ver: Int = 0
)

data class MeshNode(
    @SerializedName("_id") val id: String = "",
    val name: String = "",
    val meshid: String = "",
    val conn: Int = 0,
    val osdesc: String? = null,
    val agent: AgentInfo? = null,
    val ip: String? = null,
    val gip: String? = null,
    val localip: String? = null,
    val icon: Int = 0,
    val caps: Int = 0,
    val p: Int = 0
) {
    val isOnline: Boolean
        get() = (conn and 1) != 0 || (conn and 2) != 0 || conn > 0

    val displayIp: String?
        get() {
            val local = localip?.trim()
            if (!local.isNullOrEmpty() && local != "0.0.0.0" && local != "127.0.0.1") return local
            val remote = ip?.trim()
            if (!remote.isNullOrEmpty() && remote != "0.0.0.0" && remote != "127.0.0.1") return remote
            val global = gip?.trim()
            if (!global.isNullOrEmpty() && global != "0.0.0.0" && global != "127.0.0.1") return global
            return null
        }

    val supportsDesktop: Boolean
        get() {
            if (!isOnline) return false
            val agentCaps = agent?.caps ?: caps
            if (agentCaps > 0 && (agentCaps and 1) == 0) return false
            if (p > 0 && (p and 0x1000) != 0) return false
            val os = osdesc?.lowercase() ?: ""
            if (os.contains("headless") || os.contains("no-desktop")) return false
            return true
        }

    val supportsTerminal: Boolean
        get() {
            if (!isOnline) return false
            val agentCaps = agent?.caps ?: caps
            if (agentCaps > 0 && (agentCaps and 2) == 0) return false
            return true
        }

    val supportsFiles: Boolean
        get() {
            if (!isOnline) return false
            val agentCaps = agent?.caps ?: caps
            if (agentCaps > 0 && (agentCaps and 4) == 0) return false
            return true
        }
}

data class FileItem(
    val name: String = "",
    val isDir: Boolean = false,
    val size: Long = 0,
    val modifiedTime: Long = 0,
    val path: String = ""
)

enum class TransferType {
    DOWNLOAD,
    UPLOAD,
    COPY,
    MOVE,
    COMPRESS
}

data class TransferProgress(
    val fileName: String = "",
    val filePath: String = "",
    val isUpload: Boolean = false,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val isCompleted: Boolean = false,
    val type: TransferType = if (isUpload) TransferType.UPLOAD else TransferType.DOWNLOAD
) {
    val percentage: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class ServerConfig(
    val serverUrl: String = "",
    val ignoreSsl: Boolean = false
)

data class SessionData(
    val serverUrl: String = "",
    val username: String = "",
    val authCookie: String = "",
    val ignoreSsl: Boolean = false,
    val isRemembered: Boolean = false
)

enum class SessionStatus {
    NONE,
    CONNECTED,
    DISCONNECTED_WITH_BUFFER
}