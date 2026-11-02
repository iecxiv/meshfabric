package iecxiv.meshfabric.data.remote

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ClipboardControlProtocolTest {
    private fun decode(value: String, fallback: String? = null) =
        ClipboardControlProtocol.decode(JsonParser.parseString(value).asJsonObject, fallback)

    @Test fun preserveExactTextAndNormalizeNode() {
        val value = decode("""{"action":"msg","type":"getclip","nodeid":"node//abc","data":"  a\n "}""")
        assertEquals(ClipboardControlReply.Content("abc", "  a\n "), value)
    }

    @Test fun emptyClipboardIsContentNotMissingResponse() {
        assertEquals(ClipboardControlReply.Content("abc", ""),
            decode("""{"action":"msg","type":"getclip","nodeid":"abc","data":""}"""))
        assertNull(decode("""{"action":"msg","type":"getclip","nodeid":"abc"}"""))
    }

    @Test fun ignoreUnrelatedMessagesAndMalformedPayloads() {
        assertNull(decode("""{"action":"msg","type":"console","nodeid":"abc","data":"not clipboard"}"""))
        assertNull(decode("""{"action":"msg","type":"getclip","nodeid":"abc","data":true}"""))
        assertNull(decode("""{"action":"msg","type":"getclip","nodeid":"abc","data":{"text":"x"}}"""))
    }

    @Test fun writeAcknowledgementsAreNotClipboardContent() {
        assertEquals(ClipboardControlReply.WriteResult("abc", true),
            decode("""{"action":"msg","type":"setclip","nodeid":"node/abc","success":true}"""))
        assertEquals(ClipboardControlReply.WriteResult("abc", false),
            decode("""{"action":"setclip","nodeid":"abc","success":false}"""))
        assertNull(decode("""{"action":"setclip","nodeid":"abc","data":"do not treat as content"}"""))
        assertNull(decode("""{"action":"setclip","nodeid":"abc","success":"true"}"""))
    }

    @Test fun controlRequiresNodeButDedicatedKvmCanSupplyIt() {
        val packet = """{"type":"getclip","data":"text"}"""
        assertNull(decode(packet))
        assertEquals(ClipboardControlReply.Content("abc", "text"), decode(packet, "node//abc"))
    }

    @Test fun preserveNodeIdentityAndRejectBlankNode() {
        assertEquals(ClipboardControlReply.Content("domain/other", "x"),
            decode("""{"action":"getclip","nodeid":"node/domain/other","data":"x"}"""))
        assertNull(decode("""{"action":"getclip","nodeid":" ","data":"x"}"""))
        assertNull(decode("""{"action":"getclip","nodeid":{"id":"abc"},"data":"x"}"""))
    }

    @Test fun failedReadDoesNotPublishItsData() {
        assertNull(decode("""{"action":"getclip","nodeid":"abc","success":false,"data":"old"}"""))
    }

    @Test fun commandsAreDirectedAndClearingIsExplicit() {
        val get = ClipboardControlProtocol.command("getclip", "node/abc")!!
        assertEquals("msg", get.get("action").asString)
        assertEquals("getclip", get.get("type").asString)
        assertEquals("node//abc", get.get("nodeid").asString)
        assertFalse(get.has("data"))
        val clear = ClipboardControlProtocol.command("setclip", "abc", "")!!
        assertEquals("setclip", clear.get("type").asString)
        assertTrue(clear.has("data"))
        assertEquals("", clear.get("data").asString)
        assertNull(ClipboardControlProtocol.command("setclip", "abc"))
        assertNull(ClipboardControlProtocol.command("getclip", ""))
        assertNull(ClipboardControlProtocol.command("console", "abc"))
    }
}
