package iecxiv.meshfabric.ui.devices

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class DeviceGeneralConsentTest {
    private val before=GeneralMeta("Equipo",listOf("a"),4,"Linux","mesh//a","Texto",1024L or 8L)
    private fun draft(value:Long)=generalDraft(before.name,"a",before.description,value)
    @Test fun absentConsentMeansNoDeviceSpecificFlags() {assertEquals(0L,generalConsentValue(JsonObject()))}
    @Test fun readsConsentAsUnsignedMask() {assertEquals(0xffffffffL,generalConsentValue(JsonObject().apply {addProperty("consent",0xffffffffL)}))}
    @Test fun rejectsStringOrFractionalConsent() {
        assertTrue(runCatching {generalConsentValue(JsonObject().apply {addProperty("consent","8")})}.isFailure)
        assertTrue(runCatching {generalConsentValue(JsonObject().apply {addProperty("consent",1.5)})}.isFailure)
    }
    @Test fun rejectsNegativeAndOversizedMasks() {
        assertTrue(runCatching {generalDraft("Equipo","",consent=-1)}.isFailure)
        assertTrue(runCatching {generalDraft("Equipo","",consent=0x100000000L)}.isFailure)
    }
    @Test fun clearingKnownFlagsPreservesUnknownBits() {assertEquals(1024L,generalConsentMerge(before.consent,0))}
    @Test fun selectingFlagsPreservesUnknownBits() {assertEquals(1024L or 16L,generalConsentMerge(before.consent,16))}
    @Test fun cannotSelectUnknownBits() {assertTrue(runCatching {generalConsentMerge(before.consent,1024)}.isFailure)}
    @Test fun sendsOnlyConsentWhenOtherFieldsAreUnchanged() {
        val change=generalChange("node//a","write",before,draft(1024L or 16L))
        assertEquals(1040L,change.get("consent").asLong)
        assertFalse(change.has("name"));assertFalse(change.has("tags"));assertFalse(change.has("desc"))
    }
    @Test fun wireBuilderRejectsDroppingUnknownBits() {assertTrue(runCatching {generalChange("node//a","write",before,draft(16))}.isFailure)}
    @Test fun legacyDraftDoesNotSendConsent() {assertFalse(generalChange("node//a","write",before,generalDraft("Otro","a")).has("consent"))}
    @Test fun rereadMustObserveConsentBeforeConfirmation() {
        val next=draft(1024L or 16L)
        assertFalse(generalMatches(before,next))
        assertTrue(generalMatches(before.copy(consent=1040),next))
    }
    @Test fun concurrentConsentChangeInvalidatesReview() {assertFalse(generalBaselineMatches(before.copy(consent=1024),before,draft(1040)))}
    @Test fun missingRightsBlocksConsentWrite() {assertTrue(runCatching {generalChange("node//a","write",before.copy(rights=0),draft(1040))}.isFailure)}
}
