package iecxiv.meshfabric.ui.devices

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

private const val BATCH_UPLOAD_BUFFER_BYTES = 64 * 1024

private fun copyBatchUploadFile(name: String, input: InputStream, output: OutputStream?,
    expectedSize: Long?, checkpoint: () -> Unit): DeviceBatchUploadFile {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(BATCH_UPLOAD_BUFFER_BYTES)
    var total = 0L
    while (true) {
        checkpoint()
        var count = input.read(buffer)
        if (count < 0) break
        if (count == 0) {
            val single = input.read()
            if (single < 0) break
            buffer[0] = single.toByte(); count = 1
        }
        checkpoint()
        if (total > Long.MAX_VALUE - count.toLong()) throw IOException("File size overflow")
        total += count.toLong()
        if (expectedSize != null && total > expectedSize) throw IOException("Local file size changed")
        output?.write(buffer, 0, count)
        digest.update(buffer, 0, count)
    }
    checkpoint()
    if (expectedSize != null && total != expectedSize) throw IOException("Local file size changed")
    val hex = "0123456789abcdef"
    val hash = buildString(64) {
        digest.digest().forEach { byte ->
            val value = byte.toInt() and 255
            append(hex[value ushr 4]); append(hex[value and 15])
        }
    }
    return DeviceBatchUploadFile(name, total, hash)
}

internal class PreparedDeviceBatchUpload private constructor(
    private val localFile: File,
    val metadata: DeviceBatchUploadFile
) : Closeable {
    private var closed = false

    @Synchronized fun openVerified(checkpoint: () -> Unit = {}): InputStream {
        check(!closed) { "Prepared file is closed" }
        val input = FileInputStream(localFile)
        try {
            val observed = copyBatchUploadFile(metadata.name, input, null, metadata.size, checkpoint)
            if (observed != metadata) throw IOException("Prepared file content changed")
            input.channel.position(0L)
            checkpoint()
            return input
        } catch (failure: Throwable) {
            runCatching { input.close() }.exceptionOrNull()?.let { failure.addSuppressed(it) }
            throw failure
        }
    }

    @Synchronized override fun close() {
        closed = true
        if (localFile.exists() && !localFile.delete()) throw IOException("Could not remove prepared upload file")
    }

    companion object {
        internal fun create(directory: File, name: String, expectedSize: Long? = null,
            open: () -> InputStream, checkpoint: () -> Unit = {}): PreparedDeviceBatchUpload {
            require(deviceBatchUploadName(name))
            require(expectedSize == null || expectedSize >= 0L)
            checkpoint()
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Upload cache unavailable")
            if (expectedSize != null && directory.usableSpace < expectedSize) throw IOException("Insufficient upload cache space")
            val temporary = File.createTempFile("batch-upload-", ".bin", directory)
            try {
                val metadata = open().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val result = copyBatchUploadFile(name, input, output, expectedSize, checkpoint)
                        output.fd.sync()
                        result
                    }
                }
                checkpoint()
                return PreparedDeviceBatchUpload(temporary, metadata)
            } catch (failure: Throwable) {
                if (temporary.exists() && !temporary.delete()) failure.addSuppressed(IOException("Could not remove incomplete upload file"))
                throw failure
            }
        }
    }
}

internal suspend fun prepareDeviceBatchUploadFile(context: Context, uri: Uri): PreparedDeviceBatchUpload {
    var prepared: PreparedDeviceBatchUpload? = null
    try {
        return withContext(Dispatchers.IO) {
            require(uri.scheme == "content")
            val coroutine = currentCoroutineContext()
            val resolver = context.contentResolver
            var name: String? = null
            var size: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn).takeIf { it >= 0L }
                }
            }
            coroutine.ensureActive()
            PreparedDeviceBatchUpload.create(File(context.cacheDir, "batch-uploads"), requireNotNull(name), size,
                open = { resolver.openInputStream(uri) ?: throw IOException("Local file unavailable") },
                checkpoint = { coroutine.ensureActive() }).also { prepared = it }
        }
    } catch (failure: Throwable) {
        runCatching { prepared?.close() }.exceptionOrNull()?.let { failure.addSuppressed(it) }
        throw failure
    }
}
