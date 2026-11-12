package iecxiv.meshfabric.ui.devices
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
class DeviceGeneralPolicyTest {
    private val meta=GeneralMeta("PC",listOf("A"),4,"OS","mesh//x")
    @Test fun normalizesTags() { assertEquals(listOf("A","B"),generalDraft("PC","B, A, B").tags) }
    @Test(expected=IllegalArgumentException::class) fun rejectsBlankName() { generalDraft(" ","") }
    @Test(expected=IllegalArgumentException::class) fun rejectsLongTag() { generalDraft("PC","a".repeat(64)) }
    @Test(expected=IllegalArgumentException::class) fun rejectsControlCharacters() { generalDraft("PC\u0000","") }
    @Test(expected=IllegalArgumentException::class) fun deniesWriteWithoutRights() { generalChange("node//x","r",meta.copy(rights=0),GeneralDraft("New",emptyList())) }
    @Test fun onlySendsChangedFields() {
        val p=generalChange("node//x","r",meta,GeneralDraft("New",listOf("A")))
        assertTrue(p.has("name"));assertFalse(p.has("tags"));assertEquals("changedevice",p.get("action").asString)
    }
    @Test fun emptyArrayClearsTags() { assertEquals(0,generalChange("node//x","r",meta,GeneralDraft("PC",emptyList())).getAsJsonArray("tags").size()) }
    @Test fun acceptedIsNotContentConfirmation() { assertFalse(generalMatches(meta,GeneralDraft("New",listOf("A")))) }
    @Test fun requiresNodeAndCorrelation() {
        val p=JsonParser.parseString("""{"action":"getsysinfo","tag":"r","nodeid":"node//x","rights":4,"node":{"_id":"node//x","name":"PC","tags":["A"]}}""").asJsonObject
        assertNotNull(generalMeta(p,"node//x","r"));assertNull(generalMeta(p,"node//other","r"));assertNull(generalMeta(p,"node//x","wrong"))
        p.remove("rights");assertNull(generalMeta(p,"node//x","r"))
    }
}
