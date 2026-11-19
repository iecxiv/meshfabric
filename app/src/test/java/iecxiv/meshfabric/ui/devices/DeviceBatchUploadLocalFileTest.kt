package iecxiv.meshfabric.ui.devices

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class DeviceBatchUploadLocalFileTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun source(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))
    private fun remainingFiles() = temporary.root.listFiles().orEmpty().size

    @Test fun measuresContentAndCalculatesKnownSha256() {
        PreparedDeviceBatchUpload.create(temporary.root, "a.txt", open = { source("abc") }).use { file ->
            assertEquals(3L, file.metadata.size)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", file.metadata.sha256)
            file.openVerified().use { assertEquals("abc", it.readBytes().toString(Charsets.UTF_8)) }
        }
        assertEquals(0, remainingFiles())
    }

    @Test fun supportsEmptyFiles() {
        PreparedDeviceBatchUpload.create(temporary.root, "empty.txt", 0L, { source("") }).use { file ->
            assertEquals(0L, file.metadata.size)
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", file.metadata.sha256)
            file.openVerified().use { assertEquals(-1, it.read()) }
        }
    }

    @Test fun acceptsContentBeyondFormer32MiBLimitUsingBoundedReads() {
        val size = 33L * 1024L * 1024L
        var largestRead = 0
        val generated = object : InputStream() {
            var left = size
            override fun read(): Int = if (left <= 0L) -1 else { left--; 0 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (left <= 0L) return -1
                largestRead = maxOf(largestRead, length)
                val count = minOf(left, length.toLong()).toInt()
                buffer.fill(0.toByte(), offset, offset + count)
                left -= count.toLong()
                return count
            }
        }
        PreparedDeviceBatchUpload.create(temporary.root, "large.bin", open = { generated }).use { file ->
            assertEquals(size, file.metadata.size)
            assertTrue(largestRead in 1..65536)
        }
        assertEquals(0, remainingFiles())
    }

    @Test fun closesTheOriginalStreamAfterPreparation() {
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2)) { override fun close() { closed = true; super.close() } }
        PreparedDeviceBatchUpload.create(temporary.root, "a.bin", open = { input }).use { assertTrue(closed) }
    }

    @Test fun mismatchedDeclaredSizesRemovePartialCopies() {
        for (size in listOf(2L, 4L)) {
            assertTrue(runCatching { PreparedDeviceBatchUpload.create(temporary.root, "a.txt", size, { source("abc") }) }.isFailure)
            assertEquals(0, remainingFiles())
        }
    }

    @Test fun sourceReadFailureCleansUpAndClosesSource() {
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("Read failed")
            override fun close() { closed = true }
        }
        assertTrue(runCatching { PreparedDeviceBatchUpload.create(temporary.root, "a.bin", open = { input }) }.isFailure)
        assertTrue(closed)
        assertEquals(0, remainingFiles())
    }

    @Test fun cancellationRemovesPartialCopies() {
        var checks = 0
        val result = runCatching {
            PreparedDeviceBatchUpload.create(temporary.root, "a.txt", open = { source("abc") }, checkpoint = {
                checks++
                if (checks >= 3) throw CancellationException("Cancelled")
            })
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(0, remainingFiles())
    }

    @Test fun invalidNamesDoNotOpenTheSource() {
        var opened = false
        assertTrue(runCatching {
            PreparedDeviceBatchUpload.create(temporary.root, "../a", open = { opened = true; source("abc") })
        }.isFailure)
        assertFalse(opened)
        assertEquals(0, remainingFiles())
    }

    @Test fun eachVerifiedStreamStartsAtTheBeginningIndependently() {
        PreparedDeviceBatchUpload.create(temporary.root, "a.txt", open = { source("abc") }).use { file ->
            file.openVerified().use { first ->
                assertEquals('a'.code, first.read())
                file.openVerified().use { second -> assertEquals('a'.code, second.read()) }
                assertEquals('b'.code, first.read())
            }
        }
    }

    @Test fun sameSizeContentChangesAreDetectedBeforeReturningAStream() {
        PreparedDeviceBatchUpload.create(temporary.root, "a.txt", open = { source("abc") }).use { file ->
            temporary.root.listFiles().orEmpty().single().writeText("xyz")
            assertTrue(runCatching { file.openVerified().use { it.read() } }.isFailure)
        }
    }

    @Test fun closedPreparationCannotBeReused() {
        val file = PreparedDeviceBatchUpload.create(temporary.root, "a.txt", open = { source("abc") })
        file.close()
        file.close()
        assertTrue(runCatching { file.openVerified() }.isFailure)
        assertEquals(0, remainingFiles())
    }
}
