package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.DeviceIpv4
import org.junit.Assert.*
import org.junit.Test

class DeviceNetworkDisplayTest {
    @Test fun singleAddressIncludesInterface() {
        assertEquals("192.168.1.154 (vmbr0)", DeviceNetworkDisplay.text(listOf(DeviceIpv4("vmbr0", "192.168.1.154")), false))
    }
    @Test fun androidAddressIncludesWlanName() {
        assertEquals("192.168.1.143 (wlan0)", DeviceNetworkDisplay.text(listOf(DeviceIpv4("wlan0", "192.168.1.143")), false))
    }
    @Test fun multipleAddressesHaveNoPrefixAndKeepInterfaces() {
        val text = DeviceNetworkDisplay.text(listOf(DeviceIpv4("vmbr0", "192.168.1.154"), DeviceIpv4("vpn0", "10.8.0.2")), false)
        assertEquals("192.168.1.154 (vmbr0)\n10.8.0.2 (vpn0)", text)
        assertFalse(text!!.contains("IP:"))
    }
    @Test fun hiddenAddressesHaveNoDisplayText() {
        assertNull(DeviceNetworkDisplay.text(listOf(DeviceIpv4("vmbr0", "192.168.1.154")), true))
    }
    @Test fun emptyInventoryDoesNotInventText() {
        assertNull(DeviceNetworkDisplay.text(emptyList(), false))
        assertNull(DeviceNetworkDisplay.text(emptyList(), true))
    }
    @Test fun deduplicatedInterfaceNamesArePreserved() {
        assertEquals("192.168.1.154 (eth0/vmbr0)", DeviceNetworkDisplay.text(listOf(DeviceIpv4("eth0/vmbr0", "192.168.1.154")), false))
    }
}
