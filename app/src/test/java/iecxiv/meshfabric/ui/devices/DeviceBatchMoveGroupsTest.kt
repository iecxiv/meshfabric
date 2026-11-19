package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchMoveGroupsTest {
    private val actor = "user//operator"
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    private fun group(id: String = "mesh//target", rights: String = "5"): JsonObject = json(
        """{"_id":"$id","name":"Target","mtype":2,"links":{"$actor":{"rights":$rights}}}"""
    )
    private fun read(records: List<JsonObject> = listOf(group()), user: String = actor, site: Long = 0L) =
        deviceBatchMoveReadGroups(records, user, site)

    @Test fun readsExplicitDirectPermissions() {
        assertEquals(5L, requireNotNull(read()).single().group.rights)
    }

    @Test fun missingLinksDoNotGrantPermission() {
        val record = group().apply { remove("links") }
        assertNull(requireNotNull(read(listOf(record))).single().group.rights)
    }

    @Test fun anotherUsersLinkDoesNotGrantPermission() {
        val record = group().apply { add("links", json("""{"user//other":{"rights":4294967295}}""")) }
        assertNull(requireNotNull(read(listOf(record))).single().group.rights)
    }

    @Test fun inheritedLinksAreNotAssumedToBeEffectiveRights() {
        val record = group().apply { add("links", json("""{"ugrp//team":{"rights":5}}""")) }
        assertNull(requireNotNull(read(listOf(record))).single().group.rights)
    }

    @Test fun fullSiteAdministratorIsExplicitlyRecognized() {
        val record = group().apply { remove("links") }
        for (site in listOf(-1L, 0xffffffffL)) {
            assertEquals(0xffffffffL, requireNotNull(read(listOf(record), site = site)).single().group.rights)
        }
    }

    @Test fun ordinarySiteRolesDoNotImplyGroupManagement() {
        val record = group().apply { remove("links") }
        assertNull(requireNotNull(read(listOf(record), site = 2L)).single().group.rights)
    }

    @Test fun malformedRightsRemainUnknown() {
        for (rights in listOf("\"5\"", "true", "null", "5.5", "-2", "4294967296")) {
            assertNull(requireNotNull(read(listOf(group(rights = rights)))).single().group.rights)
        }
    }

    @Test fun masksMustBeExactIntegers() {
        assertEquals(-1L, deviceBatchMoveReadMask(JsonParser.parseString("-1")))
        assertEquals(4294967295L, deviceBatchMoveReadMask(JsonParser.parseString("4294967295")))
        assertNull(deviceBatchMoveReadMask(JsonParser.parseString("4.999999999999999999999")))
    }

    @Test fun duplicateGroupIdsInvalidateRead() {
        assertNull(read(listOf(group(), group())))
    }

    @Test fun crossDomainGroupsAndInvalidActorsInvalidateRead() {
        assertNull(read(listOf(group(id = "mesh/other/target"))))
        assertNull(read(user = "user/other/operator"))
        assertNull(read(user = "node//operator"))
    }

    @Test fun invalidTypesAndIdentifiersInvalidateRead() {
        assertNull(read(listOf(group().apply { addProperty("mtype", 2.5) })))
        assertNull(read(listOf(group().apply { addProperty("mtype", "2") })))
        assertNull(read(listOf(group(id = "mesh//"))))
        assertNull(read(listOf(group(id = "mesh//target/extra"))))
    }

    @Test fun malformedLinkContainersInvalidateRead() {
        assertNull(read(listOf(group().apply { addProperty("links", "invalid") })))
        assertNull(read(listOf(group().apply { add("links", json("""{"$actor":5}""")) })))
    }

    @Test fun namesNeverReplaceIdentityAndCanBeDuplicated() {
        val records = listOf(group(id = "mesh//b"), group(id = "mesh//a"))
        assertEquals(listOf("mesh//a", "mesh//b"), requireNotNull(read(records)).map { it.group.id })
    }

    @Test fun emptyReadIsDistinctFromFailedRead() {
        assertEquals(emptyList<DeviceBatchMoveChoice>(), read(emptyList()))
        assertNull(read(site = -2L))
    }
}
