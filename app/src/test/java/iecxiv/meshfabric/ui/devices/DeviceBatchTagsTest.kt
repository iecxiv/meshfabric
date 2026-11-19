package iecxiv.meshfabric.ui.devices

import iecxiv.meshfabric.data.model.MeshGroup
import iecxiv.meshfabric.data.model.MeshNode
import org.junit.Assert.*
import org.junit.Test

class DeviceBatchTagsTest {
    private val before = GeneralMeta("Device", listOf("keep", "old"), 4L, "Linux", "mesh//one", "Description", 1L)

    @Test fun parsesCommasAndNewlinesWithoutDuplicates() {
        assertEquals(listOf("a", "b"), deviceBatchTagInput(" b, a\nb ", DeviceBatchTagMode.ADD))
    }
    @Test fun acceptsWindowsLineEndings() {
        assertEquals(listOf("a", "b"), deviceBatchTagInput("a\r\nb", DeviceBatchTagMode.ADD))
    }
    @Test fun addAndRemoveRequireNonemptyInput() {
        for (mode in listOf(DeviceBatchTagMode.ADD, DeviceBatchTagMode.REMOVE)) {
            assertTrue(runCatching { deviceBatchTagInput(" , \n", mode) }.isFailure)
        }
    }
    @Test fun emptyReplaceExplicitlyRepresentsClearAll() {
        val input = deviceBatchTagInput("", DeviceBatchTagMode.REPLACE)
        assertTrue(deviceBatchTagDraft(before, input, DeviceBatchTagMode.REPLACE).tags.isEmpty())
    }
    @Test fun rejectsOversizedTagsAndControlCharacters() {
        for (text in listOf("a".repeat(64), "a".repeat(4097), "a\tb", "a\u0000b")) {
            assertTrue(runCatching { deviceBatchTagInput(text, DeviceBatchTagMode.ADD) }.isFailure)
        }
    }
    @Test fun addingPreservesUnmentionedTags() {
        assertEquals(listOf("keep", "new", "old"), deviceBatchTagDraft(before, listOf("new"), DeviceBatchTagMode.ADD).tags)
    }
    @Test fun removingOnlyRemovesRequestedTags() {
        assertEquals(listOf("keep"), deviceBatchTagDraft(before, listOf("old"), DeviceBatchTagMode.REMOVE).tags)
    }
    @Test fun replacingUsesTheNewSet() {
        assertEquals(listOf("new"), deviceBatchTagDraft(before, listOf("new"), DeviceBatchTagMode.REPLACE).tags)
    }
    @Test fun draftDoesNotRequestOtherFieldChanges() {
        val draft = deviceBatchTagDraft(before, listOf("new"), DeviceBatchTagMode.ADD)
        assertEquals(before.name, draft.name)
        assertNull(draft.description)
        assertNull(draft.consent)
    }
    @Test fun existingTagsCanProduceAnUnchangedResult() {
        assertTrue(generalMatches(before, deviceBatchTagDraft(before, listOf("keep"), DeviceBatchTagMode.ADD)))
    }
    @Test fun unexpectedExistingSeparatorsAreNotSilentlyRewritten() {
        assertTrue(runCatching { deviceBatchTagDraft(before.copy(tags = listOf("a,b")), listOf("new"), DeviceBatchTagMode.ADD) }.isFailure)
    }
    @Test fun onlyReadbackConfirmationIsReportedAsVerified() {
        assertEquals(DeviceBatchTagPhase.VERIFIED, deviceBatchTagResult(GeneralEditorState(phase = "confirmed")))
        assertEquals(DeviceBatchTagPhase.CONFLICT, deviceBatchTagResult(GeneralEditorState(phase = "error", code = "conflict")))
        assertEquals(DeviceBatchTagPhase.REJECTED, deviceBatchTagResult(GeneralEditorState(phase = "error", code = "denied")))
        assertEquals(DeviceBatchTagPhase.UNCONFIRMED, deviceBatchTagResult(GeneralEditorState(phase = "error", code = "timeout")))
    }
    @Test fun editingDoesNotCollapseCaseDistinctTags() {
        assertEquals(listOf("Tag", "keep", "old", "tag"), deviceBatchTagDraft(before, listOf("Tag", "tag"), DeviceBatchTagMode.ADD).tags)
    }
    @Test fun serverSideEditingDoesNotRequireAnOnlineAgentButRequiresRights() {
        val inventory = listOf(MeshGroup(id = before.mesh, mtype = 2) to listOf(MeshNode(id = "node//one", meshid = before.mesh, conn = 0)))
        assertTrue(deviceBatchTagAvailable("node//one", before.mesh, before, inventory))
        assertFalse(deviceBatchTagAvailable("node//one", before.mesh, before.copy(rights = 0L), inventory))
        assertFalse(deviceBatchTagAvailable("node//one", "mesh//other", before, inventory))
    }
}
