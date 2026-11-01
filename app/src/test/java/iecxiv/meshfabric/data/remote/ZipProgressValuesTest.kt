package iecxiv.meshfabric.data.remote
import org.junit.Assert.*
import org.junit.Test
class ZipProgressValuesTest {
    @Test fun oneMeansOnePercent() {
        val (current, total) = zipProgressValues(1f, null, null)
        assertEquals(0.01f, current.toFloat() / total, 0.00001f)
    }
    @Test fun percentageScaleIsConsistent() {
        listOf(0f, 0.5f, 1f, 50f, 100f).forEach { p ->
            val (current, total) = zipProgressValues(p, null, null)
            assertEquals(p / 100f, current.toFloat() / total, 0.00001f)
        }
    }
    @Test fun unknownTotalsDoNotInventProgress() {
        assertEquals(0L to 0L, zipProgressValues(null, null, null))
        assertEquals(0L to 0L, zipProgressValues(null, 500L, null))
        assertEquals(0L to 0L, zipProgressValues(null, 500L, 0L))
    }
    @Test fun countersRequireValidMatchingRange() {
        assertEquals(25L to 100L, zipProgressValues(null, 25L, 100L))
        assertEquals(0L to 0L, zipProgressValues(null, 200L, 100L))
        assertEquals(0L to 0L, zipProgressValues(null, -1L, 100L))
    }
    @Test fun invalidPercentIsUnknown() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 101f).forEach {
            assertEquals(0L to 0L, zipProgressValues(it, null, null))
        }
    }
}
