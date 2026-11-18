package iecxiv.meshfabric.ui.devices

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun deviceChatNeedsKeepAlive(phase: String): Boolean = phase in setOf(
    "loading", "ready", "checking", "connecting", "waiting", "connected", "checking_send"
)

internal object DeviceChatKeepAlive {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions = ConcurrentHashMap<DeviceGeneralChatSession, Job>()
    var onChanged: (() -> Unit)? = null

    fun register(session: DeviceGeneralChatSession) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            session.state.map { deviceChatNeedsKeepAlive(it.phase) }.distinctUntilChanged().collect {
                onChanged?.invoke()
            }
        }
        if (sessions.putIfAbsent(session, job) == null) job.start() else job.cancel()
    }

    fun unregister(session: DeviceGeneralChatSession) {
        sessions.remove(session)?.cancel()
        onChanged?.invoke()
    }

    fun activeCount(): Int = sessions.keys.count { deviceChatNeedsKeepAlive(it.state.value.phase) }

    fun closeAll() {
        val current = sessions.entries.toList()
        sessions.clear()
        current.forEach { (session, job) -> job.cancel(); session.close() }
        onChanged?.invoke()
    }
}
