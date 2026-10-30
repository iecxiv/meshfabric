package iecxiv.meshfabric.data.remote

import com.google.gson.JsonObject
import iecxiv.meshfabric.data.model.FileItem
import java.util.Locale

internal data class WindowsDriveEntry(val name: String, val path: String, val type: String?, val capacity: Long?, val free: Long?) {
    fun item() = FileItem(name = name, isDir = true, path = path)
}

internal object WindowsDriveSupport {
    fun selector(path: String) = path.isEmpty() || path == "/"
    fun directory(path: String): String {
        if (selector(path)) return "/"
        val p = path.replace('\\', '/')
        if (p.length >= 2 && p[0].isLetter() && p[1] == ':') {
            val tail = p.substring(2).replace(Regex("/+"), "/")
            return p[0].uppercaseChar().toString() + ":" + tail.ifEmpty { "/" }
        }
        return if (p.startsWith("//")) "//" + p.drop(2).replace(Regex("/+"), "/") else p
    }
    fun key(path: String) = directory(path).trimEnd('/').lowercase(Locale.ROOT)
    fun wire(path: String, windows: Boolean) = if (windows) { if (selector(path)) "" else directory(path) } else path
    fun drivePath(raw: String): String? {
        if (raw.isBlank() || raw.any { it.code < 32 }) return null
        val p = directory(raw).trimEnd('/')
        val drive = p.length >= 2 && p[0] in 'A'..'Z' && p[1] == ':' && (p.length == 2 || p[2] == '/')
        val unc = p.startsWith("//") && p.drop(2).split('/').filter { it.isNotEmpty() }.size >= 2
        if ((!drive && !unc) || p.split('/').any { it == "." || it == ".." }) return null
        return "$p/"
    }
    fun protectedRoot(path: String, roots: Collection<String>): Boolean {
        val p = directory(path).trimEnd('/')
        if (p.length == 2 && p[0] in 'A'..'Z' && p[1] == ':') return true
        if (p.startsWith("//") && p.drop(2).split('/').filter { it.isNotEmpty() }.size == 2) return true
        return roots.any { key(it) == key(p) }
    }
    fun parent(path: String, windows: Boolean, roots: Collection<String> = emptyList()): String {
        if (!windows) {
            val p = path.trimEnd('/')
            return if (p.isEmpty() || '/' !in p) "/" else p.substringBeforeLast('/').ifEmpty { "/" }
        }
        if (selector(path) || protectedRoot(path, roots)) return "/"
        val p = directory(path).trimEnd('/')
        return if ('/' !in p) "/" else directory(p.substringBeforeLast('/').ifEmpty { "/" })
    }
    private fun text(j: JsonObject, field: String): String? = try { j.get(field)?.takeUnless { it.isJsonNull }?.asString } catch (_: RuntimeException) { null }
    private fun number(j: JsonObject, field: String) = text(j, field)?.toLongOrNull()?.takeIf { it >= 0L }
    fun rootListing(json: JsonObject, windows: Boolean): List<WindowsDriveEntry>? {
        if (!windows || !json.has("path") || !selector(text(json, "path") ?: return null)) return null
        if (text(json, "action").orEmpty() !in setOf("", "ls", "dir")) return null
        val array = listOf("dir", "files", "list").firstNotNullOfOrNull { json.get(it)?.takeIf { value -> value.isJsonArray }?.asJsonArray } ?: return null
        return array.mapNotNull { value ->
            if (!value.isJsonObject) return@mapNotNull null
            val obj = value.asJsonObject
            if (text(obj, "t")?.toIntOrNull() != 1) return@mapNotNull null
            val path = drivePath(text(obj, "n") ?: text(obj, "name") ?: return@mapNotNull null) ?: return@mapNotNull null
            val total = number(obj, "s")?.takeIf { it > 0L }
            val free = number(obj, "f")?.takeIf { total != null && it <= total }
            WindowsDriveEntry(path.replace('/', '\\'), path, text(obj, "dt")?.uppercase(Locale.ROOT), total, free)
        }.distinctBy { key(it.path) }.sortedBy { key(it.path) }
    }
}
