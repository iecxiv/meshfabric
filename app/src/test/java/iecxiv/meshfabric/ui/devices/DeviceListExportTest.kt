package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.DeviceIpv4
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceListExportTest {
    @Test fun exportsOnlySuppliedDevicesWithExplicitFields() {
        val group = MeshGroup(id = "mesh//one", name = "Group")
        val node = MeshNode(id = "node//one", name = "Device", conn = 1, osdesc = "Linux",
            networkIpv4 = listOf(DeviceIpv4("eth0", "10.0.0.1"), DeviceIpv4("eth1", "10.0.0.1")))
        val root = JsonParser.parseString(deviceListInventoryJson(listOf(group to node))).asJsonObject
        assertEquals(1, root.get("formatVersion").asInt)
        val devices = root.getAsJsonArray("devices")
        assertEquals(1, devices.size())
        val exported = devices[0].asJsonObject
        assertEquals(setOf("id", "name", "groupId", "group", "os", "connected", "ipv4"), exported.entrySet().map { it.key }.toSet())
        assertEquals(node.id, exported.get("id").asString)
        assertTrue(exported.get("connected").asBoolean)
        assertEquals(1, exported.getAsJsonArray("ipv4").size())
    }

    @Test fun removesDuplicatesAndBlankIdentifiers() {
        val group = MeshGroup()
        val node = MeshNode(id = "node//one")
        val root = JsonParser.parseString(deviceListInventoryJson(listOf(group to node, group to node, group to MeshNode()))).asJsonObject
        assertEquals(1, root.getAsJsonArray("devices").size())
        assertEquals(0, JsonParser.parseString(deviceListInventoryJson(emptyList())).asJsonObject.getAsJsonArray("devices").size())
    }

    @Test fun preservesUnicodeQuotesAndNewlines() {
        val name = "Equipo \"á\"\nsegunda línea"
        val root = JsonParser.parseString(deviceListInventoryJson(listOf(MeshGroup() to MeshNode(id = "node//one", name = name)))).asJsonObject
        assertEquals(name, root.getAsJsonArray("devices")[0].asJsonObject.get("name").asString)
    }
}
