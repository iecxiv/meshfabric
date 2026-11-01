package iecxiv.meshfabric.ui.files
import iecxiv.meshfabric.data.model.TransferProgress
import iecxiv.meshfabric.data.model.TransferType
import org.junit.Assert.*
import org.junit.Test
class TransferProgressDetailTest {
    @Test fun zipDoesNotDisplaySyntheticBytes() {
        val zip = TransferProgress(type = TransferType.COMPRESS, bytesTransferred = 6700L, totalBytes = 10000L)
        assertEquals("67%", transferProgressDetail(zip, "6.5 KB / 9.8 KB"))
    }
    @Test fun unknownZipHasNoPercentageOrSize() {
        assertEquals("...", transferProgressDetail(TransferProgress(type = TransferType.COMPRESS), "0 B / 0 B"))
    }
    @Test fun completedZipShowsCompletion() {
        assertEquals("100%", transferProgressDetail(TransferProgress(type = TransferType.COMPRESS, isCompleted = true), "0 B / 0 B"))
    }
    @Test fun otherTransferSizesRemainUnchanged() {
        TransferType.entries.filter { it != TransferType.COMPRESS }.forEach {
            assertEquals("5 MB / 10 MB", transferProgressDetail(TransferProgress(type = it), "5 MB / 10 MB"))
        }
    }
}
