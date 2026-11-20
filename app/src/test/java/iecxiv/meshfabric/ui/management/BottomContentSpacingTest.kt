package iecxiv.meshfabric.ui.management
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
class BottomContentSpacingTest {
    @Test fun targetContainersHaveNoFixedBottomGap() {
        assertEquals(0.dp,managementBottomFlushPadding.calculateBottomPadding())
        assertEquals(0.dp,registrationBottomFlushPadding.calculateBottomPadding())
        assertEquals(0.dp,noticeInboxBottomFlushPadding.calculateBottomPadding())
    }
    @Test fun sectionTopSpacingAndSideMarginsAreConsistent() {
        assertEquals(8.dp,registrationBottomFlushPadding.calculateTopPadding())
        assertEquals(12.dp,registrationBottomFlushPadding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(0.dp,noticeInboxBottomFlushPadding.calculateTopPadding())
        assertEquals(16.dp,noticeInboxBottomFlushPadding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(0.dp,managementBottomFlushPadding.calculateTopPadding())
    }
    @Test fun otherManagementViewsKeepTheirOriginalPadding() {
        assertEquals(16.dp,managementConnectionContentPadding.calculateBottomPadding())
    }
}
