package iecxiv.meshfabric.ui.devices

import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralEditBaselineTest {
    private val before = GeneralMeta("Device", listOf("old"), 4L, "Linux", "mesh//one", "Description", 0L)
    private val tagsOnly = GeneralDraft("Device", listOf("new"))

    @Test fun unchangedReviewedContextMatches() {
        assertTrue(generalBaselineMatches(before, before, tagsOnly))
    }
    @Test fun movingToAnotherGroupInvalidatesTheReview() {
        assertFalse(generalBaselineMatches(before.copy(mesh = "mesh//other"), before, tagsOnly))
    }
    @Test fun concurrentNameChangeInvalidatesTheReview() {
        assertFalse(generalBaselineMatches(before.copy(name = "Renamed"), before, tagsOnly))
    }
    @Test fun concurrentTagChangeInvalidatesTheReview() {
        assertFalse(generalBaselineMatches(before.copy(tags = listOf("third")), before, tagsOnly))
    }
    @Test fun tagOrderingDoesNotCreateAConflict() {
        val baseline = before.copy(tags = listOf("a", "b"))
        assertTrue(generalBaselineMatches(baseline.copy(tags = listOf("b", "a")), baseline, tagsOnly))
    }
    @Test fun unrelatedDescriptionChangeIsPreservedForATagEdit() {
        assertTrue(generalBaselineMatches(before.copy(description = "Updated remotely"), before, tagsOnly))
    }
    @Test fun editingDescriptionRequiresItsReviewedBaseline() {
        val draft = tagsOnly.copy(description = "Requested description")
        assertFalse(generalBaselineMatches(before.copy(description = "Updated remotely"), before, draft))
    }
    @Test fun unrelatedConsentChangeIsPreservedForATagEdit() {
        assertTrue(generalBaselineMatches(before.copy(consent = 1L), before, tagsOnly))
    }
    @Test fun editingConsentRequiresItsReviewedBaseline() {
        val draft = tagsOnly.copy(consent = 2L)
        assertFalse(generalBaselineMatches(before.copy(consent = 1L), before, draft))
    }
    @Test fun tagOnlyPacketDoesNotRenameMoveOrOverwriteOtherFields() {
        val packet = generalChange("node//one", "request", before, tagsOnly)
        assertEquals(setOf("action", "nodeid", "responseid", "tags"), packet.entrySet().map { it.key }.toSet())
        assertEquals("changedevice", packet.get("action").asString)
        assertEquals("node//one", packet.get("nodeid").asString)
        assertEquals("request", packet.get("responseid").asString)
        assertEquals("new", packet.getAsJsonArray("tags")[0].asString)
    }
}
