package iecxiv.meshfabric.data.remote

import android.content.Context
import iecxiv.meshfabric.data.local.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.IdentityHashMap
import java.util.UUID

/** Archives never enter the current-session map and never contribute a device highlight. */
internal class TerminalHistoryRepository(private val scope: CoroutineScope, private val active: () -> List<TerminalSession>) {
    private var context: Context? = null
    private var mesh: MeshCentralClient? = null
    private var settings: SessionManager? = null
    private var disk: TerminalHistoryDiskStore? = null
    private var account: String? = null
    private var initialized = false
    private var migrateLegacy = false
    private val entries = linkedMapOf<String, TerminalSession>()
    private val owners = IdentityHashMap<TerminalSession, String?>()
    private val pending = mutableMapOf<String, Job>()
    private val changed = MutableStateFlow<Map<String, List<TerminalSession>>>(emptyMap())
    val records: StateFlow<Map<String, List<TerminalSession>>> = changed.asStateFlow()
    private fun publish() { changed.value = entries.values.groupBy { it.cleanNodeId } }
    @Synchronized fun configure(context: Context, mesh: MeshCentralClient, settings: SessionManager) {
        this.context = context.applicationContext; this.mesh = mesh; this.settings = settings
        disk = TerminalHistoryDiskStore(File(context.noBackupFilesDir, "terminal_history")) { settings.isSaveTerminalBuffersEnabled() }
        migrateLegacy = settings.getSession() != null
        ensureLoaded()
    }
    @Synchronized fun ensureLoaded() {
        val manager = settings ?: return
        if (!manager.isSaveTerminalBuffersEnabled()) purgeDisk()
        val next = manager.terminalHistoryAccount()
        if (initialized && account == next) return
        pending.values.forEach { it.cancel() }; pending.clear()
        entries.clear(); owners.clear(); account = next; initialized = true
        if (next != null && manager.isSaveTerminalBuffersEnabled()) {
            disk!!.read(next).forEach { restore(it) }
            if (migrateLegacy) migrate(next)
        }
        migrateLegacy = false
        publish()
    }
    private fun restore(record: TerminalHistoryRecord) {
        val session = TerminalSession(record.nodeId, mesh!!, scope, record.id, record.openedAt)
        session.restoreHistoryRecord(record)
        owners[session] = account
        entries[session.localSessionId] = session
    }
    private fun migrate(account: String) {
        val dir = File(context!!.filesDir, "terminal_buffers")
        dir.listFiles().orEmpty().filter { it.name.startsWith("buffer_") && it.name.endsWith(".txt") && it.length() <= 2_000_000L }.forEach { f ->
            try {
                val node = f.name.removePrefix("buffer_").removeSuffix(".txt")
                val raw = f.readText()
                var text = raw.takeLast(200000)
                if (text.isNotEmpty() && Character.isLowSurrogate(text[0])) text = text.drop(1)
                val id = UUID.nameUUIDFromBytes((account + f.name + f.lastModified()).toByteArray(Charsets.UTF_8)).toString()
                val record = TerminalHistoryRecord(id, node, f.lastModified().coerceAtLeast(0), null, text, raw.length != text.length, 80, 25)
                if (id !in entries) restore(record)
                if (disk!!.write(account, record)) f.delete()
            } catch (_: Exception) { /* Leave unreadable legacy buffers untouched. */ }
        }
    }
    @Synchronized fun register(session: TerminalSession) { ensureLoaded(); owners[session] = account }
    @Synchronized fun archive(session: TerminalSession) {
        ensureLoaded()
        if (session.isConnected() || owners[session] != account || !owners.containsKey(session)) return
        pending.remove(session.localSessionId)?.cancel()
        entries[session.localSessionId] = session
        publish()
        account?.let { disk?.write(it, session.historyRecord()) }
    }
    @Synchronized fun scheduleCheckpoint(session: TerminalSession) {
        if (settings?.isSaveTerminalBuffersEnabled() != true) return
        ensureLoaded()
        if (settings?.isSaveTerminalBuffersEnabled() != true || account == null || owners[session] != account || session.localSessionId in entries || session.localSessionId in pending) return
        val owner = account
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(1000)
            synchronized(this@TerminalHistoryRepository) {
                if (!isActive || owner != account) return@synchronized
                pending.remove(session.localSessionId)
                if (session.localSessionId !in entries && active().any { it === session } && owners[session] == owner)
                    disk?.write(owner!!, session.historyRecord())
            }
        }
        pending[session.localSessionId] = job; job.start()
    }
    @Synchronized fun checkpointAll() {
        ensureLoaded()
        val owner = account ?: return
        (entries.values + active().filter { owners[it] == owner }).distinctBy { it.localSessionId }.forEach { disk?.write(owner, it.historyRecord()) }
    }
    @Synchronized fun remove(node: String, requested: Set<String>): List<TerminalSession> {
        ensureLoaded()
        val currentIds = active().map { it.localSessionId }.toSet()
        val removed = entries.values.filter { it.cleanNodeId == node && it.localSessionId in requested && it.localSessionId !in currentIds && !it.isConnected() && (account == null || disk?.delete(account!!, it.localSessionId) == true) }.toList()
        removed.forEach { pending.remove(it.localSessionId)?.cancel(); entries.remove(it.localSessionId); owners.remove(it) }
        publish(); return removed
    }
    @Synchronized fun purgeDisk() {
        context?.let { TerminalHistoryDiskStore.purge(File(it.noBackupFilesDir, "terminal_history")); File(it.filesDir, "terminal_buffers").deleteRecursively() }
    }
}
