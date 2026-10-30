package iecxiv.meshfabric.ui.terminal

import iecxiv.meshfabric.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal class TerminalReconnectGate {
    private var serial = 0L
    private val active = mutableMapOf<String, Long>()
    @Synchronized fun begin(key: String): Long? {
        if (key in active) return null
        return (++serial).also { active[key] = it }
    }
    @Synchronized fun current(key: String, token: Long) = active[key] == token
    @Synchronized fun finish(key: String, token: Long): Boolean {
        if (active[key] != token) return false
        active.remove(key)
        return true
    }
}

internal enum class ManualReconnectState { IDLE, CONNECTING, FAILED, TIMED_OUT, CANCELLED, CONNECTED_NEW }

/** UI-scope owner: a candidate never replaces the readable session before handshake. */
internal class TerminalManualReconnect(
    private val mesh: MeshCentralClient,
    private val client: TerminalRelayClient,
    private val scope: CoroutineScope
) {
    private val gate = TerminalReconnectGate()
    private val jobs = mutableMapOf<String, Pair<Long, Job>>()
    private val candidates = mutableMapOf<String, TerminalSession>()
    private val versions = MutableStateFlow(0L)
    val revision: StateFlow<Long> = versions.asStateFlow()
    private val states = MutableStateFlow<Map<String, ManualReconnectState>>(emptyMap())
    val state: StateFlow<Map<String, ManualReconnectState>> = states.asStateFlow()
    private val records = MutableStateFlow<Map<String, List<TerminalSession>>>(emptyMap())
    val history: StateFlow<Map<String, List<TerminalSession>>> = records.asStateFlow()
    private fun key(node: String) = node.removePrefix("node//").removePrefix("node/").trim()
    private fun publish(key: String, value: ManualReconnectState) { states.value = states.value + (key to value) }

    fun start(node: String) {
        val key = key(node)
        val old = client.getSession(mesh, node)
        if (old.isConnected() || old.connectionStatus.value == TerminalConnectionStatus.CONNECTING) return
        val token = gate.begin(key) ?: return
        val candidate = TerminalSession(key, mesh, scope).also {
            it.lastCols = old.lastCols
            it.lastRows = old.lastRows
        }
        candidates[key] = candidate
        publish(key, ManualReconnectState.CONNECTING)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var promoted = false
            try {
                withTimeout(30_000L) {
                    mesh.connectionState.first { it }
                    ensureActive()
                    candidate.connect()
                    val status = candidate.connectionStatus.first { it != TerminalConnectionStatus.CONNECTING }
                    check(status == TerminalConnectionStatus.CONNECTED)
                    check(gate.current(key, token))
                    check(client.promoteSession(old, candidate))
                    promoted = true
                    gate.finish(key, token)
                    records.value = records.value + (key to (records.value[key].orEmpty() + old))
                    versions.value++
                    publish(key, ManualReconnectState.CONNECTED_NEW)
                }
            } catch (_: TimeoutCancellationException) {
                if (gate.finish(key, token)) publish(key, ManualReconnectState.TIMED_OUT)
            } catch (e: CancellationException) {
                if (gate.finish(key, token)) publish(key, ManualReconnectState.CANCELLED)
                throw e
            } catch (_: IllegalStateException) {
                if (gate.finish(key, token)) publish(key, ManualReconnectState.FAILED)
            } finally {
                if (!promoted) candidate.closeSocketOnly()
                if (jobs[key]?.first == token) {
                    jobs.remove(key)
                    candidates.remove(key)
                }
            }
        }
        jobs[key] = token to job
        job.start()
    }
    fun cancel(node: String) {
        val key = key(node)
        val entry = jobs[key] ?: return
        if (!gate.finish(key, entry.first)) return
        jobs.remove(key)
        candidates.remove(key)?.closeSocketOnly()
        entry.second.cancel()
        publish(key, ManualReconnectState.CANCELLED)
    }
    fun clear() {
        jobs.keys.toList().forEach { cancel(it) }
        records.value = emptyMap()
        states.value = emptyMap()
    }
}
