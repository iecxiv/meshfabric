package iecxiv.meshfabric.ui.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebNoticeProtocolTest {
    @Test fun nestedServerInfoExposesLanguages() {
        val packet=JSONObject().put("action","serverinfo").put("serverinfo",JSONObject().put("languages",org.json.JSONArray().put("en").put("es")))
        assertEquals(listOf("en","es"),accountLanguageTags(accountServerInfo(packet).getJSONArray("languages")))
    }
    @Test fun allFiveOptionsRoundTrip() {
        for(mask in 0L..31L) assertEquals(mask,webNoticeFlags(webNoticeOptions(mask)))
    }
    @Test fun preservesOtherWebSettingsAndUnknownBits() {
        val original=JSONObject().put("notifications","256").put("theme","dark").put("desktopsettings","{}")
        val changed=mergeWebNoticeState(original,256L,webNoticeOptions(31L))!!
        assertEquals(287L,webNoticeMask(changed))
        assertEquals("dark",changed.getString("theme"))
        assertEquals("{}",changed.getString("desktopsettings"))
        assertEquals("256",original.getString("notifications"))
        assertNull(mergeWebNoticeState(original,0L,webNoticeOptions(0L)))
    }
    @Test fun acceptsStringAndObjectWebState() {
        val state=JSONObject().put("notifications","31")
        val event=JSONObject().put("action","userWebState").put("state",state.toString())
        assertEquals(31L,webNoticeMask(webNoticeState(event)!!))
        event.put("state",state)
        assertEquals(31L,webNoticeMask(webNoticeState(JSONObject().put("action","event").put("event",event))!!))
        state.put("notifications",false)
        assertNull(webNoticeState(event))
    }
    @Test fun changenodeUsesNestedConnectivityAndCanonicalId() {
        val packet=JSONObject().put("action","changenode").put("node",JSONObject().put("_id","node//abc").put("conn",5))
        assertEquals("abc" to 5,noticeConnectivity(packet))
        assertEquals(4 to 0,accountConnectionChange(1,5))
        assertEquals(0 to 4,accountConnectionChange(5,1))
        packet.getJSONObject("node").put("conn","bad")
        assertNull(noticeConnectivity(packet))
    }
    @Test fun amtIsNotBroadcastOrRelayLog() {
        assertEquals("amt",webNoticeKind(JSONObject().put("amtMessage",0)))
        assertEquals("general",webNoticeKind(JSONObject().put("tag","broadcast")))
        assertNull(noticeConnectivity(JSONObject().put("action","relaylog").put("protocol",101)))
    }
}
