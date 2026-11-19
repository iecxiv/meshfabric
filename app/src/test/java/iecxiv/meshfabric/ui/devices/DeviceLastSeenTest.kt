package iecxiv.meshfabric.ui.devices

import com.google.gson.Gson
import com.google.gson.JsonParser
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceLastSeenTest {
    private val actor = "user//admin"
    private val allowed = setOf("node//one", "node//two")
    private val group = MeshGroup(id = "mesh//one", name = "Group")
    private fun packet(values: String) = JsonParser.parseString("""{"action":"lastconnects","lastconnects":$values}""").asJsonObject
    private fun decode(values: String) = deviceLastSeenValues(packet(values), actor, allowed)

    @Test fun readsServerMillisecondsWithoutConversion() {
        assertEquals(mapOf("node//one" to 1731586752000L), decode("""{"node//one":1731586752000}"""))
    }
    @Test fun ignoresUnknownNodesAndOtherDomains() {
        val values = packet("""{"node//one":10,"node//unknown":20,"node/other/three":30}""")
        assertEquals(mapOf("node//one" to 10L), deviceLastSeenValues(values, actor, allowed + "node/other/three"))
    }
    @Test fun missingInvalidOrNonNumericTimesAreUnknown() {
        for (raw in listOf("0", "-1", "null", "true", "\"10\"", "{}", "[]")) {
            assertEquals(emptyMap<String, Long>(), decode("""{"node//one":$raw}"""))
        }
    }
    @Test fun rejectsUnrelatedAndDeniedMessages() {
        assertNull(deviceLastSeenValues(packet("{}").apply { addProperty("action", "nodes") }, actor, allowed))
        assertNull(deviceLastSeenValues(packet("{}").apply { addProperty("error", "denied") }, actor, allowed))
    }
    @Test fun rejectsMalformedMaps() {
        for (raw in listOf("null", "[]", "42", "\"text\"")) assertNull(decode(raw))
    }
    @Test fun rejectsInvalidAccountIdentifiers() {
        for (invalid in listOf("", "admin", "node//admin", "user//")) {
            assertNull(deviceLastSeenValues(packet("{}"), invalid, allowed))
        }
    }
    @Test fun fractionsAndOverflowAreNotRounded() {
        for (raw in listOf("1.5", "9223372036854775808", "1e1000")) {
            assertEquals(emptyMap<String, Long>(), decode("""{"node//one":$raw}"""))
        }
    }
    @Test fun currentlyConnectedDevicesComeFirstWithoutInventingDates() {
        val live = deviceListInventorySortRow(group, MeshNode(id = "live", lastSeenConnected = true))
        val dated = deviceListInventorySortRow(group, MeshNode(id = "dated", lastSeenAt = 1731586752000L))
        assertNull(live.lastSeen)
        assertEquals(listOf("live", "dated"), listOf(dated, live).sortedWith(deviceListComparator(DeviceListSort.LAST_SEEN)).map { it.id })
    }
    @Test fun reversingKeepsUnknownValuesLast() {
        val rows = listOf(
            deviceListInventorySortRow(group, MeshNode(id = "unknown")),
            deviceListInventorySortRow(group, MeshNode(id = "live", lastSeenConnected = true)),
            deviceListInventorySortRow(group, MeshNode(id = "dated", lastSeenAt = 1731586752000L))
        )
        assertEquals(listOf("dated", "live", "unknown"), rows.sortedWith(deviceListComparator(DeviceListSort.LAST_SEEN, true)).map { it.id })
    }
    @Test fun otherTelemetryDoesNotInventLastSeenDates() {
        val row = deviceListInventorySortRow(group, MeshNode(id = "one", conn = 1, lastBootTime = 1731586752000L, pwr = 1))
        assertNull(row.lastSeen)
        assertFalse(row.currentlyConnected)
    }
    @Test fun serverInventoryCannotInjectTransientLastSeenMetadata() {
        val node = Gson().fromJson("""{"lastSeenAt":10,"lastSeenConnected":true}""", MeshNode::class.java)
        assertNull(node.lastSeenAt)
        assertFalse(node.lastSeenConnected)
    }
    @Test fun disconnectedDevicesUseTheReportedHistoricalDate() {
        val rows = listOf(
            deviceListInventorySortRow(group, MeshNode(id = "old", name = "Alpha", lastSeenAt = 1700000000000L)),
            deviceListInventorySortRow(group, MeshNode(id = "new", name = "Zulu", lastSeenAt = 1731586752000L))
        )
        assertEquals(listOf("new", "old"), rows.sortedWith(deviceListComparator(DeviceListSort.LAST_SEEN)).map { it.id })
    }
}
