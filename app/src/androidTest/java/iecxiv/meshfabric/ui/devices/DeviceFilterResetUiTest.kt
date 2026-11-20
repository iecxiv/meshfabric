package iecxiv.meshfabric.ui.devices
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class DeviceFilterResetUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun resetPreservesEnabledOsName()=checkReset(true)
    @Test fun resetPreservesDisabledOsName()=checkReset(false)
    private fun checkReset(initialOs:Boolean) {
        var ips by mutableStateOf(true)
        var disconnected by mutableStateOf(true)
        var filter by mutableStateOf(DeviceListFilter.STARRED)
        var os by mutableStateOf(initialOs)
        var sort by mutableStateOf(DeviceListSort.DEVICE)
        var reverse by mutableStateOf(true)
        var selecting by mutableStateOf(true)
        compose.setContent {MaterialTheme {
            val menu=DeviceBrowseMenuState(sort,reverse,filter,os,selecting,
                onSort={sort=it},onReverse={reverse=!reverse},onFilter={filter=it},
                onOsName={os=!os},onSelection={selecting=!selecting})
            DeviceHeaderBrowseFilter(ips,disconnected,{ips=it},{disconnected=it},menu)
        }}
        compose.onNodeWithTag("devices_header_filter").performClick()
        compose.onNodeWithTag("devices_header_reset_filters").performScrollTo().performClick()
        compose.onNodeWithTag("devices_header_filter_menu").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(DeviceListFilter.ALL,filter)
            assertFalse(ips)
            assertFalse(disconnected)
            assertEquals(initialOs,os)
            assertEquals(DeviceListSort.DEVICE,sort)
            assertTrue(reverse)
            assertTrue(selecting)
        }
    }
}
