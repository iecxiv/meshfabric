package iecxiv.meshfabric.ui.management

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ServerConsolePolicyTest {
    @Test fun terminalControlsCannotClearOutputOrRequestClipboard() {
        assertEquals("normal red",serverConsolePlainText("normal \u001b[31mred\u001b[0m\u001b[2J"))
        assertEquals("safe",serverConsolePlainText("\u001b]52;c;secret\u0007safe"))
        assertEquals("text",serverConsolePlainText("\u001bPpayload\u001b\\text"))
        assertEquals("a\nb\nc",serverConsolePlainText("a\r\nb\rc"))
    }
    @Test fun renderedSnapshotIsBoundedAndIncludesTruncationMarker() {
        val snapshot=serverConsoleSnapshot(List(200) { "x".repeat(400) })
        assertTrue(snapshot.length<=SERVER_CONSOLE_LIMIT)
        assertTrue(snapshot.startsWith("[… truncated]\n"))
    }
    @Test fun onlyBundledConsoleAssetsCanBeLoaded() {
        assertEquals("terminal/server-console.html",serverConsoleAsset(SERVER_CONSOLE_URL))
        assertEquals("terminal/xterm.js",serverConsoleAsset("https://appassets.androidplatform.net/terminal/xterm.js"))
        listOf("https://example.test/terminal/xterm.js","https://appassets.androidplatform.net/terminal/../secret","https://appassets.androidplatform.net/terminal/xterm.js?auth=secret","file:///data/private","https://user:pass@appassets.androidplatform.net/terminal/xterm.js").forEach { assertNull(it,serverConsoleAsset(it)) }
    }
    @Test fun untrustedOutputIsPassedAsJsonDataNotHtml() {
        val text="</script>\";alert(1)//"
        val payload=serverConsolePayload(text,"Console","Empty",13)
        assertEquals(text,JsonParser.parseString(payload).asJsonObject.get("text").asString)
        assertFalse(payload.contains("</script>"))
    }
}
