package iecxiv.meshfabric.ui.devices
import org.junit.Assert.*
import org.junit.Test
class DeviceBatchDialogSizingTest {
    @Test fun phoneUsesAvailableWidth() { assertEquals(320f,deviceBatchDialogWidth(320f),0f) }
    @Test fun tabletIsCapped() { assertEquals(640f,deviceBatchDialogWidth(1000f),0f) }
    @Test fun narrowWindowDoesNotOverflow() { assertEquals(120f,deviceBatchDialogWidth(120f),0f) }
    @Test fun zeroIsSafe() { assertEquals(0f,deviceBatchDialogWidth(0f),0f) }
    @Test fun negativeIsSafe() { assertEquals(0f,deviceBatchDialogWidth(-20f),0f) }
    @Test fun nanIsSafe() { assertEquals(0f,deviceBatchDialogWidth(Float.NaN),0f) }
    @Test fun unboundedWidthHasLimit() { assertEquals(640f,deviceBatchDialogWidth(Float.POSITIVE_INFINITY),0f) }
    @Test fun negativeInfinityIsSafe() { assertEquals(0f,deviceBatchDialogWidth(Float.NEGATIVE_INFINITY),0f) }
}
