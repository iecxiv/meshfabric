package iecxiv.meshfabric.ui.devices

import org.junit.Assert.*
import org.junit.Test

class DeviceListBatchPolicyTest {
    private fun row(id: String, name: String = id, group: String = "Group") =
        DeviceListSortRow(id, name, group)

    @Test fun namesAndGroupsUseTheirLabels() {
        val rows = listOf(row("1", "z", "Alpha"), row("2", "a", "Zulu"))
        assertEquals(listOf("2", "1"), rows.sortedWith(deviceListComparator(DeviceListSort.DEVICE)).map { it.id })
        assertEquals(listOf("1", "2"), rows.sortedWith(deviceListComparator(DeviceListSort.GROUP)).map { it.id })
    }

    @Test fun reverseChangesPrimaryOrder() {
        assertEquals(listOf("b", "a"), listOf(row("a"), row("b")).sortedWith(deviceListComparator(DeviceListSort.DEVICE, true)).map { it.id })
    }

    @Test fun equalNamesHaveStableIdTieBreakers() {
        for (reverse in listOf(false, true)) {
            assertEquals(listOf("1", "2"), listOf(row("2", " Alpha "), row("1", "alpha")).sortedWith(deviceListComparator(DeviceListSort.DEVICE, reverse)).map { it.id })
        }
    }

    @Test fun tagsAreCaseInsensitiveDeduplicatedAndOrderIndependent() {
        val a = row("1", "same").copy(tags = listOf("Zulu", " alpha ", "ALPHA"))
        val b = row("2", "same").copy(tags = listOf("alpha", "zulu"))
        assertTrue(deviceListComparator(DeviceListSort.TAGS).compare(a, b) < 0)
        assertTrue(deviceListComparator(DeviceListSort.TAGS).compare(b, a) > 0)
    }

    @Test fun missingTagsStayLastInBothDirections() {
        for (sort in listOf(DeviceListSort.TAGS, DeviceListSort.GROUP_TAGS)) {
            for (reverse in listOf(false, true)) {
                val present = row("present").copy(tags = listOf("tag"), groupTags = listOf("tag"))
                assertEquals("missing", listOf(row("missing"), present).sortedWith(deviceListComparator(sort, reverse)).last().id)
            }
        }
    }

    @Test fun timestampsAreNewestFirstAndUnknownLast() {
        for (sort in listOf(DeviceListSort.LAST_SEEN, DeviceListSort.LAST_BOOT)) {
            val rows = listOf(row("missing"), row("old").copy(lastSeen = 10L, lastBoot = 10L), row("new").copy(lastSeen = 20L, lastBoot = 20L))
            assertEquals(listOf("new", "old", "missing"), rows.sortedWith(deviceListComparator(sort)).map { it.id })
            assertEquals(listOf("old", "new", "missing"), rows.sortedWith(deviceListComparator(sort, true)).map { it.id })
        }
    }

    @Test fun invalidTimestampsAreUnknown() {
        val rows = listOf(row("zero").copy(lastSeen = 0L), row("valid").copy(lastSeen = 10L), row("negative").copy(lastSeen = -1L))
        for (reverse in listOf(false, true)) assertEquals("valid", rows.sortedWith(deviceListComparator(DeviceListSort.LAST_SEEN, reverse)).first().id)
    }

    @Test fun powerIsTriStateRatherThanConnectivity() {
        val rows = listOf(row("unknown"), row("off").copy(poweredOn = false), row("on").copy(poweredOn = true))
        assertEquals(listOf("on", "off", "unknown"), rows.sortedWith(deviceListComparator(DeviceListSort.POWER)).map { it.id })
        assertEquals(listOf("off", "on", "unknown"), rows.sortedWith(deviceListComparator(DeviceListSort.POWER, true)).map { it.id })
    }

    @Test fun hiddenAndRemovedDevicesAreDeselected() {
        assertEquals(setOf("a"), DeviceListSelection("server/account", setOf("a", "b")).reconcile("server/account", setOf("a", "c")).ids)
    }

    @Test fun changingServerOrAccountClearsSelection() {
        val selection = DeviceListSelection("server/account", setOf("a"))
        for (scope in listOf("other/account", "server/other")) assertTrue(selection.reconcile(scope, setOf("a")).ids.isEmpty())
    }

    @Test fun toggleCannotSelectHiddenOrBlankIds() {
        val selection = DeviceListSelection("scope")
        assertTrue(selection.toggle("scope", "hidden", setOf("a")).ids.isEmpty())
        assertTrue(selection.toggle("scope", "", setOf("")).ids.isEmpty())
        val selected = selection.toggle("scope", "a", setOf("a"))
        assertEquals(setOf("a"), selected.ids)
        assertTrue(selected.toggle("scope", "a", setOf("a")).ids.isEmpty())
    }

    @Test fun groupSelectionIsLimitedToVisibleMembers() {
        val visible = setOf("a", "c")
        val selection = DeviceListSelection("scope", setOf("c"))
        val selected = selection.toggleGroup("scope", setOf("a", "b"), visible)
        assertEquals(setOf("a", "c"), selected.ids)
        assertEquals(setOf("c"), selected.toggleGroup("scope", setOf("a", "b"), visible).ids)
    }

    @Test fun selectVisibleCopiesItsInputAndClearRemovesSelection() {
        val visible = mutableSetOf("a", "")
        val selection = DeviceListSelection("old").selectVisible("new", visible)
        visible.add("b")
        assertEquals("new", selection.scope)
        assertEquals(setOf("a"), selection.ids)
        assertTrue(selection.clear().ids.isEmpty())
    }
}
