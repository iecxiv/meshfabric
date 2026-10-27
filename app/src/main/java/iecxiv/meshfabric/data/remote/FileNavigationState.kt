package iecxiv.meshfabric.data.remote

internal class FileNavigationState {
    data class Request(val id: Long, val path: String)
    var desiredPath: String = "/"
        private set
    var confirmedPath: String = "/"
        private set
    var pending: Request? = null
        private set
    private var sequence = 0L
    private var dirty = true

    private fun key(path: String) = path.replace('\\', '/').trimEnd('/').ifEmpty { "/" }

    fun request(path: String) {
        val target = path.ifEmpty { "/" }
        if (key(target) != key(desiredPath) || pending == null) dirty = true
        desiredPath = target
    }

    fun refresh() { dirty = true }

    fun next(): Request? {
        if (pending != null || !dirty) return null
        dirty = false
        return Request(++sequence, desiredPath).also { pending = it }
    }

    fun accept(id: String?, path: String?, success: Boolean = true): String? {
        val sent = pending ?: return null
        if (id != null && id != sent.id.toString()) return null
        if (path != null && key(path) != key(sent.path)) return null
        pending = null
        if (dirty || key(desiredPath) != key(sent.path) || !success) return null
        val accepted = path ?: sent.path
        confirmedPath = accepted
        desiredPath = accepted
        return accepted
    }

    fun transportReset() {
        pending = null
        dirty = true
    }

    fun reset() {
        desiredPath = "/"
        confirmedPath = "/"
        transportReset()
    }
}
