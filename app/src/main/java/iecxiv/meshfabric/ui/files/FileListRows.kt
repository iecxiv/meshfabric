package iecxiv.meshfabric.ui.files

import iecxiv.meshfabric.data.model.FileItem

internal data class FileListRow(val key: String, val item: FileItem)

internal fun fileListRows(items: List<FileItem>): List<FileListRow> {
    val occurrences = mutableMapOf<FileItem, Int>()
    return items.map { item ->
        val ordinal = occurrences[item] ?: 0
        occurrences[item] = ordinal + 1
        val identity = listOf(item.path, item.name, item.isDir.toString(),
            item.size.toString(), item.modifiedTime.toString())
            .joinToString("") { value -> "${value.length}:$value" }
        FileListRow("file:$identity#$ordinal", item)
    }
}
