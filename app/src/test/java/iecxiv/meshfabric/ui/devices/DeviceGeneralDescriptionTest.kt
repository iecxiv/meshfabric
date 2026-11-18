package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralDescriptionTest {
    private val before=GeneralMeta("Equipo",listOf("original"),4,"Linux","mesh//a","Anterior")
    private fun packet()=JsonObject().apply {
        addProperty("action","getsysinfo");addProperty("tag","read");addProperty("nodeid","node//a");addProperty("rights",4)
        add("node",JsonObject().apply {addProperty("_id","node//a");addProperty("name","Equipo");addProperty("desc","Texto\nSegunda línea")})
    }
    @Test fun readsDescriptionFromVerifiedNode() {
        assertEquals("Texto\nSegunda línea",generalMeta(packet(),"node//a","read")?.description)
    }
    @Test fun absentDescriptionIsEmpty() {
        val value=packet();value.getAsJsonObject("node").remove("desc")
        assertEquals("",generalMeta(value,"node//a","read")?.description)
    }
    @Test fun invalidDescriptionTypeDoesNotEnableEditing() {
        val value=packet();value.getAsJsonObject("node").addProperty("desc",false)
        assertNull(generalMeta(value,"node//a","read"))
    }
    @Test fun descriptionOnlySendsNoNameOrTags() {
        val draft=generalDraft(before.name,"original","Nueva\nDescripción")
        val change=generalChange("node//a","write",before,draft)
        assertEquals("Nueva\nDescripción",change.get("desc").asString)
        assertFalse(change.has("name"));assertFalse(change.has("tags"))
    }
    @Test fun emptyDescriptionExplicitlyClearsIt() {
        val change=generalChange("node//a","write",before,generalDraft(before.name,"original",""))
        assertTrue(change.has("desc"));assertEquals("",change.get("desc").asString)
    }
    @Test fun legacyNameEditNeverErasesDescription() {
        val change=generalChange("node//a","write",before,generalDraft("Otro","original"))
        assertFalse(change.has("desc"))
    }
    @Test fun unchangedDescriptionIsNotSent() {
        val change=generalChange("node//a","write",before,generalDraft("Otro","original",before.description))
        assertFalse(change.has("desc"))
    }
    @Test fun rereadMustMatchDescriptionBeforeConfirmation() {
        val draft=generalDraft(before.name,"original","Nueva")
        assertFalse(generalMatches(before,draft))
        assertTrue(generalMatches(before.copy(description="Nueva"),draft))
    }
    @Test fun concurrentDescriptionChangeInvalidatesBaseline() {
        val draft=generalDraft(before.name,"original","Nueva")
        assertFalse(generalBaselineMatches(before.copy(description="Concurrente"),before,draft))
        assertTrue(generalBaselineMatches(before,before,draft))
    }
    @Test fun validatesLengthAndControlCharacters() {
        assertTrue(runCatching {generalDraft("Equipo","","x".repeat(1024))}.isSuccess)
        assertTrue(runCatching {generalDraft("Equipo","","x".repeat(1025))}.isFailure)
        assertTrue(runCatching {generalDraft("Equipo","","bad\u0000text")}.isFailure)
    }
    @Test fun missingRightsStillPreventsDescriptionChanges() {
        assertTrue(runCatching {generalChange("node//a","write",before.copy(rights=0),generalDraft(before.name,"original","Nueva"))}.isFailure)
    }
}
