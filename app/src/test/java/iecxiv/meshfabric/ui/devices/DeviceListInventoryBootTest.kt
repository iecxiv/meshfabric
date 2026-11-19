package iecxiv.meshfabric.ui.devices

import com.google.gson.Gson
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceListInventoryBootTest {
    private val gson = Gson()
    private val group = MeshGroup(id = "mesh//one", name = "Group")
    private fun decode(raw: String): MeshNode = gson.fromJson("""{"lastbootuptime":$raw}""", MeshNode::class.java)

    @Test fun readsReportedMillisecondsWithoutConversion() {
        assertEquals(1731586752000L, decode("1731586752000").lastBootTime)
    }
    @Test fun absentAndNullBootTimesRemainUnknown() {
        assertNull(gson.fromJson("{}", MeshNode::class.java).lastBootTime)
        assertNull(decode("null").lastBootTime)
    }
    @Test fun zeroAndNegativeValuesRemainUnknown() {
        for (raw in listOf("0", "-1", "-1731586752000")) assertNull(decode(raw).lastBootTime)
    }
    @Test fun fractionsAndOverflowDoNotDiscardTheDevice() {
        for (raw in listOf("1.5", "9223372036854775808", "1e1000")) assertNull(decode(raw).lastBootTime)
    }
    @Test fun unexpectedTypesRemainUnknown() {
        for (raw in listOf("\"1731586752000\"", "true", "{}", "[]")) assertNull(decode(raw).lastBootTime)
    }
    @Test fun copiesPreserveBootTimeAndTags() {
        val node = MeshNode(id = "one", tags = listOf("tag"), lastBootTime = 1731586752000L)
        val copy = node.copy(conn = 1, networkIpv4 = emptyList())
        assertEquals(node.lastBootTime, copy.lastBootTime)
        assertEquals(node.tags, copy.tags)
    }
    @Test fun sortingUsesBootTimeRatherThanDeviceName() {
        val rows = listOf(
            deviceListInventorySortRow(group, MeshNode(id = "old", name = "Alpha", lastBootTime = 1700000000000L)),
            deviceListInventorySortRow(group, MeshNode(id = "new", name = "Zulu", lastBootTime = 1731586752000L))
        )
        assertEquals(listOf("new", "old"), rows.sortedWith(deviceListComparator(DeviceListSort.LAST_BOOT)).map { it.id })
        assertEquals(listOf("old", "new"), rows.sortedWith(deviceListComparator(DeviceListSort.LAST_BOOT, true)).map { it.id })
    }
    @Test fun unknownBootTimesStayLastInBothDirections() {
        val rows = listOf(
            deviceListInventorySortRow(group, MeshNode(id = "unknown")),
            deviceListInventorySortRow(group, MeshNode(id = "known", lastBootTime = 1731586752000L))
        )
        for (reverse in listOf(false, true)) assertEquals("unknown", rows.sortedWith(deviceListComparator(DeviceListSort.LAST_BOOT, reverse)).last().id)
    }
    @Test fun onlineStatusDoesNotInventABootTime() {
        val row = deviceListInventorySortRow(group, MeshNode(id = "online", conn = 1))
        assertNull(row.lastBoot)
    }
    @Test fun serializationUsesTheServerFieldName() {
        val node = MeshNode(id = "one", lastBootTime = 1731586752000L)
        val json = gson.toJson(node)
        assertTrue(json.contains("\"lastbootuptime\""))
        assertEquals(node.lastBootTime, gson.fromJson(json, MeshNode::class.java).lastBootTime)
    }
}
