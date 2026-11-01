package iecxiv.meshfabric.ui.files
import org.junit.Assert.*
import org.junit.Test
class RootFileFabVisibilityTest {
    @Test fun hiddenOnlyInVirtualRoots() {
        listOf("", "/").forEach {
            assertTrue(hideRootFileFab(true, false, it))
            assertTrue(hideRootFileFab(false, true, it))
            assertFalse(hideRootFileFab(false, false, it))
        }
    }
    @Test fun visibleInsideDrivesAndCategories() {
        listOf("C:/", "D:/", "C:/Documents").forEach {
            assertFalse(hideRootFileFab(false, true, it))
        }
        listOf("Audio", "Images", "Sdcard", "Videos", "/Sdcard/Download").forEach {
            assertFalse(hideRootFileFab(true, false, it))
        }
    }
}
