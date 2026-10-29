package iecxiv.meshfabric.data.remote

internal object TerminalHandshakePolicy {
    fun shouldHandle(data: String, completed: Boolean): Boolean =
        !completed && (data == "c" || data == "cr" || data == "1")
}
