package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralNotesPolicyTest {
    private fun reply()=JsonObject().apply {addProperty("action","getNotes");addProperty("id","node//a");addProperty("responseid","read");addProperty("notes","Original")}
    @Test fun acceptsCorrelatedNotes() {assertEquals("Original",generalNotesReply(reply(),"node//a","read"))}
    @Test fun differentDeviceNeverMatchesEvenWithCorrectRequest() {val p=reply();p.addProperty("id","node//b");assertNull(generalNotesReply(p,"node//a","read"))}
    @Test fun staleRequestIsIgnoredEvenWithCorrectDevice() {assertNull(generalNotesReply(reply(),"node//a","new-read"))}
    @Test fun supportsDeviceCorrelatedReplyWithoutResponseId() {val p=reply();p.remove("responseid");assertEquals("Original",generalNotesReply(p,"node//a","read"))}
    @Test fun uncorrelatedReplyCannotBecomeNotes() {val p=reply();p.remove("id");p.remove("responseid");assertNull(generalNotesReply(p,"node//a","read"))}
    @Test fun missingPayloadIsNotAnEmptyNote() {val p=reply();p.remove("notes");assertTrue(runCatching {generalNotesReply(p,"node//a","read")}.isFailure)}
    @Test fun explicitNullCanRepresentEmptyNotes() {val p=reply();p.add("notes",JsonNull.INSTANCE);assertEquals("",generalNotesReply(p,"node//a","read"))}
    @Test fun oversizedReplyIsRejectedNotTruncated() {val p=reply();p.addProperty("notes","x".repeat(64001));assertTrue(runCatching {generalNotesReply(p,"node//a","read")}.isFailure)}
    @Test fun clearingNotesIsExplicitAndRequiresNotesPermission() {
        val p=generalNotesPacket("node//a","write","",128)
        assertEquals("setNotes",p.get("action").asString);assertEquals("node//a",p.get("id").asString);assertEquals("",p.get("notes").asString)
        assertTrue(runCatching {generalNotesPacket("node//a","write","",4)}.isFailure)
    }
    @Test fun notesAreSentAsPlainJsonTextNotUriEncoded() {val text="Revisión + 100%\nSegunda línea";assertEquals(text,generalNotesPacket("node//a","write",text,128).get("notes").asString)}
    @Test fun invalidDraftCannotBeWritten() {
        assertFalse(generalNotesValid("x".repeat(64001)));assertFalse(generalNotesValid("bad\u0000text"))
        assertTrue(generalNotesValid(""));assertTrue(generalNotesValid("x".repeat(64000)))
    }
    @Test fun editPermissionRequiresReadyVerifiedState() {
        assertTrue(GeneralNotesState("","ready",128).canEdit)
        assertFalse(GeneralNotesState("","ready",4).canEdit)
        for(phase in listOf("loading","saving","error")) assertFalse(GeneralNotesState("",phase,128).canEdit)
    }
}
