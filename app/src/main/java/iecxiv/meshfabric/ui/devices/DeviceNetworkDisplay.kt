package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.DeviceIpv4

internal object DeviceNetworkDisplay {
    fun text(addresses: List<DeviceIpv4>, hidden: Boolean): String? {
        if (hidden || addresses.isEmpty()) return null
        return addresses.joinToString("\n") { "${it.address} (${it.interfaceName})" }
    }
}
