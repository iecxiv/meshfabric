package iecxiv.meshfabric.ui.devices

import com.google.gson.Gson
import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceListInventoryTagsTest {
    private val gson = Gson()
    private val group = MeshGroup(id = "mesh//one", name = "Group")

    @Test fun absentAndNullTagsRemainUnknown() {
        assertNull(gson.fromJson("{}", MeshNode::class.java).tags)
        assertNull(gson.fromJson("""{"tags":null}""", MeshNode::class.java).tags)
    }
    @Test fun readsTrimsAndDeduplicatesStringTags() {
        val node = gson.fromJson("""{"tags":[" beta ","alpha","beta",""]}""", MeshNode::class.java)
        assertEquals(listOf("beta", "alpha"), node.tags)
    }
    @Test fun malformedArrayMembersDoNotDiscardTheNode() {
        val node = gson.fromJson("""{"_id":"node//one","tags":[null,42,true,{"x":1},["nested"],"valid"]}""", MeshNode::class.java)
        assertEquals("node//one", node.id)
        assertEquals(listOf("valid"), node.tags)
    }
    @Test fun unexpectedFieldTypesDoNotDiscardTheNode() {
        for (value in listOf("42", "true", "\"text\"", "{}")) {
            val node = gson.fromJson("""{"_id":"node//one","tags":$value}""", MeshNode::class.java)
            assertEquals("node//one", node.id)
            assertNull(node.tags)
        }
    }
    @Test fun connectionAndNetworkCopiesPreserveTags() {
        val node = MeshNode(id = "node//one", tags = listOf("tag"))
        assertEquals(node.tags, node.copy(conn = 1).tags)
        assertEquals(node.tags, node.copy(networkIpv4 = emptyList()).tags)
    }
    @Test fun sortingUsesInventoryTagsRatherThanDeviceNames() {
        val rows = listOf(
            deviceListInventorySortRow(group, MeshNode(id = "one", name = "Alpha", tags = listOf("zulu"))),
            deviceListInventorySortRow(group, MeshNode(id = "two", name = "Zulu", tags = listOf("alpha")))
        )
        assertEquals(listOf("two", "one"), rows.sortedWith(deviceListComparator(DeviceListSort.TAGS)).map { it.id })
        assertEquals(listOf("one", "two"), rows.sortedWith(deviceListComparator(DeviceListSort.TAGS, true)).map { it.id })
    }
    @Test fun missingAndEmptyTagsStayAtTheEndInEitherDirection() {
        val rows = listOf(
            deviceListInventorySortRow(group, MeshNode(id = "missing")),
            deviceListInventorySortRow(group, MeshNode(id = "empty", tags = emptyList())),
            deviceListInventorySortRow(group, MeshNode(id = "present", tags = listOf("tag")))
        )
        for (reverse in listOf(false, true)) {
            assertEquals("present", rows.sortedWith(deviceListComparator(DeviceListSort.TAGS, reverse)).first().id)
        }
    }
    @Test fun tagSerializationRoundTrips() {
        val node = MeshNode(id = "node//one", tags = listOf("á", "tag"))
        assertEquals(node.tags, gson.fromJson(gson.toJson(node), MeshNode::class.java).tags)
    }
    @Test fun unsupportedCriteriaRemainDisabled() {
        assertEquals(setOf(DeviceListSort.GROUP, DeviceListSort.POWER, DeviceListSort.DEVICE, DeviceListSort.TAGS, DeviceListSort.GROUP_TAGS, DeviceListSort.LAST_BOOT), deviceListAvailableSorts)
    }
}
