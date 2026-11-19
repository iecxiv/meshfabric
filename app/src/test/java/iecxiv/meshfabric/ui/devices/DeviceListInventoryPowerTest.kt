package iecxiv.meshfabric.ui.devices

import com.google.gson.Gson
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceListInventoryPowerTest {
    private val gson = Gson()
    private val group = MeshGroup(id = "mesh//one", name = "Group")
    private fun decode(raw: String): MeshNode = gson.fromJson("""{"_id":"node//one","pwr":$raw}""", MeshNode::class.java)

    @Test fun s0IsReportedAsPoweredOn() {
        assertEquals(true, deviceListPoweredOn(1))
    }
    @Test fun sleepAndHibernateAreKnownNonS0States() {
        for (state in 2..5) assertEquals(false, deviceListPoweredOn(state))
    }
    @Test fun softOffAndOffAreKnownNonS0States() {
        for (state in listOf(6, 8)) assertEquals(false, deviceListPoweredOn(state))
    }
    @Test fun missingUnknownPresentAndUnsupportedStatesAreNotCalledOff() {
        for (state in listOf(null, 0, 7, -1, 9)) assertNull(deviceListPoweredOn(state))
    }
    @Test fun decodesOnlyTheReportedPowerField() {
        assertEquals(1, decode("1").pwr)
        assertEquals(true, deviceListInventorySortRow(group, decode("1")).poweredOn)
        assertNull(gson.fromJson("{}", MeshNode::class.java).pwr)
    }
    @Test fun invalidPowerDataDoesNotDiscardTheNode() {
        for (raw in listOf("null", "true", "\"1\"", "{}", "[]", "1.5", "-1", "9", "2147483648", "1e1000")) {
            val node = decode(raw)
            assertEquals("node//one", node.id)
            assertNull(node.pwr)
        }
    }
    @Test fun connectivityDoesNotInventPowerState() {
        for (conn in listOf(0, 1, 2, 3, 4)) {
            assertNull(deviceListInventorySortRow(group, MeshNode(id = "one", conn = conn)).poweredOn)
        }
    }
    @Test fun protocolFlagsAreNotMistakenForPowerState() {
        assertNull(deviceListInventorySortRow(group, MeshNode(id = "one", p = 1)).poweredOn)
    }
    @Test fun sortingKeepsUnknownLastInBothDirections() {
        val rows = listOf(
            deviceListInventorySortRow(group, MeshNode(id = "unknown", name = "unknown")),
            deviceListInventorySortRow(group, MeshNode(id = "sleep", name = "sleep", pwr = 2)),
            deviceListInventorySortRow(group, MeshNode(id = "on", name = "on", pwr = 1)),
            deviceListInventorySortRow(group, MeshNode(id = "off", name = "off", pwr = 8))
        )
        assertEquals(listOf("on", "off", "sleep", "unknown"), rows.sortedWith(deviceListComparator(DeviceListSort.POWER)).map { it.id })
        assertEquals(listOf("off", "sleep", "on", "unknown"), rows.sortedWith(deviceListComparator(DeviceListSort.POWER, true)).map { it.id })
    }
    @Test fun copiesAndSerializationPreserveTelemetry() {
        val node = MeshNode(id = "one", tags = listOf("tag"), lastBootTime = 1731586752000L, pwr = 1)
        val copy = node.copy(conn = 0, networkIpv4 = emptyList())
        assertEquals(node.pwr, copy.pwr)
        assertEquals(node.lastBootTime, copy.lastBootTime)
        assertEquals(node.tags, copy.tags)
        assertEquals(node.pwr, gson.fromJson(gson.toJson(node), MeshNode::class.java).pwr)
    }
}
