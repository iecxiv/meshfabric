package iecxiv.meshfabric.data.remote

import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest

internal data class TerminalHistoryRecord(
    val id: String, val nodeId: String, val openedAt: Long, val disconnectedAt: Long?,
    val text: String, val truncated: Boolean, val cols: Int, val rows: Int, val schema: Int = 1
)

/** App-private per-account records. Output is plain text, not encrypted. */
internal class TerminalHistoryDiskStore(private val root: File, private val enabled: () -> Boolean) {
    companion object {
        private val lock = Any()
        private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        fun purge(root: File) = synchronized(lock) { if (root.exists()) root.deleteRecursively(); Unit }
    }
    private val gson = Gson()
    private fun directory(account: String) = File(root, digest(account))
    private fun file(account: String, id: String) = File(directory(account), digest(id) + ".json")
    private fun valid(r: TerminalHistoryRecord) = r.schema == 1 && r.id.isNotBlank() && r.nodeId.isNotBlank() &&
        r.id.length <= 256 && r.nodeId.length <= 4096 && r.text.length <= 200000 && r.openedAt >= 0 && r.cols >= 5 && r.rows >= 2
    fun read(account: String): List<TerminalHistoryRecord> = synchronized(lock) {
        if (!enabled()) { purge(root); return@synchronized emptyList() }
        directory(account).listFiles().orEmpty().filter { it.extension == "json" && it.length() <= 2_000_000L }.mapNotNull { f ->
            try { gson.fromJson(f.readText(), TerminalHistoryRecord::class.java)?.takeIf { valid(it) && f == file(account, it.id) } }
            catch (_: Exception) { null }
        }.distinctBy { it.id }.sortedBy { it.openedAt }
    }
    fun write(account: String, record: TerminalHistoryRecord): Boolean = synchronized(lock) {
        if (!enabled()) { purge(root); return@synchronized true }
        if (!valid(record)) return@synchronized false
        val target = file(account, record.id)
        val temporary = File(target.path + ".tmp")
        try {
            target.parentFile!!.mkdirs()
            FileOutputStream(temporary).use { stream ->
                stream.write(gson.toJson(record).toByteArray(Charsets.UTF_8)); stream.fd.sync()
            }
            try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            true
        } catch (_: Exception) { temporary.delete(); false }
    }
    fun delete(account: String, id: String): Boolean = synchronized(lock) {
        val target = file(account, id)
        val temporary = File(target.path + ".tmp")
        val a = !target.exists() || target.delete()
        val b = !temporary.exists() || temporary.delete()
        a && b
    }
}
