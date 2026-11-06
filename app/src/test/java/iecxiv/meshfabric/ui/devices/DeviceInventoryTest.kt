package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonParser
import iecxiv.meshfabric.data.remote.DeviceInventoryState
import iecxiv.meshfabric.data.remote.inventoryReplyMatches
import iecxiv.meshfabric.data.remote.inventoryRequest
import org.junit.Assert.*
import org.junit.Test

class DeviceInventoryTest {
    private fun packet(text: String) = JsonParser.parseString(text).asJsonObject

    @Test fun requestsAreReadOnlyAndUseStoredInventory() {
        val request = inventoryRequest("getsysinfo", "node/domain/a", "r1")
        assertTrue(request.get("cache").asBoolean)
        assertTrue(request.get("nodeinfo").asBoolean)
        assertEquals("node/domain/a", request.get("nodeid").asString)
    }

    @Test fun mismatchedDeviceAndRequestAreRejected() {
        assertFalse(inventoryReplyMatches(packet("""{"action":"getsysinfo","nodeid":"node/domain/b","responseid":"r1"}"""), "getsysinfo", "node/domain/a", "r1"))
        assertFalse(inventoryReplyMatches(packet("""{"action":"getsysinfo","responseid":"old"}"""), "getsysinfo", "node/domain/a", "r1"))
        assertTrue(inventoryReplyMatches(packet("""{"action":"getsysinfo","nodeid":"node/domain/a"}"""), "getsysinfo", "node/domain/a", "r1"))
    }

    @Test fun linuxHardwareAndNetworkValuesArePreserved() {
        val state = DeviceInventoryState(pending = emptySet(), packets = mapOf(
            "getsysinfo" to packet("""{"hardware":{"linux":{"arch":"x86_64","kernel_release":"6.19","bios_vendor":"Microsoft","board_name":"Surface","memory":[{"Size":4096}],"volumes":{"/":{"size":100,"free":0}}},"identifiers":{"cpu_name":"Intel","gpu_name":["GPU 1","GPU 2"]},"tpm":{"IsEnabled":false}}}"""),
            "getnetworkinfo" to packet("""{"action":"getnetworkinfo","netif2":{"wlan0":[{"mac":"aa:bb","family":"IPv6","address":"fe80::1","gateway":"192.168.1.1"}]},"dns":["192.168.1.16"]}""")
        ))
        val sections = inventorySections(state).associate { it.key to it.rows }
        assertTrue(sections.getValue("os").any { it.value == "6.19" })
        assertTrue(sections.getValue("bios").any { it.value == "Microsoft" })
        assertTrue(sections.getValue("gpu").any { it.value == "GPU 2" })
        assertTrue(sections.getValue("storage").any { it.value == "0" })
        assertTrue(sections.getValue("tpm").any { it.value == "false" })
        assertTrue(sections.getValue("network").any { it.value == "fe80::1" })
        assertTrue(sections.getValue("network").any { it.value == "192.168.1.16" })
    }

    @Test fun windowsCollectionsAndUnknownFieldsAreNotDiscarded() {
        val sections = inventorySections(DeviceInventoryState(pending = emptySet(), packets = mapOf(
            "getsysinfo" to packet("""{"hardware":{"windows":{"osinfo":{"OSArchitecture":"64-bit"},"memory":[{"Capacity":"4294967296"}],"volumes":{"C:":{"size":200}}},"new_platform_field":{"value":"kept"}}}""")
        ))).associate { it.key to it.rows }
        assertTrue(sections.getValue("memory").any { it.value == "4294967296" })
        assertTrue(sections.getValue("extra").any { it.value == "kept" })
    }
}
